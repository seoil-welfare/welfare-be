package kr.seoil.welfare.stats;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatsPeriodFullWeekTest {
    @ParameterizedTest @CsvSource({
        "2025,2,2025-01-26,2025-02-01", "2026,11,2026-11-01,2026-11-07",
        "2026,1,2025-12-28,2026-01-03", "2024,2,2024-01-28,2024-02-03",
        "2024,3,2024-02-25,2024-03-02", "2025,3,2025-02-23,2025-03-01",
        "2026,8,2026-07-26,2026-08-01", "2026,5,2026-04-26,2026-05-02",
        "2027,1,2026-12-27,2027-01-02", "2028,2,2028-01-30,2028-02-05"
    })
    void 월경계_윤년_연경계_7일_비교(int year, int month, String start, String end) {
        List<StatsPeriod.Range> weeks = StatsPeriod.weeksInMonth(year, month);
        assertThat(weeks.getFirst().start()).isEqualTo(LocalDate.parse(start));
        assertThat(weeks.getFirst().end()).isEqualTo(LocalDate.parse(end));
        for (int w = 1; w <= weeks.size(); w++) {
            StatsPeriod period = new StatsPeriod("week", year, month, 1, w);
            assertThat(period.range().end()).isEqualTo(period.range().start().plusDays(6));
            assertThat(period.previous().range().start()).isEqualTo(period.range().start().minusDays(7));
            List<StatsPeriod.Range> trend = period.trend().stream().map(StatsPeriod::range).toList();
            assertThat(trend.stream().map(StatsPeriod.Range::start).distinct()).hasSize(6);
            for (int i = 1; i < trend.size(); i++) assertThat(trend.get(i).start()).isEqualTo(trend.get(i - 1).end().plusDays(1));
        }
    }
}
