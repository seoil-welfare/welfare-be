package kr.seoil.welfare.notification.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.notification.dto.NotificationSettingsDto;
import kr.seoil.welfare.notification.dto.NotificationSummaryResponse;
import kr.seoil.welfare.notification.repository.NotificationRepository;
import kr.seoil.welfare.notification.repository.NotificationRepository.ReadState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 안 알림 (헤더 종 아이콘).
 *
 * 규칙
 *  1. 알림 = 마지막으로 읽은 뒤에 다른 직원이 일지를 등록·수정한 소식 (log_history). 내가 한 것은 세지 않는다
 *  2. 한 번도 읽지 않았으면 계정을 만든 뒤부터 센다 (가입 전 소식이 한꺼번에 쌓여 보이지 않게)
 *  3. 읽음 처리는 '전부 읽음' 하나뿐이다. 소식 화면을 열면 프론트가 부른다
 *  4. 알림 설정(notifyNewLog)을 꺼도 수는 그대로 세어 준다. 표시할지는 프론트가 설정을 보고 정한다
 */
@Service
public class NotificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    /** GET /api/notifications/summary */
    @Transactional(readOnly = true)
    public NotificationSummaryResponse summary(long staffId) {
        ReadState state = readState(staffId);
        OffsetDateTime since = state.readAt() != null ? state.readAt() : state.createdAt();   // 규칙 2
        int unread = repo.countOthersHistorySince(staffId, since);
        return new NotificationSummaryResponse(unread, iso(state.readAt()));
    }

    /** POST /api/notifications/read */
    @Transactional
    public void markAllRead(long staffId) {
        if (repo.markAllRead(staffId) == 0) {
            throw ApiException.unauthenticated();                   // 세션은 있는데 직원이 없음 → 다시 로그인
        }
    }

    /** GET /api/notifications/settings */
    @Transactional(readOnly = true)
    public NotificationSettingsDto settings(long staffId) {
        return new NotificationSettingsDto(readState(staffId).notifyNewLog());
    }

    /** PUT /api/notifications/settings */
    @Transactional
    public NotificationSettingsDto updateSettings(long staffId, NotificationSettingsDto body) {
        if (body == null || body.notifyNewLog() == null) {
            throw ApiException.badRequest("알림 받기 여부(notifyNewLog)를 보내 주세요.");
        }
        if (repo.updateNotifyNewLog(staffId, body.notifyNewLog()) == 0) {
            throw ApiException.unauthenticated();
        }
        return new NotificationSettingsDto(body.notifyNewLog());
    }

    private ReadState readState(long staffId) {
        return repo.findReadState(staffId).orElseThrow(ApiException::unauthenticated);
    }

    /** 응답 시각은 다른 API와 같이 한국 시간 표기 (2026-10-30T14:12:00+09:00) */
    private static String iso(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toOffsetDateTime().toString();
    }
}
