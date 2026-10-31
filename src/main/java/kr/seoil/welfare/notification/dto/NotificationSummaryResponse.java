package kr.seoil.welfare.notification.dto;

/**
 * GET /api/notifications/summary 응답 — 헤더 종 아이콘에 보여 줄 값
 */
public record NotificationSummaryResponse(
        int unreadCount,            // 마지막으로 읽은 뒤 다른 직원이 등록·수정한 일지 소식 수
        String lastReadAt           // 마지막으로 읽은 시각 (2026-10-30T14:12:00+09:00). 한 번도 안 읽었으면 응답에서 빠짐
) {
}
