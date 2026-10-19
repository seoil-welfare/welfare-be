package kr.seoil.welfare.calendar;

/** GET /api/calendar 응답의 날짜 1개 */
public record CalendarDayCount(
        String date,                    // YYYY-MM-DD
        int completed,                  // 작성 완료 수
        int draft,                      // 작성 중 수
        int target,                     // 대상 이용자 수
        boolean hasBehavior) {          // 도전적 행동이 있었는지
}
