package kr.seoil.welfare.calendar;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 달력 조회 SQL. daily_log, client만 읽는다.
 * 작성자 이름은 staff를 JOIN하지 않고 authorId만 넘긴다 (직원 계정 재사용 여부 미정).
 */
@Repository
public class CalendarRepository {

    public record DayRow(LocalDate date, int completed, int draft, boolean hasBehavior) {
    }

    /** 그날 일지가 없으면 logId, status, authorId는 null */
    public record ClientRow(long clientId, String clientName, Long logId, String status, Long authorId) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public CalendarRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /*
     * SELECT l.log_date,
     *        count(*) FILTER (WHERE l.status = 'completed') AS completed,
     *        count(*) FILTER (WHERE l.status = 'draft')     AS draft,
     *        bool_or(EXISTS (SELECT 1 FROM activity_record ar
     *                        WHERE ar.log_id = l.log_id AND ar.has_behavior)) AS has_behavior
     * FROM daily_log l JOIN client c ON c.client_id = l.client_id
     * WHERE l.log_date BETWEEN :from AND :to AND c.status = 'active'
     * GROUP BY l.log_date ORDER BY l.log_date
     *
     * 프론트는 작성 중 일지의 도전적 행동도 표시하므로 status로 거르지 않음
     * TODO has_behavior만 볼지, 프론트처럼 세부 항목 건수로 볼지 확인
     */
    public List<DayRow> countByDate(LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("TODO: 날짜별 일지 수 SQL");
    }

    // SELECT count(*) FROM client WHERE status = 'active'
    public int activeClientCount() {
        throw new UnsupportedOperationException("TODO: 이용 중 이용자 수 SQL");
    }

    /*
     * 일지가 없는 이용자도 나와야 해서 LEFT JOIN
     *
     * SELECT c.client_id, c.name, l.log_id, l.status, l.created_by
     * FROM client c
     * LEFT JOIN daily_log l ON l.client_id = c.client_id AND l.log_date = :date
     * WHERE c.status = 'active'
     * ORDER BY c.client_id
     */
    public List<ClientRow> findClientStatuses(LocalDate date) {
        throw new UnsupportedOperationException("TODO: 이용자별 일지 상태 SQL");
    }
}
