package kr.seoil.welfare.stats;

import java.util.List;
import java.util.Map;

/**
 * GET /api/stats/summary 응답 (명세서 5.2). 통계 화면 4개(메인·도전적 행동·참여 활동·기타 항목 상세)에 필요한 숫자 전부.
 * 한 번 받아 두면 상세 화면으로 옮겨도 다시 부르지 않는다.
 */
public record StatsSummaryResponse(
        PeriodInfo period,                      // 조회한 기간
        int completedDiaries,                   // 기간 안의 작성 완료 일지 수
        Behavior behavior,                      // 도전적 행동 요약·유형별·추이·세부·발생 기록
        List<CountItem> participation,          // 참여 활동별 건수 (많은 순, 0건 포함)
        List<CountItem> reaction,               // 이용자 반응별
        List<CountItem> preSupport,             // 사전 지원별
        List<CountItem> positiveSupport,        // 긍정행동지원별
        List<CountItem> condition,              // 컨디션별 (일지 기준)
        Activities activities                   // 참여 활동 상세 (FR-53, 55, 56)
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
            Map<String, TypeSummary> typeSummary,   // 유형별 건수·발생 일수·증감 (유형 필터로 요약을 바꿀 때, FR-47)
            List<TrendPoint> trend,             // 추이 그래프 (FR-35)
            List<DetailCount> details,          // 세부 행동별 건수 (FR-48, 1건 이상만)
            List<BehaviorRecord> records) {     // 발생 기록 최신순 (FR-49)
    }

    /** 유형 하나의 요약 */
    public record TypeSummary(int count, int days, int delta) {
    }

    /** 추이 그래프 막대 1개 */
    public record TrendPoint(String label, int count, boolean isCurrent) {
    }

    /** 세부 행동 1개의 건수. type=영역(otherHarm 등), detailId=세부 항목 코드(oh-1 등, 상위 '기타'는 other) */
    public record DetailCount(String type, String detailId, int count) {
    }

    /** 발생 기록 1줄 = 도전적 행동이 있었던 활동 1개 */
    public record BehaviorRecord(
            String diaryId,
            String date,
            String startTime,                   // HH:mm
            String endTime,
            String activityId,
            List<RecordBehavior> behaviors) {   // 그 활동에서 고른 세부 행동 (유형 순서 → 목록 순서)
    }

    /** 발생 기록 안의 세부 행동 1개. otherText는 상위 '기타'의 직접 입력 */
    public record RecordBehavior(String type, String detailId, String otherText) {
    }

    /** 참여 활동 상세 */
    public record Activities(
            int totalCount,                     // 전체 참여 활동 기록 건수 (FR-53)
            int kindCount,                      // 기록된 활동 종류 수
            int otherCount,                     // '기타' 직접 입력 건수
            List<CountItem> behaviorByParticipation,    // 활동별 도전적 행동 건수 (FR-55, 0건 포함)
            List<ParticipationReaction> reactionByParticipation) {   // 활동별 이용자 반응 (FR-56)
    }

    /** 참여 활동 1개의 이용자 반응 건수 (반응 목록 전체, 0건 포함) */
    public record ParticipationReaction(String code, String label, List<CountItem> reaction) {
    }

    /** 항목별 건수 1개: code(저장 기준), label(화면 글자), count */
    public record CountItem(String code, String label, int count) {
    }
}
