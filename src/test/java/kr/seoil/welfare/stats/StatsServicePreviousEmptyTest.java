package kr.seoil.welfare.stats;

import kr.seoil.welfare.diary.Catalog;
import kr.seoil.welfare.stats.StatsPeriod.Range;
import kr.seoil.welfare.stats.StatsSummaryResponse.Behavior;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * '이전 기간 기록 없음'(previousEmpty) 판단 검사 (StatsService.summary, DB 없음, FR-34 / ST-06).
 * StatsRepository와 Catalog는 Mockito 가짜로 대신한다. 따로 정하지 않은 조회는 0건·빈 목록이다.
 */
class StatsServicePreviousEmptyTest {

    private static final long CLIENT = 1L;                                  // 가상 이용자
    private static final StatsPeriod NOVEMBER = new StatsPeriod("month", 2026, 11, 1, 1);
    private static final Range NOV = NOVEMBER.range();
    private static final Range OCT = NOVEMBER.previous().range();           // 이전 기간 = 2026년 10월

    private StatsRepository repo;
    private StatsService service;

    @BeforeEach
    void setUp() {
        repo = mock(StatsRepository.class);
        service = new StatsService(repo, mock(Catalog.class));
        when(repo.behaviorCount(CLIENT, NOV)).thenReturn(4);                // 이번 달 도전적 행동 4건
    }

    private Behavior november() {
        return service.summary(CLIENT, NOVEMBER).behavior();
    }

    @Test
    @DisplayName("이전 기간에 완료 일지는 있고 행동이 0건이면 '기록 없음'이 아니고 증감은 +4")
    void previousDiariesWithoutBehavior() {
        when(repo.completedCount(CLIENT, OCT)).thenReturn(1);               // 10월 완료 일지 1건
        when(repo.behaviorCount(CLIENT, OCT)).thenReturn(0);                // 그 일지에 행동은 없음

        Behavior b = november();
        assertFalse(b.previousEmpty());
        assertEquals(4, b.delta());
    }

    @Test
    @DisplayName("이전 기간에 완료 일지가 없으면 '기록 없음'")
    void noPreviousDiaries() {
        when(repo.completedCount(CLIENT, OCT)).thenReturn(0);

        assertTrue(november().previousEmpty());
    }

    @Test
    @DisplayName("이전 기간에 행동이 있으면 '기록 없음'이 아니고 증감은 차이")
    void previousWithBehavior() {
        when(repo.completedCount(CLIENT, OCT)).thenReturn(3);
        when(repo.behaviorCount(CLIENT, OCT)).thenReturn(6);

        Behavior b = november();
        assertFalse(b.previousEmpty());
        assertEquals(-2, b.delta());
    }
}
