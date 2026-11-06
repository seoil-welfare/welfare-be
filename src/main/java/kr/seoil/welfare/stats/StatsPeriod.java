package kr.seoil.welfare.stats;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 통계 기간 계산 — 프론트 statsService.ts의 periodRange / previousPeriod / trendPeriods를 그대로 옮긴 것.
 * 서버와 프론트가 같은 기간을 쓰도록, 규칙을 바꿀 때는 두 곳을 함께 바꾼다.
 *
 *  unit   : week | month | quarter | year
 *  week   : '해당 월의 N주차' — 1일이 포함된 주가 1주차, 주는 일요일~토요일, 하루짜리 주는 옆 주에 붙임
 */
public record StatsPeriod(String unit, int year, int month, int quarter, int week) {

    /** 기간 범위: start~end (둘 다 포함) + 화면 표시 이름 */
    public record Range(LocalDate start, LocalDate end, String label) {
    }

    /** 추이 그래프 구간 수 (FR-35 제안값): 주 6, 월 6, 분기 4, 연 5 */
    static int trendSegments(String unit) {
        return switch (unit) {
            case "week", "month" -> 6;
            case "quarter" -> 4;
            default -> 5;                                       // year
        };
    }

    /**
     * 해당 월의 주차 목록. 주는 일요일~토요일이고 월 안에서 자른다.
     * 하루짜리 주(1일이 토요일, 말일이 일요일)는 옆 주에 붙인다. 이전 주와 비교할 때 증감이 크게 왜곡되기 때문.
     */
    static List<Range> weeksInMonth(int year, int month) {
        int lastDay = YearMonth.of(year, month).lengthOfMonth();
        List<int[]> spans = new ArrayList<>();                  // {시작일, 끝일}
        for (int from = 1; from <= lastDay; ) {
            int jsDay = LocalDate.of(year, month, from).getDayOfWeek().getValue() % 7;  // 일=0 ~ 토=6 (프론트 getDay와 같게)
            int to = Math.min(from + (6 - jsDay), lastDay);    // 그 주 토요일 또는 말일
            spans.add(new int[] {from, to});
            from = to + 1;
        }
        if (spans.size() > 1 && spans.get(0)[0] == spans.get(0)[1]) {              // 첫 주가 하루면 다음 주에 붙임
            spans.get(1)[0] = 1;
            spans.remove(0);
        }
        int last = spans.size() - 1;
        if (last > 0 && spans.get(last)[0] == spans.get(last)[1]) {                // 마지막 주가 하루면 앞 주에 붙임
            spans.get(last - 1)[1] = lastDay;
            spans.remove(last);
        }

        List<Range> ranges = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            int from = spans.get(i)[0];
            int to = spans.get(i)[1];
            ranges.add(new Range(LocalDate.of(year, month, from), LocalDate.of(year, month, to),
                    year + "년 " + month + "월 " + (i + 1) + "주차"));
        }
        return ranges;
    }

    /** 이 기간의 날짜 범위 */
    public Range range() {
        switch (unit) {
            case "year":
                return new Range(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), year + "년");
            case "quarter": {
                int startMonth = (quarter - 1) * 3 + 1;
                LocalDate start = LocalDate.of(year, startMonth, 1);
                return new Range(start, start.plusMonths(3).minusDays(1), year + "년 " + quarter + "분기");
            }
            case "month": {
                LocalDate start = LocalDate.of(year, month, 1);
                return new Range(start, start.plusMonths(1).minusDays(1), year + "년 " + month + "월");
            }
            default: {                                          // week
                List<Range> weeks = weeksInMonth(year, month);
                int index = Math.min(Math.max(week - 1, 0), weeks.size() - 1);   // 범위를 벗어나면 끝으로 맞춤
                return weeks.get(index);
            }
        }
    }

    /** 바로 이전 같은 단위 기간 (FR-34) */
    public StatsPeriod previous() {
        switch (unit) {
            case "year":
                return new StatsPeriod(unit, year - 1, month, quarter, week);
            case "quarter":
                return quarter == 1 ? new StatsPeriod(unit, year - 1, month, 4, week)
                        : new StatsPeriod(unit, year, month, quarter - 1, week);
            case "month":
                return month == 1 ? new StatsPeriod(unit, year - 1, 12, quarter, week)
                        : new StatsPeriod(unit, year, month - 1, quarter, week);
            default: {                                          // week: 1주차 이전은 전달의 마지막 주차
                if (week > 1) {
                    return new StatsPeriod(unit, year, month, quarter, week - 1);
                }
                int prevMonth = month == 1 ? 12 : month - 1;
                int prevYear = month == 1 ? year - 1 : year;
                return new StatsPeriod(unit, prevYear, prevMonth, quarter, weeksInMonth(prevYear, prevMonth).size());
            }
        }
    }

    /** 추이 그래프에 쓸 기간들 (오래된 것 → 지금 순) */
    public List<StatsPeriod> trend() {
        LinkedList<StatsPeriod> out = new LinkedList<>();
        out.add(this);
        StatsPeriod cursor = this;
        for (int i = 1; i < trendSegments(unit); i++) {
            cursor = cursor.previous();
            out.addFirst(cursor);                               // 앞에 끼워 넣기
        }
        return out;
    }

    /** 추이 그래프 막대 아래 짧은 이름: 10월 / 3분기 / 2026 / 2주차 */
    public String shortLabel() {
        return switch (unit) {
            case "month" -> month + "월";
            case "quarter" -> quarter + "분기";
            case "year" -> String.valueOf(year);
            default -> week + "주차";
        };
    }
}
