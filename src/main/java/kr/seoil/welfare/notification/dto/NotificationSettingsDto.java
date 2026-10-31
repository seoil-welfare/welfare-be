package kr.seoil.welfare.notification.dto;

/**
 * 알림 설정 — GET 응답과 PUT 요청이 같은 모양 (마이페이지 '새 일지 알림 받기' 스위치)
 */
public record NotificationSettingsDto(
        Boolean notifyNewLog        // 새 일지 알림 받기. PUT에서 비어 있으면 400
) {
}
