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
 *  week   : '해당 월의 N주차' — 1일이 포함된 주가 1주차, 주는 일요일~토요일 (주차 계산은 회의 확인 사항)
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

    /** 해당 월의 주차 목록 (1일부터 토요일까지가 1주차, 마지막 주는 월말까지) */
    static List<Range> weeksInMonth(int year, int month) {
        int lastDay = YearMonth.of(year, month).lengthOfMonth();
        List<Range> ranges = new ArrayList<>();
        int cursor = 1;
        int week = 1;
        while (cursor <= lastDay) {
            LocalDate date = LocalDate.of(year, month, cursor);
            int jsDay = date.getDayOfWeek().getValue() % 7;     // 자바 월=1..일=7 → 자바스크립트 일=0..토=6
            int endDay = Math.min(cursor + (6 - jsDay), lastDay);   // 그 주 토요일 또는 월말
            ranges.add(new Range(date, LocalDate.of(year, month, endDay),
                    year + "년 " + month + "월 " + week + "주차"));
            cursor = endDay + 1;
            week += 1;
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
