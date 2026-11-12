package kr.seoil.welfare.card;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.card.CardRepository.CardRow;
import kr.seoil.welfare.card.CardRepository.ItemRow;
import kr.seoil.welfare.card.CardRepository.LogInfo;
import kr.seoil.welfare.card.CardRepository.PickRow;
import kr.seoil.welfare.card.CardRepository.TextEdit;
import kr.seoil.welfare.card.ai.CardSource;
import kr.seoil.welfare.card.ai.GeneratedCard;
import kr.seoil.welfare.card.dto.CardDtos;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.DiaryService;
import kr.seoil.welfare.diary.dto.ActivityDto;
import kr.seoil.welfare.diary.dto.BehaviorDto;
import kr.seoil.welfare.diary.dto.DiaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘 카드 — 담당자 쪽 규칙 (docs/오늘카드_API_명세.md)
 *
 *  1. 일지를 작성 완료하면 카드를 generating으로 만들고, 커밋 뒤 비동기로 AI를 부른다 (CardEvents → CardGenerationWorker)
 *     - 활동이 하나도 없는 일지는 카드를 만들지 않는다
 *     - 완료 일지를 고치면: 카드가 ready면 outdated, 카드가 없으면(기능 전 일지) 새로 만든다, 그 밖에는 그대로
 *  2. generating이 5분 넘게 이어지면(생성 중 서버 재시작 등) 조회할 때 failed로 바꾼다 → 다시 만들기로 회복
 *  3. 고치기는 ready·outdated에서만, 문장과 '따로 연락' 표시만. 일지는 건드리지 않는다
 *     불러올 때 받은 updatedAt이 다르면 409 CARD_UPDATED_BY_OTHER (일지 동시 수정과 같은 방식)
 *  4. 공유(send)는 ready에서만, 이용자에 카드 수신 동의가 있어야 한다. 링크는 7일 뒤 만료
 *     문자는 보내지 않는다. 응답의 shareUrl을 담당자가 복사해 보호자에게 직접 보낸다. 같은 링크를 언제든 다시 복사할 수 있다
 *  5. 다시 만들기는 failed·outdated에서만. 담당자가 고친 문장과 보호자가 고른 결과는 지워진다
 *  6. 권한: 조회는 직원 누구나 / 고치기·공유·다시 만들기는 일지 작성자, 이용자 담당자, 팀장·국장
 */
@Service
public class TodayCardService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final Duration STUCK_AFTER = Duration.ofMinutes(5);
    static final int LINK_DAYS = 7;
    static final String PROGRAM = "주간활동";
    static final String DEFAULT_NICKNAME = "이용자";
    private static final Set<String> MANAGERS = Set.of("teamLead", "director");

    private final CardRepository repo;
    private final DiaryService diaries;
    private final AuthService auth;
    private final CardTokens tokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String shareBaseUrl;

    public TodayCardService(CardRepository repo, DiaryService diaries, AuthService auth, CardTokens tokens,
                            ApplicationEventPublisher events, Clock clock,
                            @Value("${card.share-base-url}") String shareBaseUrl) {
        this.repo = repo;
        this.diaries = diaries;
        this.auth = auth;
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
        this.shareBaseUrl = shareBaseUrl.endsWith("/") ? shareBaseUrl.substring(0, shareBaseUrl.length() - 1) : shareBaseUrl;
    }

    // ==================================================================
    // 규칙 1: 일지 작성 완료 → 카드 (일지 저장 트랜잭션 안에서 불린다)
    // ==================================================================

    @Transactional
    public void onDiaryCompleted(long logId) {
        CardRow card = repo.lockByLogId(logId);
        if (card == null) {
            if (repo.hasActivities(logId) && repo.insertGenerating(logId, now())) {
                events.publishEvent(new CardGenerationRequested(logId));   // 커밋 뒤에 AI 호출
            }
            return;
        }
        if ("ready".equals(card.status())) {
            repo.setStatus(card.cardId(), "outdated", null, now());
        }
    }

    // ==================================================================
    // 조회 (규칙 2)
    // ==================================================================

    /** GET /api/diaries/{diaryId}/card */
    @Transactional
    public CardDtos.TodayCard get(String diaryId, long staffId) {
        auth.me(staffId);                                           // 사용 중지된 계정이면 403
        long logId = parseId(diaryId);
        LogInfo info = requireLog(logId);
        CardRow card = repo.findByLogId(logId);
        if (card == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "아직 오늘 카드가 없어요.");
        }
        if ("generating".equals(card.status()) && card.updatedAt().isBefore(now().minus(STUCK_AFTER))
                && repo.failIfGenerating(card.cardId(), "생성 시간 초과 (서버 재시작 등)", now())) {
            card = repo.findByLogId(logId);
        }
        return toResponse(card, info);
    }

    // ==================================================================
    // 고치기 (규칙 3)
    // ==================================================================

    /** PUT /api/diaries/{diaryId}/card */
    @Transactional
    public CardDtos.TodayCard update(String diaryId, CardDtos.CardUpdate req, long staffId) {
        if (req == null) {
            throw ApiException.badRequest("고칠 내용을 보내 주세요.");
        }
        long logId = parseId(diaryId);
        LogInfo info = requireLog(logId);
        CardRow card = lockCard(logId);
        checkCanEdit(info, staffId);
        if ("sent".equals(card.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CARD_ALREADY_SENT", "이미 보낸 카드는 고칠 수 없어요.");
        }
        if (!"ready".equals(card.status()) && !"outdated".equals(card.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CARD_NOT_READY", "아직 카드를 고칠 수 없어요.");
        }
        checkNotUpdatedByOther(req.updatedAt(), card);

        Set<Long> ids = new HashSet<>();
        repo.items(card.cardId()).forEach(i -> ids.add(i.itemId()));
        List<TextEdit> edits = new ArrayList<>();
        for (CardDtos.ItemText t : req.items() == null ? List.<CardDtos.ItemText>of() : req.items()) {
            if (t == null) {
                throw ApiException.badRequest("비어 있는 카드 항목이 있어요.");
            }
            Long id = parseItemId(t.id());
            if (id == null || !ids.contains(id)) {
                throw ApiException.badRequest("이 카드에 없는 항목이에요.");
            }
            String easy = text(t.easyText(), CardRules.EASY_MAX, "앞면 문장");
            String guardian = text(t.guardianText(), CardRules.GUARDIAN_MAX, "보호자 메모");
            String tip = t.homeTip() == null || t.homeTip().isBlank() ? null : t.homeTip().trim();
            if (tip != null && tip.length() > CardRules.TIP_MAX) {
                throw ApiException.badRequest("집에서 해 보면 좋은 것은 " + CardRules.TIP_MAX + "자까지 쓸 수 있어요.");
            }
            edits.add(new TextEdit(id, easy, guardian, tip));
        }
        boolean needsCall = req.needsCall() == null ? card.needsCall() : req.needsCall();
        repo.updateTexts(card.cardId(), edits, needsCall, now());
        return toResponse(repo.findByLogId(logId), info);
    }

    // ==================================================================
    // 공유 링크 (규칙 4)
    // ==================================================================

    /** POST /api/diaries/{diaryId}/card/send */
    @Transactional
    public CardDtos.TodayCard send(String diaryId, long staffId) {
        long logId = parseId(diaryId);
        LogInfo info = requireLog(logId);
        CardRow card = lockCard(logId);
        checkCanEdit(info, staffId);
        if ("sent".equals(card.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CARD_ALREADY_SENT", "이미 보낸 카드예요. 링크를 다시 복사해 주세요.");
        }
        if (!"ready".equals(card.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CARD_NOT_READY", "확인을 마친 카드만 보낼 수 있어요.");
        }
        if (!info.consent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GUARDIAN_NOT_READY",
                    "이용자 정보에서 보호자의 카드 수신 동의를 먼저 등록해 주세요.");
        }
        String token = tokens.newToken();
        OffsetDateTime now = now();
        repo.markSent(card.cardId(), tokens.hash(token), tokens.encrypt(token), now,
                now.atZoneSameInstant(KST).toLocalDate().plusDays(LINK_DAYS));
        return toResponse(repo.findByLogId(logId), info);
    }

    // ==================================================================
    // 다시 만들기 (규칙 5)
    // ==================================================================

    /** POST /api/diaries/{diaryId}/card/regenerate */
    @Transactional
    public CardDtos.TodayCard regenerate(String diaryId, long staffId) {
        long logId = parseId(diaryId);
        LogInfo info = requireLog(logId);
        CardRow card = lockCard(logId);
        checkCanEdit(info, staffId);
        if (!"failed".equals(card.status()) && !"outdated".equals(card.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CARD_NOT_REGENERABLE", "지금은 카드를 다시 만들 수 없어요.");
        }
        repo.resetForRegenerate(card.cardId(), now());
        events.publishEvent(new CardGenerationRequested(logId));
        return toResponse(repo.findByLogId(logId), info);
    }

    // ==================================================================
    // AI 생성 단계 (CardGenerationWorker가 트랜잭션 밖에서 순서대로 부른다)
    // ==================================================================

    /** 만드는 중인 카드의 AI 입력. 만드는 중이 아니면(그사이 다른 처리) 빈 값 */
    @Transactional(readOnly = true)
    public Optional<Prepared> prepare(long logId) {
        CardRow card = repo.findByLogId(logId);
        LogInfo info = repo.logInfo(logId);
        if (card == null || info == null || !"generating".equals(card.status())) {
            return Optional.empty();
        }
        DiaryResponse diary = diaries.get(String.valueOf(logId));
        Map<String, String> pictograms = repo.participationPictograms();
        Map<String, String> detailNames = repo.behaviorDetailNames();
        List<String> behaviorWords = new ArrayList<>();
        List<CardSource.Activity> activities = new ArrayList<>();
        diary.activities().stream()
                .filter(a -> a.startTime() != null && !a.startTime().isBlank())
                .sorted((x, y) -> x.startTime().compareTo(y.startTime()))
                .forEach(a -> activities.add(activity(a, pictograms, detailNames, behaviorWords)));
        String condition = "기타".equals(diary.condition()) && diary.conditionOther() != null
                ? diary.conditionOther() : diary.condition();
        CardSource source = new CardSource(nickname(info), diary.date(), condition, diary.notes(), activities);
        return Optional.of(new Prepared(source, behaviorWords));
    }

    /** AI 입력과 앞면 금지어 (그날 기록된 도전적 행동 이름) */
    public record Prepared(CardSource source, List<String> behaviorWords) {
    }

    /** AI 결과 저장. 검증에 실패하면 failed */
    @Transactional
    public void complete(long logId, Prepared prepared, GeneratedCard generated) {
        CardRow card = repo.lockByLogId(logId);
        if (card == null || !"generating".equals(card.status())) {
            return;                                                 // 그사이 시간 초과로 failed가 됐거나 지워짐
        }
        CardRules.Valid valid;
        try {
            valid = CardRules.validate(prepared.source(), generated, prepared.behaviorWords());
        } catch (CardRules.InvalidCard e) {
            repo.failIfGenerating(card.cardId(), "검증 실패: " + e.getMessage(), now());
            return;
        }
        repo.saveResult(card.cardId(), valid.summary(), valid.items(), now());
    }

    @Transactional
    public void fail(long logId, String reason) {
        CardRow card = repo.findByLogId(logId);
        if (card != null) {
            repo.failIfGenerating(card.cardId(), reason == null ? "알 수 없는 오류" : truncate(reason, 500), now());
        }
    }

    // ==================================================================
    // 도우미
    // ==================================================================

    private CardSource.Activity activity(ActivityDto a, Map<String, String> pictograms,
                                         Map<String, String> detailNames, List<String> behaviorWords) {
        String label = a.participation() == null || a.participation().isEmpty() ? "활동" : a.participation().get(0);
        if ("기타".equals(label) && a.participationOther() != null && !a.participationOther().isBlank()) {
            label = a.participationOther().trim();
        }
        String pictogram = pictograms.getOrDefault(a.participation() == null || a.participation().isEmpty()
                ? "" : a.participation().get(0), "favorite");
        List<String> behaviors = new ArrayList<>();
        if (a.hasBehavior() && a.behaviors() != null) {
            for (BehaviorDto b : a.behaviors()) {
                String area = AREA_NAMES.getOrDefault(b.type(), b.type());
                List<String> names = new ArrayList<>();
                for (String code : b.detailIds() == null ? List.<String>of() : b.detailIds()) {
                    String name = detailNames.getOrDefault(code, code);
                    if ("기타".equals(name) && b.otherText() != null && !b.otherText().isBlank()) {
                        name = b.otherText().trim();
                    }
                    names.add(name);
                    behaviorWords.add(name);
                }
                if (names.isEmpty() && b.otherText() != null && !b.otherText().isBlank()) {
                    names.add(b.otherText().trim());                // 상위 '기타'는 직접 입력만 있다
                    behaviorWords.add(b.otherText().trim());
                }
                behaviors.add(area + ": " + String.join(", ", names));
            }
        }
        return new CardSource.Activity(parseRecordId(a.id()), a.startTime(), label, pictogram, a.detail(),
                withOther(a.preSupport(), a.preSupportOther()), withOther(a.reaction(), a.reactionOther()),
                withOther(a.positiveSupport(), a.positiveSupportOther()), behaviors);
    }

    private static final Map<String, String> AREA_NAMES = Map.of(
            "otherHarm", "타해", "selfHarm", "자해", "destruction", "파괴행동", "other", "기타");

    /** '기타'를 직접 쓴 글로 바꾼 목록 */
    private static List<String> withOther(List<String> picked, String other) {
        List<String> result = new ArrayList<>();
        for (String p : picked == null ? List.<String>of() : picked) {
            result.add("기타".equals(p) && other != null && !other.isBlank() ? other.trim() : p);
        }
        return result;
    }

    CardDtos.TodayCard toResponse(CardRow card, LogInfo info) {
        boolean showItems = !"generating".equals(card.status()) && !"failed".equals(card.status());
        List<CardDtos.CardItem> items = showItems ? items(card.cardId()) : List.of();
        String shareUrl = null;
        if ("sent".equals(card.status())) {
            String token = tokens.decrypt(card.tokenEnc());
            shareUrl = token == null ? null : shareBaseUrl + "/card/" + token;
        }
        return new CardDtos.TodayCard(
                String.valueOf(card.logId()), card.status(), nickname(info), info.date().toString(), PROGRAM,
                card.summary() == null ? "" : card.summary(), items, card.needsCall(), iso(card.updatedAt()),
                iso(card.sentAt()), card.expiresOn() == null ? null : card.expiresOn().toString(), shareUrl,
                pick(card.cardId()));
    }

    List<CardDtos.CardItem> items(long cardId) {
        return repo.items(cardId).stream().map(TodayCardService::item).toList();
    }

    static CardDtos.CardItem item(ItemRow i) {
        return new CardDtos.CardItem(String.valueOf(i.itemId()), i.startTime().toString().substring(0, 5),
                i.activityLabel(), i.word(), i.pictogram(), i.mood(), i.easyText(), i.guardianText(), i.homeTip());
    }

    CardDtos.CardPick pick(long cardId) {
        PickRow p = repo.pick(cardId);
        if (p == null) {
            return null;
        }
        return new CardDtos.CardPick(p.itemId() == null ? null : String.valueOf(p.itemId()), p.how(), p.skipped(),
                iso(p.pickedAt()));
    }

    static String nickname(LogInfo info) {
        return info.nickname() == null || info.nickname().isBlank() ? DEFAULT_NICKNAME : info.nickname();
    }

    private LogInfo requireLog(long logId) {
        LogInfo info = repo.logInfo(logId);
        if (info == null) {
            throw ApiException.notFound("일지");
        }
        return info;
    }

    private CardRow lockCard(long logId) {
        CardRow card = repo.lockByLogId(logId);
        if (card == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "아직 오늘 카드가 없어요.");
        }
        return card;
    }

    /** 규칙 6: 일지 작성자, 이용자 담당자, 팀장·국장 */
    private void checkCanEdit(LogInfo info, long staffId) {
        String role = auth.me(staffId).role();                      // 사용 중지된 계정이면 403
        boolean allowed = info.createdBy() == staffId
                || (info.clientStaffId() != null && info.clientStaffId() == staffId)
                || MANAGERS.contains(role);
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "일지 작성자, 이용자 담당자, 팀장·국장만 카드를 고치거나 보낼 수 있어요.");
        }
    }

    private static void checkNotUpdatedByOther(String requestUpdatedAt, CardRow card) {
        if (requestUpdatedAt == null || requestUpdatedAt.isBlank()) {
            throw ApiException.badRequest("카드를 다시 불러온 뒤 저장해 주세요.");
        }
        boolean same;
        try {
            same = OffsetDateTime.parse(requestUpdatedAt.trim()).toInstant().equals(card.updatedAt().toInstant());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("updatedAt 형식이 올바르지 않아요.");
        }
        if (!same) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("updatedAt", iso(card.updatedAt()));
            throw new ApiException(HttpStatus.CONFLICT, "CARD_UPDATED_BY_OTHER",
                    "다른 분이 먼저 이 카드를 고쳤어요. 최신 내용을 불러와 주세요.", details);
        }
    }

    private static String text(String value, int max, String what) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw ApiException.badRequest(what + "을 입력해 주세요.");
        }
        if (v.length() > max) {
            throw ApiException.badRequest(what + "은 " + max + "자까지 쓸 수 있어요.");
        }
        return v;
    }

    /** DB가 마이크로초까지 저장하므로 미리 잘라 둔다 (돌려준 updatedAt과 DB 값이 같게) */
    OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    static String iso(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toOffsetDateTime().toString();
    }

    private static long parseId(String id) {
        try {
            long parsed = Long.parseLong(id == null ? "" : id.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 404
        }
        throw ApiException.notFound("일지");
    }

    private static Long parseItemId(String id) {
        try {
            return Long.parseLong(id == null ? "" : id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseRecordId(String id) {
        try {
            return id == null ? null : Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
