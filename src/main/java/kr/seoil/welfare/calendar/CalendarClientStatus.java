package kr.seoil.welfare.calendar;

/** GET /api/calendar/days/{date} 응답의 이용자 1명. 일지가 없으면 diaryId, authorId는 null */
public record CalendarClientStatus(
        String clientId,
        String clientName,
        String status,                  // completed | draft | none
        String diaryId,
        String authorId,                // 처음 작성한 직원
        int behaviorCount,              // 그날 일지의 도전적 행동 건수
        boolean canCreate) {            // 그날 일지가 없으면 true
}
