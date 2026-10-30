package kr.seoil.welfare.diary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.Catalog.BehaviorItem;
import kr.seoil.welfare.diary.Catalog.Item;
import kr.seoil.welfare.diary.Catalog.Kind;
import kr.seoil.welfare.diary.DiaryRepository.LockedLog;
import kr.seoil.welfare.diary.DiaryRepository.LogRow;
import kr.seoil.welfare.diary.DiaryRepository.NewPick;
import kr.seoil.welfare.diary.DiaryRepository.PickRow;
import kr.seoil.welfare.diary.DiaryRepository.RecordRow;
import kr.seoil.welfare.diary.dto.ActivityDto;
import kr.seoil.welfare.diary.dto.BehaviorDto;
import kr.seoil.welfare.diary.dto.DiaryResponse;
import kr.seoil.welfare.diary.dto.NewsEntryDto;
import kr.seoil.welfare.diary.dto.SaveDiaryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일지 업무 규칙 (기능명세서 4.3~4.5, 8.2)
 *
 *  1. 이용자 1명당 하루 1건 — 이미 있으면 409 DIARY_ALREADY_EXISTS + 기존 일지 번호
 *  2. 상태는 작성 중 → 작성 완료 한 방향 — 완료를 작성 중으로 저장하면 400 INVALID_STATUS_TRANSITION
 *  3. 작성자는 서버가 정한다 — 처음 저장한 직원, 수정해도 그대로
 *  4. 저장할 때 활동은 통째로 교체 (지우고 다시 넣기)
 *  5. 작성 완료로 저장할 때만 소식 이력을 남긴다 — 처음 완료 = created, 완료 후 수정 = updated
 *  6. 활동 시간은 10:00~17:00, 30분 단위, 같은 일지 안에서 겹치지 않게 (명세서 8.5, 프론트 ACTIVITY_SLOTS와 같음)
 *     작성 완료로 저장할 때는 모든 활동에 시간이 있어야 한다
 */
@Service
public class DiaryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> STATUSES = Set.of("draft", "completed");
    /** 도전적 행동 영역의 화면 순서 (프론트 BEHAVIOR_TYPES와 같음) */
    private static final List<String> BEHAVIOR_TYPES = List.of("otherHarm", "selfHarm", "destruction", "other");
    /** 연결 테이블이 있는 다중 선택 항목 */
    private static final List<Kind> MULTI = List.of(Kind.PARTICIPATION, Kind.PRE_SUPPORT, Kind.REACTION, Kind.POSITIVE_SUPPORT);
    /** '기타' 직접 입력 최대 글자 수 (DB VARCHAR(200)) */
    private static final int ETC_MAX = 200;
    /** 활동 시간 범위와 단위 (복지관 양식) */
    private static final LocalTime FIRST_TIME = LocalTime.of(10, 0);
    private static final LocalTime LAST_TIME = LocalTime.of(17, 0);
    private static final int SLOT_MINUTES = 30;

    private final DiaryRepository repo;
    private final Catalog catalog;

    public DiaryService(DiaryRepository repo, Catalog catalog) {
        this.repo = repo;
        this.catalog = catalog;
    }

    // ==================================================================
    // 조회
    // ==================================================================

    /** GET /api/diaries — 조건은 모두 선택 사항 */
    @Transactional(readOnly = true)
    public List<DiaryResponse> list(String userId, String from, String to, String status) {
        Long clientId = blank(userId) ? null : parseId(userId, "이용자");
        if (!blank(status) && !STATUSES.contains(status)) {
            throw ApiException.badRequest("status는 draft 또는 completed만 가능해요.");
        }
        List<LogRow> logs = repo.findLogs(clientId, blank(from) ? null : parseDate(from),
                blank(to) ? null : parseDate(to), blank(status) ? null : status);
        return assemble(logs);
    }

    /** GET /api/diaries/{id} — 없으면 404 */
    @Transactional(readOnly = true)
    public DiaryResponse get(String diaryId) {
        LogRow log = repo.findLog(parsePathId(diaryId));
        if (log == null) {
            throw ApiException.notFound("일지");
        }
        return assemble(List.of(log)).get(0);
    }

    /** GET /api/news?since= — 오늘의 소식 */
    @Transactional(readOnly = true)
    public List<NewsEntryDto> news(String since) {
        if (blank(since)) {
            throw ApiException.badRequest("since 날짜가 필요해요.");
        }
        return repo.findHistorySince(parseDate(since)).stream()
                .map(h -> new NewsEntryDto(
                        new NewsEntryDto.Event(String.valueOf(h.id()), String.valueOf(h.logId()), h.kind(),
                                String.valueOf(h.actorId()), iso(h.occurredAt())),
                        new NewsEntryDto.DiaryRef(String.valueOf(h.logId()), String.valueOf(h.clientId()),
                                h.date().toString())))
                .toList();
    }

    // ==================================================================
    // 저장
    // ==================================================================

    /** POST /api/diaries — 새 일지 */
    @Transactional                                                  // 중간에 실패하면 전부 되돌림
    public DiaryResponse create(SaveDiaryRequest req, long actorId) {
        Checked c = check(req);                                     // 입력값 검사 + 이름→번호 변환

        if (!repo.clientExists(c.clientId)) {
            throw ApiException.badRequest("없는 이용자예요.");
        }
        // 규칙 1: 같은 이용자·같은 날짜 일지가 있으면 409
        Long existing = repo.findLogIdByClientAndDate(c.clientId, c.date);
        if (existing != null) {
            throw new ApiException(HttpStatus.CONFLICT, "DIARY_ALREADY_EXISTS",
                    "이 이용자의 같은 날짜 일지가 이미 있어요.",
                    Map.of("existingDiaryId", String.valueOf(existing)));
        }

        // 규칙 3: 작성자 = 지금 로그인한 직원
        long logId = repo.insertLog(c.clientId, c.date, c.status, c.weather.id(), c.condition.id(),
                c.conditionEtc, c.remark, actorId);
        insertActivities(logId, c.activities);

        // 규칙 5: 작성 완료로 바로 저장하면 '등록' 소식
        if ("completed".equals(c.status)) {
            repo.insertHistory(logId, "created", actorId);
        }
        return get(String.valueOf(logId));
    }

    /** PUT /api/diaries/{id} — 기존 일지 수정 */
    @Transactional
    public DiaryResponse update(String diaryId, SaveDiaryRequest req, long actorId) {
        long logId = parsePathId(diaryId);
        Checked c = check(req);

        LockedLog before = repo.lockLog(logId);                     // 잠그고 읽기
        if (before == null) {
            throw ApiException.notFound("일지");
        }
        // 저장된 일지의 이용자·날짜는 바꾸지 않는다 (명세서 9장 B-8)
        if (before.clientId() != c.clientId || !before.date().equals(c.date)) {
            throw ApiException.badRequest("저장된 일지의 이용자나 날짜는 바꿀 수 없어요.");
        }
        boolean wasCompleted = "completed".equals(before.status());
        // 규칙 2: 완료 → 작성 중 되돌리기 금지
        if (wasCompleted && "draft".equals(c.status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "작성 완료된 일지는 작성 중으로 되돌릴 수 없어요.");
        }

        // 완료된 일지를 다시 저장 = 수정 → 수정자 기록
        repo.updateLog(logId, c.status, c.weather.id(), c.condition.id(), c.conditionEtc, c.remark,
                actorId, wasCompleted);

        // 규칙 4: 활동 통째로 교체
        repo.deleteRecords(logId);
        insertActivities(logId, c.activities);

        // 규칙 5: 완료로 저장할 때만 소식 이력
        if ("completed".equals(c.status)) {
            repo.insertHistory(logId, wasCompleted ? "updated" : "created", actorId);
        }
        return get(String.valueOf(logId));
    }

    // ==================================================================
    // 입력 검사
    // ==================================================================

    /** 검사를 통과한 저장용 값 */
    private record Checked(long clientId, LocalDate date, String status, Item weather, Item condition,
                           String conditionEtc, String remark, List<CheckedActivity> activities) {
    }

    /** 검사를 통과한 활동 1개 */
    private record CheckedActivity(LocalTime start, LocalTime end, String content, boolean hasBehavior,
                                   Map<Kind, List<NewPick>> picks, List<NewPick> behaviors) {
    }

    private Checked check(SaveDiaryRequest req) {
        if (req == null) {
            throw ApiException.badRequest("저장할 내용이 없어요.");
        }
        if (blank(req.userId())) {
            throw ApiException.badRequest("대상 이용자를 선택해 주세요.");
        }
        if (blank(req.date())) {
            throw ApiException.badRequest("일지 날짜가 필요해요.");
        }
        if (req.status() == null || !STATUSES.contains(req.status())) {     // Set.of는 null을 물으면 오류라 먼저 검사
            throw ApiException.badRequest("status는 draft 또는 completed만 가능해요.");
        }
        if (blank(req.weather())) {
            throw ApiException.badRequest("날씨를 선택해 주세요.");
        }
        if (blank(req.condition())) {
            throw ApiException.badRequest("컨디션을 선택해 주세요.");
        }
        Item weather = catalog.byLabel(Kind.WEATHER, req.weather());
        Item condition = catalog.byLabel(Kind.CONDITION, req.condition());
        // '기타' 컨디션일 때만 직접 입력을 저장
        String conditionEtc = condition.etc() ? etcText(req.conditionOther()) : null;

        boolean completed = "completed".equals(req.status());
        List<CheckedActivity> activities = new ArrayList<>();
        if (req.activities() != null) {
            for (ActivityDto a : req.activities()) {
                activities.add(checkActivity(a, completed));
            }
        }
        checkNoOverlap(activities);
        return new Checked(parseId(req.userId(), "이용자"), parseDate(req.date()), req.status(),
                weather, condition, conditionEtc, trimToNull(req.notes()), activities);
    }

    private CheckedActivity checkActivity(ActivityDto a, boolean completed) {
        if (a == null) {
            throw ApiException.badRequest("비어 있는 활동이 있어요.");
        }
        LocalTime start = parseTime(a.startTime());
        LocalTime end = parseTime(a.endTime());
        // 작성 중은 시간을 비워 둘 수 있지만, 작성 완료는 모든 활동에 시간이 있어야 한다
        if (completed && (start == null || end == null)) {
            throw ApiException.badRequest("활동 시간을 골라 주세요.");
        }
        if (start != null && end != null && !end.isAfter(start)) {
            throw ApiException.badRequest("종료 시간은 시작 시간보다 늦어야 해요.");
        }
        checkSlotTime(start);
        checkSlotTime(end);

        // 다중 선택 4종류: 이름 → 번호, '기타' 직접 입력 붙이기
        Map<Kind, List<NewPick>> picks = new HashMap<>();
        picks.put(Kind.PARTICIPATION, toPicks(Kind.PARTICIPATION, a.participation(), a.participationOther()));
        picks.put(Kind.PRE_SUPPORT, toPicks(Kind.PRE_SUPPORT, a.preSupport(), a.preSupportOther()));
        picks.put(Kind.REACTION, toPicks(Kind.REACTION, a.reaction(), a.reactionOther()));
        picks.put(Kind.POSITIVE_SUPPORT, toPicks(Kind.POSITIVE_SUPPORT, a.positiveSupport(), a.positiveSupportOther()));

        // 사전 지원 '없음'은 다른 항목과 같이 고를 수 없음 (FR-21)
        List<NewPick> pre = picks.get(Kind.PRE_SUPPORT);
        boolean hasNone = pre.stream().anyMatch(p -> "none".equals(catalog.byId(Kind.PRE_SUPPORT, p.itemId()).code()));
        if (hasNone && pre.size() > 1) {
            throw ApiException.badRequest("사전 지원 '없음'은 다른 항목과 함께 선택할 수 없어요.");
        }

        // 도전적 행동: '있음'일 때만 저장
        List<NewPick> behaviors = a.hasBehavior() ? toBehaviorPicks(a.behaviors()) : List.of();

        return new CheckedActivity(start, end, trimToNull(a.detail()), a.hasBehavior(), picks, behaviors);
    }

    /** 활동 시각은 10:00~17:00 사이의 30분 단위만 */
    private static void checkSlotTime(LocalTime t) {
        if (t == null) {
            return;
        }
        if (t.isBefore(FIRST_TIME) || t.isAfter(LAST_TIME) || t.getMinute() % SLOT_MINUTES != 0 || t.getSecond() != 0) {
            throw ApiException.badRequest("활동 시간은 10:00~17:00 사이에서 30분 단위로 골라 주세요.");
        }
    }

    /** 같은 일지 안의 활동 시간이 겹치면 거부 (시간이 있는 활동끼리만 비교) */
    private static void checkNoOverlap(List<CheckedActivity> activities) {
        List<CheckedActivity> timed = activities.stream()
                .filter(a -> a.start() != null && a.end() != null)
                .sorted(Comparator.comparing(CheckedActivity::start))
                .toList();
        for (int i = 1; i < timed.size(); i++) {
            if (timed.get(i).start().isBefore(timed.get(i - 1).end())) {
                throw ApiException.badRequest("활동 시간이 다른 활동과 겹쳐요.");
            }
        }
    }

    /** 이름 목록 → 저장할 선택 항목. 같은 이름이 두 번 와도 한 번만 */
    private List<NewPick> toPicks(Kind kind, List<String> labels, String otherText) {
        Map<Short, NewPick> result = new LinkedHashMap<>();         // 순서 유지 + 중복 제거
        if (labels != null) {
            for (String label : labels) {
                Item item = catalog.byLabel(kind, label);
                String etc = item.etc() ? etcText(otherText) : null;      // '기타'에만 직접 입력
                result.putIfAbsent(item.id(), new NewPick(item.id(), etc));
            }
        }
        return new ArrayList<>(result.values());
    }

    /** 도전적 행동 선택 → record_behavior에 넣을 세부 항목 */
    private List<NewPick> toBehaviorPicks(List<BehaviorDto> selections) {
        Map<Short, NewPick> result = new LinkedHashMap<>();
        if (selections == null) {
            return List.of();
        }
        for (BehaviorDto b : selections) {
            if (b == null || b.type() == null || !BEHAVIOR_TYPES.contains(b.type())) {
                throw ApiException.badRequest("알 수 없는 도전적 행동 유형이에요.");
            }
            if ("other".equals(b.type())) {
                // 상위 '기타'는 세부 목록 없이 직접 입력만 → 'other' 항목 하나로 저장
                BehaviorItem item = catalog.behaviorByCode("other");
                result.putIfAbsent(item.id(), new NewPick(item.id(), etcText(b.otherText())));
                continue;
            }
            if (b.detailIds() == null) {
                continue;
            }
            for (String code : b.detailIds()) {
                BehaviorItem item = catalog.behaviorByCode(code);
                if (item == null || !item.areaCode().equals(b.type())) {    // 다른 영역 항목이 섞이면 거부
                    throw ApiException.badRequest("'" + code + "'은(는) 선택할 수 없는 세부 행동이에요.");
                }
                String etc = item.etc() ? etcText(b.otherText()) : null;
                result.putIfAbsent(item.id(), new NewPick(item.id(), etc));
            }
        }
        return new ArrayList<>(result.values());
    }

    /** 활동들을 순서대로 저장 */
    private void insertActivities(long logId, List<CheckedActivity> activities) {
        for (CheckedActivity a : activities) {
            long recordId = repo.insertRecord(logId, a.start(), a.end(), a.content(), a.hasBehavior());
            for (Kind kind : MULTI) {
                repo.insertPicks(kind.junction(), kind.idColumn(), recordId, a.picks().get(kind));
            }
            repo.insertPicks("record_behavior", "item_id", recordId, a.behaviors());
        }
    }

    // ==================================================================
    // DB 결과 → 응답 JSON 모양으로 조립
    // ==================================================================

    private List<DiaryResponse> assemble(List<LogRow> logs) {
        if (logs.isEmpty()) {
            return List.of();
        }
        // 1) 활동을 일지별로 묶기
        List<RecordRow> records = repo.findRecords(logs.stream().map(LogRow::id).toList());
        Map<Long, List<RecordRow>> recordsByLog = new HashMap<>();
        for (RecordRow r : records) {
            recordsByLog.computeIfAbsent(r.logId(), k -> new ArrayList<>()).add(r);
        }

        // 2) 선택 항목을 활동별로 묶기 (종류마다 쿼리 1번)
        List<Long> recordIds = records.stream().map(RecordRow::id).toList();
        Map<Kind, Map<Long, List<PickRow>>> picks = new HashMap<>();
        Map<Long, List<PickRow>> behaviorPicks = new HashMap<>();
        if (!recordIds.isEmpty()) {
            for (Kind kind : MULTI) {
                picks.put(kind, groupByRecord(repo.findPicks(kind.junction(), kind.idColumn(), recordIds)));
            }
            behaviorPicks = groupByRecord(repo.findPicks("record_behavior", "item_id", recordIds));
        }

        // 3) 일지마다 응답 만들기
        List<DiaryResponse> result = new ArrayList<>();
        for (LogRow log : logs) {
            List<ActivityDto> activities = new ArrayList<>();
            for (RecordRow r : recordsByLog.getOrDefault(log.id(), List.of())) {
                activities.add(toActivity(r, picks, behaviorPicks.getOrDefault(r.id(), List.of())));
            }
            Item weather = catalog.byId(Kind.WEATHER, log.weatherId());
            Item condition = catalog.byId(Kind.CONDITION, log.conditionId());
            result.add(new DiaryResponse(
                    String.valueOf(log.id()),
                    String.valueOf(log.clientId()),
                    log.date().toString(),
                    String.valueOf(log.createdBy()),
                    log.updatedBy() == null ? null : String.valueOf(log.updatedBy()),
                    log.status(),
                    catalog.label(Kind.WEATHER, weather),
                    catalog.label(Kind.CONDITION, condition),
                    log.conditionEtc(),
                    activities,
                    log.remark() == null ? "" : log.remark(),          // 프론트는 notes를 문자열로 기대
                    iso(log.createdAt()),
                    iso(log.updatedAt())));
        }
        return result;
    }

    private ActivityDto toActivity(RecordRow r, Map<Kind, Map<Long, List<PickRow>>> picks, List<PickRow> behaviorRows) {
        Labels participation = labels(Kind.PARTICIPATION, picks, r.id());
        Labels pre = labels(Kind.PRE_SUPPORT, picks, r.id());
        Labels reaction = labels(Kind.REACTION, picks, r.id());
        Labels positive = labels(Kind.POSITIVE_SUPPORT, picks, r.id());
        return new ActivityDto(
                String.valueOf(r.id()),
                r.start() == null ? null : r.start().format(HH_MM),
                r.end() == null ? null : r.end().format(HH_MM),
                participation.names(), participation.other(),
                r.content() == null ? "" : r.content(),
                pre.names(), pre.other(),
                reaction.names(), reaction.other(),
                positive.names(), positive.other(),
                r.hasBehavior(),
                behaviors(behaviorRows));
    }

    /** 선택 이름 목록 + '기타' 직접 입력 */
    private record Labels(List<String> names, String other) {
    }

    private Labels labels(Kind kind, Map<Kind, Map<Long, List<PickRow>>> picks, long recordId) {
        List<PickRow> rows = picks.getOrDefault(kind, Map.of()).getOrDefault(recordId, List.of());
        List<Item> items = new ArrayList<>();
        String other = null;
        for (PickRow row : rows) {
            Item item = catalog.byId(kind, row.itemId());
            items.add(item);
            if (item.etc()) {
                other = row.etcText();
            }
        }
        items.sort(Comparator.comparingInt(Item::sortOrder));          // 화면 목록 순서대로
        return new Labels(items.stream().map(i -> catalog.label(kind, i)).toList(), other);
    }

    /** record_behavior 줄들 → 영역별 BehaviorDto */
    private List<BehaviorDto> behaviors(List<PickRow> rows) {
        Map<String, List<BehaviorItem>> byArea = new HashMap<>();
        Map<String, String> otherText = new HashMap<>();
        for (PickRow row : rows) {
            BehaviorItem item = catalog.behaviorById(row.itemId());
            byArea.computeIfAbsent(item.areaCode(), k -> new ArrayList<>()).add(item);
            if (item.etc()) {
                otherText.put(item.areaCode(), row.etcText());
            }
        }
        List<BehaviorDto> result = new ArrayList<>();
        for (String type : BEHAVIOR_TYPES) {                           // 타해 → 자해 → 파괴 → 기타 순
            List<BehaviorItem> items = byArea.get(type);
            if (items == null) {
                continue;
            }
            items.sort(Comparator.comparingInt(BehaviorItem::sortOrder));
            // 상위 '기타'는 세부 목록을 비워서 돌려준다 (프론트 규칙)
            List<String> detailIds = "other".equals(type) ? List.of() : items.stream().map(BehaviorItem::code).toList();
            result.add(new BehaviorDto(type, detailIds, otherText.get(type)));
        }
        return result;
    }

    private static Map<Long, List<PickRow>> groupByRecord(List<PickRow> rows) {
        Map<Long, List<PickRow>> map = new HashMap<>();
        for (PickRow row : rows) {
            map.computeIfAbsent(row.recordId(), k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    // ==================================================================
    // 작은 도우미
    // ==================================================================

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return blank(s) ? null : s.trim();
    }

    /** '기타' 직접 입력. DB 칸(200자)을 넘으면 서버 오류 대신 400으로 알려 준다 */
    private static String etcText(String s) {
        String text = trimToNull(s);
        if (text != null && text.length() > ETC_MAX) {
            throw ApiException.badRequest("'기타' 직접 입력은 " + ETC_MAX + "자까지 쓸 수 있어요.");
        }
        return text;
    }

    /** 요청 본문·검색 조건의 id ("3") → 숫자. 잘못되면 400 */
    private static long parseId(String id, String what) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(what + " 번호가 올바르지 않아요.");
        }
    }

    /** 주소의 id (/api/diaries/abc) → 숫자. 잘못되면 그런 일지는 없는 것이므로 404 */
    private static long parsePathId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw ApiException.notFound("일지");
        }
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim());                          // YYYY-MM-DD
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("날짜는 YYYY-MM-DD 형식이어야 해요.");
        }
    }

    private static LocalTime parseTime(String s) {
        if (blank(s)) {
            return null;                                               // 시간은 비워 둘 수 있음
        }
        try {
            return LocalTime.parse(s.trim(), HH_MM);                   // HH:mm
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("시간은 HH:mm 형식이어야 해요.");
        }
    }

    /** DB 시각 → 한국 시간 ISO 문자열 (2026-10-16T22:40:00.123+09:00) */
    private static String iso(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toOffsetDateTime().toString();
    }
}
