package kr.seoil.welfare.diary;

import static org.assertj.core.api.Assertions.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import kr.seoil.welfare.auth.*;
import kr.seoil.welfare.client.ServiceUserService;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.dto.*;
import kr.seoil.welfare.staff.StaffQueries;
import kr.seoil.welfare.stats.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 PostgreSQL과 운영 Flyway 마이그레이션으로 저장·재조회·집계·동시 수정을 검사한다. */
class PostgresRoundTripTest {
    static EmbeddedPostgres pg;
    static JdbcTemplate jdbc;
    static NamedParameterJdbcTemplate named;
    static TransactionTemplate tx;
    DiaryService diaries;
    StatsService stats;
    ServiceUserService users;
    static final StatsPeriod PERIOD = new StatsPeriod("month", 2026, 11, 4, 1);

    @BeforeAll static void database() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        var source = pg.getPostgresDatabase();
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        named = new NamedParameterJdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.update("INSERT INTO staff (staff_id, login_id, password_hash, name, department, position, role) VALUES "
                + "(1,'qa.staff','unused','직원','팀','담당자','staff'),(2,'qa.lead','unused','팀장','팀','팀장','teamLead')");
    }
    @AfterAll static void stop() throws Exception { if (pg != null) pg.close(); }
    @BeforeEach void services() {
        jdbc.execute("TRUNCATE daily_log, client RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO client (client_id, name, birth_date, staff_id) VALUES (1,'가상 이용자','2000-01-01',1)");
        AuthService auth = new AuthService(new AuthRepository(jdbc), new StaffQueries(jdbc), new BCryptPasswordEncoder());
        Catalog catalog = new Catalog(jdbc);
        diaries = new DiaryService(new DiaryRepository(named), catalog, auth,
                Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        stats = new StatsService(new StatsRepository(named), catalog);
        users = new ServiceUserService(jdbc, auth);
    }
    SaveDiaryRequest request(String start, String end, String notes, String version) {
        ActivityDto activity = new ActivityDto(null, start, end, List.of("예술활동"), null, "활동", List.of("선택지 제공"), null,
                List.of("적극적 참여", "기타"), "집중", List.of(), null, true, List.of(new BehaviorDto("selfHarm", List.of("sh-1"), null)));
        return new SaveDiaryRequest("1", "2026-11-07", "completed", "맑음", "양호", null, List.of(activity), notes, version);
    }
    @ParameterizedTest @CsvSource({"00:00,00:01", "00:00,24:00", "09:00,10:00", "17:00,18:00", "23:59,24:00",
            "12:15,13:45", "10:00,11:00", "01:01,01:59", "18:30,21:17", "22:45,23:55"})
    void 실제DB_시간_왕복_통계응답_일치(String start, String end) {
        DiaryResponse saved = tx.execute(s -> diaries.create(request(start, end, "내용", null), 1));
        DiaryResponse loaded = diaries.get(saved.id());
        assertThat(loaded.activities().getFirst().startTime()).isEqualTo(start);
        assertThat(loaded.activities().getFirst().endTime()).isEqualTo(end);
        assertThat(loaded.activities().getFirst().preSupport()).containsExactly("선택지 제공");
        var summary = stats.summary(1, PERIOD);
        assertThat(summary.behavior().count()).isEqualTo(1);
        assertThat(summary.behavior().records().getFirst().endTime()).isEqualTo(end);
        assertThat(summary.preSupport()).anyMatch(item -> item.label().equals("선택지 제공") && item.count() == 1);
    }
    @Test void 종결_재개_이력보존과_직원권한() {
        tx.execute(s -> diaries.create(request("10:00", "11:00", "보존", null), 1));
        assertThatThrownBy(() -> tx.execute(s -> users.close("1", 1))).isInstanceOf(ApiException.class);
        assertThat(tx.execute(s -> users.close("1", 2)).status()).isEqualTo("closed");
        assertThat(stats.summary(1, PERIOD).completedDiaries()).isEqualTo(1);
        assertThat(tx.execute(s -> users.reopen("1", 2)).status()).isEqualTo("active");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_log", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM client WHERE closed_at IS NULL", Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints = {0,1,2,3,4,5,6,7,8,9})
    void 실제DB_같은버전_동시수정_한건만_성공(int attempt) throws Exception {
        DiaryResponse saved = tx.execute(s -> diaries.create(request("10:00", "11:00", "원본", null), 1));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> first = () -> editAfter(start, saved, "첫째" + attempt);
            Callable<Integer> second = () -> editAfter(start, saved, "둘째" + attempt);
            var a = pool.submit(first); var b = pool.submit(second); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
    }
    int editAfter(CountDownLatch start, DiaryResponse saved, String notes) throws Exception {
        start.await();
        try { tx.execute(s -> diaries.update(saved.id(), request("10:00", "11:00", notes, saved.updatedAt()), 1)); return 200; }
        catch (ApiException e) { return e.getStatus().value(); }
    }
}
