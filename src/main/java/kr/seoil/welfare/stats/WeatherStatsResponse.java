package kr.seoil.welfare.stats;

import java.util.List;

/**
 * GET /api/stats/weather 응답 — 날씨별·온도 구간별 도전적 행동.
 * 날씨와 온도 구간은 0건이어도 모두 보낸다 (목록 순서 그대로). 건수 규칙은 통계 요약과 같다.
 */
public record WeatherStatsResponse(
        String start,                           // 조회 시작일 YYYY-MM-DD
        String end,                             // 조회 종료일 (포함)
        int completedDiaries,                   // 기간 안의 작성 완료 일지 수
        List<WeatherCount> weather,             // 날씨별
        List<TemperatureCount> temperature,     // 온도 구간별 (온도가 있는 일지만)
        Excluded temperatureExcluded            // 온도가 없어 온도별 집계에서 뺀 것
) {

    /** 날씨 1개. days = 그 날씨였던 날 수 (작성 완료 일지 기준) */
    public record WeatherCount(String code, String label, int behaviorCount, int days) {
    }

    /** 온도 구간 1개. range = "10℃ 이하" 같은 설명 */
    public record TemperatureCount(String code, String label, String range, int behaviorCount, int days) {
    }

    /** 온도를 고르지 않은 일지 (온도 칸이 생기기 전 일지 포함) */
    public record Excluded(int days, int behaviorCount) {
    }
}
