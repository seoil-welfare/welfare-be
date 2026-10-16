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
import kr.seoil.welfare.stats.StatsSummaryResponse.Behavior;
import kr.seoil.welfare.stats.StatsSummaryResponse.CountItem;
import kr.seoil.welfare.stats.StatsSummaryResponse.PeriodInfo;
import kr.seoil.welfare.stats.StatsSummaryResponse.TrendPoint;
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

        // 유형별 — 0건인 유형도 0으로 채워서 4개 모두 보낸다
        Map<String, Integer> found = repo.behaviorByType(clientId, range);
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (String type : BEHAVIOR_TYPES) {
            byType.put(type, found.getOrDefault(type, 0));
        }

        // 추이 (FR-35): 기간마다 한 번씩 센다 (최대 6번)
        List<TrendPoint> trend = new ArrayList<>();
        for (StatsPeriod p : period.trend()) {
            Range r = p.range();
            trend.add(new TrendPoint(p.shortLabel(), repo.behaviorCount(clientId, r),
                    r.label().equals(range.label())));                    // 지금 보는 기간인지
        }

        Behavior behavior = new Behavior(count, days, count - prevCount, prevCount == 0, byType, trend);
        PeriodInfo info = new PeriodInfo(period.unit(), range.label(), range.start().toString(), range.end().toString());

        return new StatsSummaryResponse(info, behavior,
                activityCounts(Kind.PARTICIPATION, clientId, range),
                activityCounts(Kind.REACTION, clientId, range),
                activityCounts(Kind.PRE_SUPPORT, clientId, range),
                activityCounts(Kind.POSITIVE_SUPPORT, clientId, range),
                sorted(Kind.CONDITION, repo.conditionCounts(clientId, range)));
    }

    /** 활동 기준 항목별 건수 */
    private List<CountItem> activityCounts(Kind kind, long clientId, Range range) {
        return sorted(kind, repo.activityPickCounts(kind.junction(), kind.idColumn(), clientId, range));
    }

    /** { 항목 번호: 건수 } → 건수 많은 순, 같으면 목록 순서 (0건 항목은 빠짐 — 프론트 통계 메인과 같음) */
    private List<CountItem> sorted(Kind kind, Map<Short, Integer> counts) {
        List<Map.Entry<Item, Integer>> rows = new ArrayList<>();
        for (Map.Entry<Short, Integer> e : counts.entrySet()) {
            rows.add(Map.entry(catalog.byId(kind, e.getKey()), e.getValue()));
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
