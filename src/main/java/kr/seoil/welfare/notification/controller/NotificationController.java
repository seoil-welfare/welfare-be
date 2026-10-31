package kr.seoil.welfare.notification.controller;

import kr.seoil.welfare.auth.SessionStaff;
import kr.seoil.welfare.notification.dto.NotificationSettingsDto;
import kr.seoil.welfare.notification.dto.NotificationSummaryResponse;
import kr.seoil.welfare.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 알림 API — 규칙은 NotificationService에 있다. 모두 로그인한 직원 본인 기준이다.
 *
 *  GET  /api/notifications/summary    안 읽은 알림 수 (헤더 종 아이콘)
 *  POST /api/notifications/read       전부 읽음 (소식 화면을 열 때)
 *  GET  /api/notifications/settings   알림 설정 조회
 *  PUT  /api/notifications/settings   알림 설정 저장 (마이페이지 스위치)
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/summary")
    public NotificationSummaryResponse summary(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId) {
        return notificationService.summary(SessionStaff.require(staffId));     // 로그인 안 했으면 401
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markAllRead(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId) {
        notificationService.markAllRead(SessionStaff.require(staffId));
        return ResponseEntity.noContent().build();                              // 204
    }

    @GetMapping("/settings")
    public NotificationSettingsDto settings(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId) {
        return notificationService.settings(SessionStaff.require(staffId));
    }

    @PutMapping("/settings")
    public NotificationSettingsDto updateSettings(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
            @RequestBody(required = false) NotificationSettingsDto body) {
        return notificationService.updateSettings(SessionStaff.require(staffId), body);
    }
}
