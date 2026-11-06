package kr.seoil.welfare.stats;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import kr.seoil.welfare.stats.StatsPeriod.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 주차 계산 검사 (StatsPeriod.weeksInMonth, DB 없음).
 * 하루짜리 주는 옆 주에 붙는다. 프론트 statsService.ts의 weeksInMonth와 결과가 같아야 한다.
 */
class StatsPeriodWeekTest {

    @Test
    @DisplayName("1일이 토요일이면 1일은 다음 주에 붙는다 (2025년 2월)")
    void firstDaySaturday() {
        List<Range> weeks = StatsPeriod.weeksInMonth(2025, 2);
        assertEquals(4, weeks.size());
        assertEquals(LocalDate.of(2025, 2, 1), weeks.get(0).start());
        assertEquals(LocalDate.of(2025, 2, 8), weeks.get(0).end());
        assertEquals("2025년 2월 1주차 (1~8일)", weeks.get(0).label());
    }

    @Test
    @DisplayName("1일이 토요일이고 말일이 일요일이면 앞뒤 모두 붙는다 (2025년 11월)")
    void bothEndsOneDay() {
        List<Range> weeks = StatsPeriod.weeksInMonth(2025, 11);
        assertEquals(4, weeks.size());
        assertEquals("2025년 11월 1주차 (1~8일)", weeks.get(0).label());
        assertEquals("2025년 11월 4주차 (23~30일)", weeks.get(3).label());
    }

    @Test
    @DisplayName("이틀 이상인 주는 그대로 둔다 (2026년 8월 5주차 30~31일)")
    void twoDayWeekKept() {
        List<Range> weeks = StatsPeriod.weeksInMonth(2026, 8);
        assertEquals(5, weeks.size());
        assertEquals("2026년 8월 5주차 (30~31일)", weeks.get(4).label());
    }

    @Test
    @DisplayName("2025~2026년 모든 달: 빈틈·겹침 없고 하루짜리 주 없음")
    void allMonthsCovered() {
        for (int year = 2025; year <= 2026; year++) {
            for (int month = 1; month <= 12; month++) {
                List<Range> weeks = StatsPeriod.weeksInMonth(year, month);
                LocalDate expected = LocalDate.of(year, month, 1);
                for (Range week : weeks) {
                    assertEquals(expected, week.start(), year + "-" + month);
                    assertNotEquals(week.start(), week.end(), year + "-" + month);
                    expected = week.end().plusDays(1);
                }
                assertEquals(YearMonth.of(year, month).atEndOfMonth().plusDays(1), expected);
            }
        }
    }

    @Test
    @DisplayName("전달 마지막 주차로 넘어갈 때 합쳐진 주차 수를 쓴다 (2025년 12월 1주차 → 11월 4주차)")
    void previousWeekAcrossMonth() {
        StatsPeriod prev = new StatsPeriod("week", 2025, 12, 1, 1).previous();
        assertEquals(11, prev.month());
        assertEquals(4, prev.week());
    }
}
