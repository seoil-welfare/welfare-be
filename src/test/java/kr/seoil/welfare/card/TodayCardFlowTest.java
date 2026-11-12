package kr.seoil.welfare.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import kr.seoil.welfare.auth.AuthRepository;
import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.card.ai.CardGenerator;
import kr.seoil.welfare.card.ai.CardSource;
import kr.seoil.welfare.card.ai.GeneratedCard;
import kr.seoil.welfare.card.ai.OpenAiCardGenerator;
import kr.seoil.welfare.card.ai.RuleCardGenerator;
import kr.seoil.welfare.card.dto.CardDtos;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.Catalog;
import kr.seoil.welfare.diary.DiaryCompletedEvent;
import kr.seoil.welfare.diary.DiaryRepository;
import kr.seoil.welfare.diary.DiaryService;
import kr.seoil.welfare.diary.dto.ActivityDto;
import kr.seoil.welfare.diary.dto.BehaviorDto;
import kr.seoil.welfare.diary.dto.DiaryResponse;
import kr.seoil.welfare.diary.dto.SaveDiaryRequest;
import kr.seoil.welfare.staff.StaffQueries;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 오늘 카드 — 명세 10장 '완료 기준'을 실제 PostgreSQL과 운영 마이그레이션으로 확인한다.
 * AI는 부르지 않는다. 정해진 문장(RuleCardGenerator) 또는 테스트가 정한 결과를 쓴다.
 */
class TodayCardFlowTest {

    static EmbeddedPostgres pg;
    static JdbcTemplate jdbc;
    static NamedParameterJdbcTemplate named;
    static TransactionTemplate tx;

    static final String REAL_NAME = "김실명";
    static final String BIRTH = "2000-03-07";
    static final String STAFF_NAME = "박담당";

    MutableClock clock;
    DiaryService diaries;
    TodayCardService cards;
    GuardianCardService guardians;
    CardGenerationWorker worker;
    CardTokens tokens;
    final List<Long> requested = new ArrayList<>();
    /** 테스트마다 바꿔 끼우는 AI 결과 (null이면 정해진 문장) */
    Function<CardSource, GeneratedCard> ai;
    final List<CardSource> aiInputs = new ArrayList<>();

    @BeforeAll
    static void database() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        var source = pg.getPostgresDatabase();
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        named = new NamedParameterJdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.update("INSERT INTO staff (staff_id, login_id, password_hash, name, department, position, role) VALUES "
                + "(1,'qa.author','unused','" + STAFF_NAME + "','팀','담당자','staff'),"
                + "(2,'qa.lead','unused','팀장님','팀','팀장','teamLead'),"
                + "(3,'qa.other','unused','다른직원','팀','담당자','staff')");
    }

    @AfterAll
    static void stop() throws Exception {
        if (pg != null) pg.close();
    }

    @BeforeEach
    void services() {
        jdbc.execute("TRUNCATE daily_log, client RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO client (client_id, name, birth_date, staff_id, card_nickname, card_consent)"
                + " VALUES (1, ?, ?::date, 1, '하늘', TRUE)", REAL_NAME, BIRTH);
        requested.clear();
        aiInputs.clear();
        ai = null;
        clock = new MutableClock(Instant.parse("2026-11-07T03:00:00Z"));
        AuthService auth = new AuthService(new AuthRepository(jdbc), new StaffQueries(jdbc), new BCryptPasswordEncoder());
        CardRepository repo = new CardRepository(named);
        tokens = new CardTokens("test-card-secret-0123456789");
        TodayCardService[] holder = new TodayCardService[1];
        diaries = new DiaryService(new DiaryRepository(named), new Catalog(jdbc), auth, clock, event -> {
            if (event instanceof DiaryCompletedEvent e) holder[0].onDiaryCompleted(e.logId());
        });
        cards = new TodayCardService(repo, diaries, auth, tokens, event -> {
            if (event instanceof CardGenerationRequested r) requested.add(r.logId());
        }, clock, "https://welfare-fe.vercel.app");
        holder[0] = cards;
        guardians = new GuardianCardService(repo, tokens, cards, clock);
        CardGenerator generator = new CardGenerator() {
            final RuleCardGenerator rule = new RuleCardGenerator();
            public GeneratedCard generate(CardSource s) {
                aiInputs.add(s);
                return ai == null ? rule.generate(s) : ai.apply(s);
            }
            public String name() { return "test"; }
        };
        worker = new CardGenerationWorker(cards, generator);
    }

    // ------------------------------------------------------------------ 준비 도우미

    static ActivityDto activity(String start, String end, String participation, boolean behavior) {
        return new ActivityDto(null, start, end, List.of(participation), null, "색종이로 풍경을 꾸몄어요.",
                List.of("모델링"), null, List.of("적극적 참여"), null, List.of("언어 촉진 및 격려"), null, behavior,
                behavior ? List.of(new BehaviorDto("selfHarm", List.of("sh-1"), null)) : List.of());
    }

    SaveDiaryRequest diary(String status, String notes, String updatedAt, ActivityDto... activities) {
        return new SaveDiaryRequest("1", "2026-11-07", status, "맑음", "양호", null, List.of(activities), notes, updatedAt);
    }

    /** 일지를 작성 완료로 만들고 카드 생성까지 끝낸다 → 일지 번호 */
    String completedDiaryWithCard(ActivityDto... activities) {
        DiaryResponse d = tx.execute(s -> diaries.create(diary("completed", "점심을 잘 먹었어요.", null, activities), 1));
        generateRequested();
        return d.id();
    }

    void generateRequested() {
        List<Long> ids = new ArrayList<>(requested);
        requested.clear();
        ids.forEach(worker::generate);
    }

    CardDtos.TodayCard card(String diaryId) {
        return tx.execute(s -> cards.get(diaryId, 1));
    }

    static void assertCode(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ================================================================== 생성

    @Test
    void 작성_완료하면_generating으로_생기고_생성_뒤_ready가_된다() {
        DiaryResponse d = tx.execute(s -> diaries.create(
                diary("completed", "메모", null, activity("10:00", "10:30", "예술활동", false),
                        activity("11:00", "11:30", "체육활동", false)), 1));
        assertThat(card(d.id()).status()).isEqualTo("generating");
        assertThat(card(d.id()).items()).isEmpty();
        assertThat(requested).containsExactly(Long.parseLong(d.id()));   // 커밋 뒤 AI 호출 요청

        generateRequested();
        CardDtos.TodayCard c = card(d.id());
        assertThat(c.status()).isEqualTo("ready");
        assertThat(c.nickname()).isEqualTo("하늘");
        assertThat(c.program()).isEqualTo("주간활동");
        assertThat(c.items()).extracting(CardDtos.CardItem::time).containsExactly("10:00", "11:00");
        assertThat(c.items()).extracting(CardDtos.CardItem::pictogram).containsExactly("art", "sports");
        assertThat(c.items()).allSatisfy(i -> {
            assertThat(CardRules.PICTOGRAMS).contains(i.pictogram());
            assertThat(CardRules.MOODS).contains(i.mood());
        });
    }

    @Test
    void 작성_중_일지와_활동_없는_일지는_카드가_없다() {
        DiaryResponse draft = tx.execute(s -> diaries.create(
                diary("draft", null, null, activity("10:00", "10:30", "예술활동", false)), 1));
        assertCode(() -> card(draft.id()), HttpStatus.NOT_FOUND, "CARD_NOT_FOUND");
        jdbc.execute("TRUNCATE daily_log RESTART IDENTITY CASCADE");
        DiaryResponse empty = tx.execute(s -> diaries.create(diary("completed", "쉬었어요.", null), 1));
        assertCode(() -> card(empty.id()), HttpStatus.NOT_FOUND, "CARD_NOT_FOUND");
        assertThat(requested).isEmpty();
    }

    @Test
    void AI_요청에_실명_생년월일_직원이름이_없다() throws Exception {
        completedDiaryWithCard(activity("10:00", "10:30", "예술활동", true));
        CardSource source = aiInputs.getFirst();
        OpenAiCardGenerator openAi = new OpenAiCardGenerator("sk-test", "gpt-test", "low", Duration.ofSeconds(1), new ObjectMapper());
        String body = new ObjectMapper().writeValueAsString(openAi.requestBody(source));
        assertThat(body).doesNotContain(REAL_NAME, BIRTH, "0307", STAFF_NAME, "팀장님", "sk-test")
                .contains("하늘", "예술활동", "자해: 머리 때리기");
        assertThat(body).doesNotContain("recordId", "defaultPictogram");   // 서버 내부 값도 보내지 않는다
    }

    @Test
    void 앞면에_도전적_행동이_들어가면_failed_다시_만들기로_회복() {
        ai = s -> new GeneratedCard("오늘 하루", List.of(new GeneratedCard.Item("10:00", "그림", "art", "good",
                "머리 때리기를 했어요.", "보호자 메모", null)));
        String id = completedDiaryWithCard(activity("10:00", "10:30", "예술활동", true));
        assertThat(card(id).status()).isEqualTo("failed");
        assertThat(card(id).items()).isEmpty();

        ai = null;                                                  // 정상 결과로 다시 만들기
        assertThat(tx.execute(s -> cards.regenerate(id, 1)).status()).isEqualTo("generating");
        generateRequested();
        CardDtos.TodayCard c = card(id);
        assertThat(c.status()).isEqualTo("ready");
        assertThat(c.items().getFirst().easyText()).doesNotContain("머리 때리기", "자해");
        assertThat(c.items().getFirst().guardianText()).contains("머리 때리기");   // 뒷면에는 사실대로
    }

    @Test
    void 그림_표정이_목록_밖이면_기본값으로_바꾼다() {
        ai = s -> new GeneratedCard("하루", List.of(new GeneratedCard.Item("10:00", "요리", "spaceship", "angry",
                "요리했어요.", "요리 시간에 참여했어요.", "없음")));
        String id = completedDiaryWithCard(activity("10:00", "10:30", "요리활동", false));
        CardDtos.CardItem item = card(id).items().getFirst();
        assertThat(item.pictogram()).isEqualTo("cooking");          // 참여 활동의 기본 그림
        assertThat(item.mood()).isEqualTo("good");
        assertThat(item.homeTip()).isNull();
    }

    @Test
    void 항목_수나_시각이_다르거나_빈_문장이면_failed() {
        ai = s -> new GeneratedCard("하루", List.of());
        String a = completedDiaryWithCard(activity("10:00", "10:30", "예술활동", false));
        assertThat(card(a).status()).isEqualTo("failed");

        jdbc.execute("TRUNCATE daily_log RESTART IDENTITY CASCADE");
        ai = s -> new GeneratedCard("하루", List.of(new GeneratedCard.Item("09:00", "그림", "art", "good", "그림", "메모", null)));
        String b = completedDiaryWithCard(activity("10:00", "10:30", "예술활동", false));
        assertThat(card(b).status()).isEqualTo("failed");

        jdbc.execute("TRUNCATE daily_log RESTART IDENTITY CASCADE");
        ai = s -> { throw new IllegalStateException("AI 시간 초과"); };
        String c = completedDiaryWithCard(activity("10:00", "10:30", "예술활동", false));
        assertThat(card(c).status()).isEqualTo("failed");
    }

    @Test
    void generating이_5분_넘게_이어지면_조회할_때_failed() {
        DiaryResponse d = tx.execute(s -> diaries.create(diary("completed", "메모", null,
                activity("10:00", "10:30", "예술활동", false)), 1));
        clock.advance(Duration.ofMinutes(4));
        assertThat(card(d.id()).status()).isEqualTo("generating");
        clock.advance(Duration.ofMinutes(2));
        assertThat(card(d.id()).status()).isEqualTo("failed");
        assertThat(tx.execute(s -> cards.regenerate(d.id(), 1)).status()).isEqualTo("generating");
    }

    // ================================================================== 확인·수정

    @Test
    void 문장을_고쳐도_일지_수정시각_이력은_그대로이고_동시수정은_409() {
        String id = completedDiaryWithCard(activity("10:00", "10:30", "예술활동", false));
        DiaryResponse before = diaries.get(id);
        int history = jdbc.queryForObject("SELECT count(*) FROM log_history", Integer.class);
        CardDtos.TodayCard c = card(id);
        String itemId = c.items().getFirst().id();

        clock.advance(Duration.ofSeconds(5));
        CardDtos.TodayCard edited = tx.execute(s -> cards.update(id, new CardDtos.CardUpdate(c.updatedAt(), true,
                List.of(new CardDtos.ItemText(itemId, "고친 앞면", "고친 뒷면", "집에서도 해 봐요"))), 1));
        assertThat(edited.items().getFirst().easyText()).isEqualTo("고친 앞면");
        assertThat(edited.needsCall()).isTrue();
        assertThat(edited.updatedAt()).isNotEqualTo(c.updatedAt());
        assertThat(diaries.get(id)).isEqualTo(before);              // 일지 내용·수정 시각 그대로
        assertThat(jdbc.queryForObject("SELECT count(*) FROM log_history", Integer.class)).isEqualTo(history);

        // 옛 updatedAt으로 다시 고치면 409
        assertCode(() -> tx.execute(s -> cards.update(id, new CardDtos.CardUpdate(c.updatedAt(), false,
                List.of(new CardDtos.ItemText(itemId, "나중", "나중", null))), 1)), HttpStatus.CONFLICT, "CARD_UPDATED_BY_OTHER");
        // 없는 항목, 빈 문장, 길이 초과
        assertCode(() -> tx.execute(s -> cards.update(id, new CardDtos.CardUpdate(edited.updatedAt(), false,
                List.of(new CardDtos.ItemText("99999", "a", "b", null))), 1)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertCode(() -> tx.execute(s -> cards.update(id, new CardDtos.CardUpdate(edited.updatedAt(), false,
                List.of(new CardDtos.ItemText(itemId, " ", "b", null))), 1)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertCode(() -> tx.execute(s -> cards.update(id, new CardDtos.CardUpdate(edited.updatedAt(), false,
                List.of(new CardDtos.ItemText(itemId, "가".repeat(101), "b", null))), 1)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void 완료_일지를_고치면_outdated_고친_문장은_남는다() {
        String id = completedDiaryWithCard(activity("10:00", "10:30", "예술활동", false));
        CardDtos.TodayCard c = card(id);
        tx.execute(s -> cards.update(id, new CardDtos.CardUpdate(c.updatedAt(), false,
                List.of(new CardDtos.ItemText(c.items().getFirst().id(), "담당자가 고침", "뒷면", null))), 1));

        DiaryResponse d = diaries.get(id);
        // 내용을 바꾸면 outdated, 고친 문장은 그대로
        // (같은 내용 재저장은 DiaryService가 이벤트 전에 되돌린다. 그 되돌리기는 Spring 트랜잭션 프록시가 있어야 해서 여기서는 확인하지 않는다)
        tx.execute(s -> diaries.update(id, diary("completed", "오후에 산책도 했어요.", d.updatedAt(),
                activity("10:00", "10:30", "예술활동", false)), 1));
        CardDtos.TodayCard outdated = card(id);
        assertThat(outdated.status()).isEqualTo("outdated");
        assertThat(outdated.items().getFirst().easyText()).isEqualTo("담당자가 고침");
        assertThat(requested).isEmpty();                            // 자동으로 다시 만들지 않는다
        assertCode(() -> tx.execute(s -> cards.send(id, 1)), HttpStatus.BAD_REQUEST, "CARD_NOT_READY");

        tx.execute(s -> cards.regenerate(id, 1));
        generateRequested();
        assertThat(card(id).items().getFirst().easyText()).isNotEqualTo("담당자가 고침");
        assertCode(() -> tx.execute(s -> cards.regenerate(id, 1)), HttpStatus.BAD_REQUEST, "CARD_NOT_REGENERABLE");
    }

    @Test
    void 권한_작성자_담당자_팀장만_고치고_보낼_수_있다() {
        String id = completedDiaryWithCard(activity("10:00", "10:30", "예술활동", false));
        CardDtos.TodayCard c = card(id);
        assertThat(tx.execute(s -> cards.get(id, 3)).status()).isEqualTo("ready");   // 조회는 누구나
        assertCode(() -> tx.execute(s -> cards.send(id, 3)), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertCode(() -> tx.execute(s -> cards.update(id, new CardDtos.CardUpdate(c.updatedAt(), true, List.of()), 3)),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(tx.execute(s -> cards.update(id, new CardDtos.CardUpdate(c.updatedAt(), true, List.of()), 2)).needsCall())
                .isTrue();                                          // 팀장
    }

    // ================================================================== 공유

    @Test
    void 동의가_없으면_막히고_보내면_같은_링크를_다시_복사할_수_있다() {
        String id = completedDiaryWithCard(activity("10:00", "10:30", "예술활동", false));
        jdbc.update("UPDATE client SET card_consent = FALSE");
        assertCode(() -> tx.execute(s -> cards.send(id, 1)), HttpStatus.BAD_REQUEST, "GUARDIAN_NOT_READY");
        jdbc.update("UPDATE client SET card_consent = TRUE");

        CardDtos.TodayCard sent = tx.execute(s -> cards.send(id, 1));
        assertThat(sent.status()).isEqualTo("sent");
        assertThat(sent.expiresOn()).isEqualTo("2026-11-14");
        assertThat(sent.shareUrl()).startsWith("https://welfare-fe.vercel.app/card/");
        assertThat(card(id).shareUrl()).isEqualTo(sent.shareUrl()); // 다시 열어도 같은 링크
        String token = sent.shareUrl().substring(sent.shareUrl().lastIndexOf('/') + 1);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM today_card", String.class)).isEqualTo(tokens.hash(token));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM today_card WHERE token_enc LIKE ?", Integer.class,
                "%" + token + "%")).isZero();                       // 원문은 저장하지 않는다

        assertCode(() -> tx.execute(s -> cards.send(id, 1)), HttpStatus.BAD_REQUEST, "CARD_ALREADY_SENT");
        assertCode(() -> tx.execute(s -> cards.update(id, new CardDtos.CardUpdate(sent.updatedAt(), true, List.of()), 1)),
                HttpStatus.BAD_REQUEST, "CARD_ALREADY_SENT");
        // 보낸 뒤 일지를 고쳐도 카드는 그대로
        DiaryResponse d = diaries.get(id);
        tx.execute(s -> diaries.update(id, diary("completed", "바뀐 메모", d.updatedAt(),
                activity("10:00", "10:30", "예술활동", false)), 1));
        assertThat(card(id).status()).isEqualTo("sent");
    }

    // ================================================================== 보호자 화면

    String sentToken() {
        String id = completedDiaryWithCard(activity("10:00", "10:30", "예술활동", false),
                activity("11:00", "11:30", "체육활동", false));
        String url = tx.execute(s -> cards.send(id, 1)).shareUrl();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    @Test
    void 보호자_확인_전에는_403_확인하면_카드가_보이고_실명은_없다() throws Exception {
        String token = sentToken();
        assertCode(() -> guardians.get(token, null), HttpStatus.FORBIDDEN, "CARD_VERIFY_REQUIRED");
        assertCode(() -> guardians.verify(token, "0101"), HttpStatus.BAD_REQUEST, "CARD_VERIFY_FAILED");

        GuardianCardService.Verified ok = guardians.verify(token, "0307");
        CardDtos.GuardianCard card = guardians.get(token, ok.cookieValue());
        assertThat(card.nickname()).isEqualTo("하늘");
        assertThat(card.items()).hasSize(2);
        String json = new ObjectMapper().writeValueAsString(card);
        assertThat(json).doesNotContain(REAL_NAME, BIRTH, STAFF_NAME, "diaryId", "updatedAt");
        // 다른 링크의 쿠키로는 열리지 않는다
        assertCode(() -> guardians.get(token, tokens.cookieValue(tokens.hash("other"), ok.maxAgeSeconds() + 1)),
                HttpStatus.FORBIDDEN, "CARD_VERIFY_REQUIRED");
    }

    @Test
    void 다섯_번_틀리면_30분_잠긴다() {
        String token = sentToken();
        for (int i = 0; i < 4; i++) {
            assertCode(() -> guardians.verify(token, "1231"), HttpStatus.BAD_REQUEST, "CARD_VERIFY_FAILED");
        }
        assertCode(() -> guardians.verify(token, "1231"), HttpStatus.TOO_MANY_REQUESTS, "CARD_VERIFY_LOCKED");
        assertCode(() -> guardians.verify(token, "0307"), HttpStatus.TOO_MANY_REQUESTS, "CARD_VERIFY_LOCKED");
        clock.advance(Duration.ofMinutes(31));
        assertThat(guardians.verify(token, "0307").cookieValue()).isNotBlank();
    }

    @Test
    void 고른_결과가_담당자_화면에_보이고_만료와_없는_링크는_410_404() {
        String token = sentToken();
        String cookie = guardians.verify(token, "0307").cookieValue();
        assertCode(() -> tx.execute(s -> guardians.pick(token, null, new CardDtos.PickRequest(null, null, true))),
                HttpStatus.FORBIDDEN, "CARD_VERIFY_REQUIRED");
        CardDtos.GuardianCard card = guardians.get(token, cookie);
        String first = card.items().getFirst().id();
        assertCode(() -> tx.execute(s -> guardians.pick(token, cookie, new CardDtos.PickRequest("99999", "pointed", false))),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        CardDtos.CardPick pick = tx.execute(s -> guardians.pick(token, cookie, new CardDtos.PickRequest(first, "pointed", false)));
        assertThat(pick.itemId()).isEqualTo(first);
        assertThat(card("1").pick().how()).isEqualTo("pointed");    // 담당자 화면에 전해진다
        tx.execute(s -> guardians.pick(token, cookie, new CardDtos.PickRequest(null, null, true)));   // 다시 고르면 덮어씀
        assertThat(card("1").pick().skipped()).isTrue();

        assertCode(() -> guardians.get("아무토큰", cookie), HttpStatus.NOT_FOUND, "NOT_FOUND");
        clock.advance(Duration.ofDays(7));                          // 11-14: 마지막 날까지는 열린다
        assertThat(guardians.get(token, cookie).expiresOn()).isEqualTo("2026-11-14");
        clock.advance(Duration.ofDays(1));                          // 11-15
        assertCode(() -> guardians.get(token, cookie), HttpStatus.GONE, "CARD_EXPIRED");
    }

    // ------------------------------------------------------------------

    /** 시각을 앞으로 돌릴 수 있는 시계 */
    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
