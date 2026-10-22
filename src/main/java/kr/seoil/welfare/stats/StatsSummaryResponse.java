package kr.seoil.welfare.stats;

import java.util.List;
import java.util.Map;

/**
 * GET /api/stats/summary 응답 (명세서 5.2). 통계 메인 화면(ST) 한 장에 필요한 숫자 전부.
 */
public record StatsSummaryResponse(
        PeriodInfo period,                      // 조회한 기간
        Behavior behavior,                      // 도전적 행동 요약·유형별·추이
        List<CountItem> participation,          // 참여 활동별 건수 (많은 순)
        List<CountItem> reaction,               // 이용자 반응별
        List<CountItem> preSupport,             // 사전 지원별
        List<CountItem> positiveSupport,        // 긍정행동지원별
        List<CountItem> condition               // 컨디션별 (일지 기준)
) {

    /** 기간: unit, 표시 이름(2026년 10월), 시작·끝 날짜 */
    public record PeriodInfo(String unit, String label, String start, String end) {
    }

    /** 도전적 행동 */
    public record Behavior(
            int count,                          // 건수 (FR-32)
            int days,                           // 발생 일수 (FR-33)
            int delta,                          // 이전 기간 대비 증감 (FR-34)
            boolean previousEmpty,              // 이전 기간에 기록이 0건이었는지
            Map<String, Integer> byType,        // 유형별 건수 — 프론트 behaviorCountsByType과 같은 모양
            List<TrendPoint> trend) {           // 추이 그래프 (FR-35)
    }

    /** 추이 그래프 막대 1개 */
    public record TrendPoint(String label, int count, boolean isCurrent) {
    }

    /** 항목별 건수 1개: code(저장 기준), label(화면 글자), count */
    public record CountItem(String code, String label, int count) {
    }
}
