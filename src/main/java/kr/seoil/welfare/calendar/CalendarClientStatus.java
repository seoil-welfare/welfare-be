package kr.seoil.welfare.calendar;

/**
 * GET /api/calendar/days/{date} 응답에 들어가는 이용자 1명.
 * CalendarService.clientStatuses가 만들고, 프론트 달력에서 날짜를 눌렀을 때 아래 목록 한 줄이 된다.
 * 일지가 없으면 diaryId, authorId는 null이고, JSON에서는 아예 빠진다 (application.yml의 non_null 설정).
 */
public record CalendarClientStatus(
        String clientId,                // 이용자 번호
        String clientName,              // 이용자 이름
        String status,                  // completed(등록) | draft(작성 중) | none(미작성)
        String diaryId,                 // 그날 일지 번호. 목록에서 누르면 프론트가 /diaries/{diaryId} 로 이동
        String authorId,                // 처음 작성한 직원 번호. 이름은 프론트가 직원 목록에서 찾음
        int behaviorCount,              // 그날 일지의 도전적 행동 건수
        boolean canCreate) {            // 새 일지를 쓸 수 있는지 (그날 일지가 없고, 오늘이거나 지난 날이면 true)
}
