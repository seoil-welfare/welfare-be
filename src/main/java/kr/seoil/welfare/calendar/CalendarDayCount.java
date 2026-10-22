package kr.seoil.welfare.calendar;

/**
 * GET /api/calendar 응답에 들어가는 날짜 1개.
 * CalendarService.dayCounts가 만들고, 프론트 달력 칸에 "완료 수 / 대상 수"와 주황 점으로 표시된다.
 * record는 값만 담는 클래스라서, 이대로 JSON { "date": ..., "completed": ... } 이 된다.
 */
public record CalendarDayCount(
        String date,                    // 날짜 YYYY-MM-DD
        int completed,                  // 작성 완료 일지 수 ("2/3"의 2)
        int draft,                      // 작성 중 일지 수
        int target,                     // 대상 이용자 수 ("2/3"의 3)
        boolean hasBehavior) {          // 그날 도전적 행동이 기록된 일지가 있는지 (달력의 주황 점)
}
