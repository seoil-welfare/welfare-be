package kr.seoil.welfare.notification.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 알림 SQL 모음.
 * 알림 내용은 따로 저장하지 않고 일지 이력(log_history)을 그대로 쓴다.
 * 직원마다 '어디까지 읽었는지'(staff.notifications_read_at)와 알림 설정(staff.notify_new_log)만 저장한다.
 */
@Repository
public class NotificationRepository {

    /** 알림 계산에 필요한 직원 한 명의 값 */
    public record ReadState(OffsetDateTime readAt, OffsetDateTime createdAt, boolean notifyNewLog) {
    }

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 직원 번호로 읽음 상태·알림 설정 찾기 (없는 직원이면 빈 Optional) */
    public Optional<ReadState> findReadState(long staffId) {
        return jdbc.query(
                "SELECT notifications_read_at, created_at, notify_new_log FROM staff WHERE staff_id = ?",
                (rs, n) -> new ReadState(
                        rs.getObject("notifications_read_at", OffsetDateTime.class),   // 한 번도 안 읽었으면 null
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getBoolean("notify_new_log")),
                staffId).stream().findFirst();
    }

    /** since 이후에 다른 직원이 남긴 일지 등록·수정 이력 수 (내가 한 것은 알림이 아니므로 뺀다) */
    public int countOthersHistorySince(long staffId, OffsetDateTime since) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM log_history WHERE occurred_at > ? AND actor_id <> ?",
                Integer.class, since, staffId);
        return count == null ? 0 : count;
    }

    /** 지금까지의 알림을 모두 읽음으로 (바뀐 줄 수를 돌려준다. 0이면 없는 직원) */
    public int markAllRead(long staffId) {
        return jdbc.update("UPDATE staff SET notifications_read_at = now() WHERE staff_id = ?", staffId);
    }

    /** 알림 설정 저장 (바뀐 줄 수를 돌려준다. 0이면 없는 직원) */
    public int updateNotifyNewLog(long staffId, boolean notifyNewLog) {
        return jdbc.update("UPDATE staff SET notify_new_log = ? WHERE staff_id = ?", notifyNewLog, staffId);
    }
}
