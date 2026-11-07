package kr.seoil.welfare.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.Catalog;
import kr.seoil.welfare.diary.Catalog.Item;
import kr.seoil.welfare.diary.Catalog.Kind;
import kr.seoil.welfare.stats.StatsPeriod.Range;
import kr.seoil.welfare.stats.StatsRepository.BehaviorRow;
import kr.seoil.welfare.stats.StatsSummaryResponse.Activities;
import kr.seoil.welfare.stats.StatsSummaryResponse.Behavior;
import kr.seoil.welfare.stats.StatsSummaryResponse.CountItem;
import kr.seoil.welfare.stats.StatsSummaryResponse.DetailCount;
import kr.seoil.welfare.stats.StatsSummaryResponse.ParticipationReaction;
import kr.seoil.welfare.stats.StatsSummaryResponse.PeriodInfo;
import kr.seoil.welfare.stats.StatsSummaryResponse.BehaviorRecord;
import kr.seoil.welfare.stats.StatsSummaryResponse.RecordBehavior;
import kr.seoil.welfare.stats.StatsSummaryResponse.TrendPoint;
import kr.seoil.welfare.stats.StatsSummaryResponse.TypeSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통계 계산 — 기간을 정하고(StatsPeriod), SQL로 센 숫자(StatsRepository)를 화면 모양으로 묶는다.
 * 결과는 프론트 statsService.ts가 계산하는 값과 같아야 한다.
 */
@Service
public class StatsService {

    private static final Set<String> UNITS = Set.of("week", "month", "quarter", "year");
    /** 유형 순서 (프론트 behaviorCountsByType 결과와 같은 키) */
    private static final List<String> BEHAVIOR_TYPES = List.of("otherHarm", "selfHarm", "destruction", "other");

    private final StatsRepository repo;
    private final Catalog catalog;

    public StatsService(StatsRepository repo, Catalog catalog) {
        this.repo = repo;
        this.catalog = catalog;
    }

    /** 요청 값 검사 → 기간 만들기. 빠진 값은 프론트처럼 1로 본다 */
    public StatsPeriod toPeriod(String unit, Integer year, Integer month, Integer quarter, Integer week) {
        if (unit == null || !UNITS.contains(unit)) {                      // Set.of는 null 검사가 먼저
            throw ApiException.badRequest("unit은 week, month, quarter, year 중 하나여야 해요.");
        }
        if (year == null || year < 2000 || year > 2100) {
            throw ApiException.badRequest("year가 올바르지 않아요.");
        }
        int m = month == null ? 1 : month;
        int q = quarter == null ? 1 : quarter;
        int w = week == null ? 1 : week;
        if (m < 1 || m > 12 || q < 1 || q > 4 || w < 1) {
            throw ApiException.badRequest("month는 1~12, quarter는 1~4, week는 1 이상이어야 해요.");
        }
        return new StatsPeriod(unit, year, m, q, w);
    }

    /** GET /api/stats/summary */
    @Transactional(readOnly = true)
    public StatsSummaryResponse summary(long clientId, StatsPeriod period) {
        Range range = period.range();
        Range prevRange = period.previous().range();

        // 도전적 행동 요약 (FR-32~34)
        int count = repo.behaviorCount(clientId, range);
        int prevCount = repo.behaviorCount(clientId, prevRange);
        int days = repo.behaviorDays(clientId, range);
        // '이전 기간 기록 없음'은 이전 기간에 작성 완료 일지가 하나도 없을 때만이다 (FR-34, ST-06).
        // 일지는 있는데 도전적 행동이 0건이면 비교할 기록이 있는 것이라 증감(+N건)으로 보여 준다
        boolean previousEmpty = repo.completedCount(clientId, prevRange) == 0;

        // 유형별 — 0건인 유형도 0으로 채워서 4개 모두 보낸다
        Map<String, Integer> found = repo.behaviorByType(clientId, range);
        Map<String, Integer> prevFound = repo.behaviorByType(clientId, prevRange);
        Map<String, Integer> daysFound = repo.behaviorDaysByType(clientId, range);
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, TypeSummary> typeSummary = new LinkedHashMap<>();
        for (String type : BEHAVIOR_TYPES) {
            int n = found.getOrDefault(type, 0);
            byType.put(type, n);
            typeSummary.put(type, new TypeSummary(n, daysFound.getOrDefault(type, 0), n - prevFound.getOrDefault(type, 0)));
        }

        // 추이 (FR-35): 기간마다 한 번씩 센다 (최대 6번)
        List<TrendPoint> trend = new ArrayList<>();
        for (StatsPeriod p : period.trend()) {
            Range r = p.range();
            trend.add(new TrendPoint(p.shortLabel(), repo.behaviorCount(clientId, r),
                    r.label().equals(range.label())));                    // 지금 보는 기간인지
        }

        List<DetailCount> details = repo.behaviorDetailCounts(clientId, range).stream()
                .map(r -> new DetailCount(r.type(), r.detailId(), r.count()))
                .toList();
        Behavior behavior = new Behavior(count, days, count - prevCount, previousEmpty, byType, typeSummary,
                trend, details, records(repo.behaviorRows(clientId, range)));
        PeriodInfo info = new PeriodInfo(period.unit(), range.label(), range.start().toString(), range.end().toString());

        Map<Short, Integer> participation = repo.activityPickCounts(
                Kind.PARTICIPATION.junction(), Kind.PARTICIPATION.idColumn(), clientId, range);
        return new StatsSummaryResponse(info, repo.completedCount(clientId, range), behavior,
                sorted(Kind.PARTICIPATION, participation),
                activityCounts(Kind.REACTION, clientId, range),
                activityCounts(Kind.PRE_SUPPORT, clientId, range),
                activityCounts(Kind.POSITIVE_SUPPORT, clientId, range),
                sorted(Kind.CONDITION, repo.conditionCounts(clientId, range)),
                activities(clientId, range, participation));
    }

    /** 참여 활동 상세 (FR-53, 55, 56) */
    private Activities activities(long clientId, Range range, Map<Short, Integer> participation) {
        int total = participation.values().stream().mapToInt(Integer::intValue).sum();
        Map<Short, Map<Short, Integer>> reactions = repo.reactionByParticipation(clientId, range);
        List<ParticipationReaction> reactionByParticipation = new ArrayList<>();
        for (Item item : catalog.items(Kind.PARTICIPATION)) {
            reactionByParticipation.add(new ParticipationReaction(item.code(), catalog.label(Kind.PARTICIPATION, item),
                    sorted(Kind.REACTION, reactions.getOrDefault(item.id(), Map.of()))));
        }
        return new Activities(total, participation.size(), repo.participationOtherCount(clientId, range),
                sorted(Kind.PARTICIPATION, repo.behaviorByParticipation(clientId, range)),
                reactionByParticipation);
    }

    /** 도전적 행동 1건씩 나온 줄을 활동 1개 = 기록 1줄로 묶는다 (순서는 SQL이 정한 최신순 그대로) */
    private static List<BehaviorRecord> records(List<BehaviorRow> rows) {
        Map<Long, BehaviorRecord> byActivity = new LinkedHashMap<>();
        for (BehaviorRow r : rows) {
            BehaviorRecord record = byActivity.computeIfAbsent(r.recordId(), id -> new BehaviorRecord(
                    String.valueOf(r.logId()), r.date().toString(), r.start(), r.end(),
                    String.valueOf(id), new ArrayList<>()));
            boolean topOther = "other".equals(r.type());                  // 상위 '기타'만 직접 입력을 보여 준다
            record.behaviors().add(new RecordBehavior(r.type(), r.detailId(), topOther ? r.etcText().trim() : null));
        }
        return List.copyOf(byActivity.values());
    }

    /** 활동 기준 항목별 건수 */
    private List<CountItem> activityCounts(Kind kind, long clientId, Range range) {
        return sorted(kind, repo.activityPickCounts(kind.junction(), kind.idColumn(), clientId, range));
    }

    /**
     * { 항목 번호: 건수 } → 목록 항목 전체를 건수 많은 순, 같으면 목록 순서로 (0건 항목도 넣는다).
     * 통계 메인은 0건을 빼고, 상세 화면은 0건까지 보여 준다 — 거르는 것은 프론트가 한다.
     */
    private List<CountItem> sorted(Kind kind, Map<Short, Integer> counts) {
        List<Map.Entry<Item, Integer>> rows = new ArrayList<>();
        for (Item item : catalog.items(kind)) {
            rows.add(Map.entry(item, counts.getOrDefault(item.id(), 0)));
        }
        rows.sort(Comparator.<Map.Entry<Item, Integer>>comparingInt(e -> -e.getValue())   // 많은 순
                .thenComparingInt(e -> e.getKey().sortOrder()));                        // 같으면 목록 순서
        List<CountItem> result = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : rows) {
            Item item = e.getKey();
            result.add(new CountItem(item.code(), catalog.label(kind, item), e.getValue()));
        }
        return result;
    }
}
