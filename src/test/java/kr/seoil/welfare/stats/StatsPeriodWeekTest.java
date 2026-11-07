package kr.seoil.welfare.stats;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

import kr.seoil.welfare.stats.StatsPeriod.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 주차 계산 검사 (StatsPeriod.weeksInMonth, DB 없음).
 * 월 경계에서도 일요일~토요일 7일 전체를 쓴다. FE와 동일한 확정 정책이다.
 */
class StatsPeriodWeekTest {

    @Test
    @DisplayName("1일이 토요일이어도 이전 달 일요일부터 7일 (2025년 2월)")
    void firstDaySaturday() {
        List<Range> weeks = StatsPeriod.weeksInMonth(2025, 2);
        assertEquals(5, weeks.size());
        assertEquals(LocalDate.of(2025, 1, 26), weeks.get(0).start());
        assertEquals(LocalDate.of(2025, 2, 1), weeks.get(0).end());
        assertEquals("2025년 2월 1주차", weeks.get(0).label());
    }

    @Test
    @DisplayName("앞뒤 월과 만나는 주도 각각 7일 (2025년 11월)")
    void bothEndsOneDay() {
        List<Range> weeks = StatsPeriod.weeksInMonth(2025, 11);
        assertEquals(6, weeks.size());
        assertEquals(LocalDate.of(2025, 10, 26), weeks.get(0).start());
        assertEquals(LocalDate.of(2025, 11, 1), weeks.get(0).end());
        assertEquals(LocalDate.of(2025, 11, 30), weeks.get(5).start());
        assertEquals(LocalDate.of(2025, 12, 6), weeks.get(5).end());
    }

    @Test
    @DisplayName("마지막 주도 다음 달 토요일까지 (2026년 8월)")
    void twoDayWeekKept() {
        List<Range> weeks = StatsPeriod.weeksInMonth(2026, 8);
        assertEquals(6, weeks.size());
        assertEquals(LocalDate.of(2026, 8, 30), weeks.get(5).start());
        assertEquals(LocalDate.of(2026, 9, 5), weeks.get(5).end());
    }

    @Test
    @DisplayName("2025~2026년 모든 달: 7일 단위로 빈틈·겹침 없이 해당 월 전체 포함")
    void allMonthsCovered() {
        for (int year = 2025; year <= 2026; year++) {
            for (int month = 1; month <= 12; month++) {
                List<Range> weeks = StatsPeriod.weeksInMonth(year, month);
                LocalDate first = LocalDate.of(year, month, 1);
                LocalDate expected = first.minusDays(first.getDayOfWeek().getValue() % 7);
                assertFalse(weeks.getFirst().start().isAfter(first));
                for (Range week : weeks) {
                    assertEquals(expected, week.start(), year + "-" + month);
                    assertEquals(6, ChronoUnit.DAYS.between(week.start(), week.end()), year + "-" + month);
                    assertEquals(7, week.start().getDayOfWeek().getValue());
                    assertEquals(6, week.end().getDayOfWeek().getValue());
                    expected = week.end().plusDays(1);
                }
                assertTrue(expected.isAfter(YearMonth.of(year, month).atEndOfMonth()));
            }
        }
    }

    @Test
    @DisplayName("월 경계에서 같은 주를 건너뛰고 정확히 7일 전을 비교한다")
    void previousWeekAcrossMonth() {
        StatsPeriod prev = new StatsPeriod("week", 2025, 12, 1, 1).previous();
        assertEquals(11, prev.month());
        assertEquals(5, prev.week());
        assertEquals(LocalDate.of(2025, 11, 23), prev.range().start());
    }
}
