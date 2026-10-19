package kr.seoil.welfare.calendar;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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

    /**
     * 일지 l에 도전적 행동이 1건이라도 있는지. 건수 규칙은 StatsRepository와 같다
     * (상위 '기타'는 내용이 있을 때만 1건). 작성 중 일지도 포함한다.
     */
    private static final String HAS_BEHAVIOR = "EXISTS (SELECT 1 FROM record_behavior rb"
            + " JOIN activity_record ar ON ar.record_id = rb.record_id"
            + " JOIN behavior_item bi ON bi.item_id = rb.item_id"
            + " JOIN behavior_area ba ON ba.area_id = bi.area_id"
            + " WHERE ar.log_id = l.log_id AND ar.has_behavior"
            + " AND (ba.code <> 'other' OR btrim(coalesce(rb.etc_text, '')) <> ''))";

    /** 기간 안 날짜별 숫자. 일지가 있는 날만 나온다 */
    public List<DayRow> countByDate(LocalDate from, LocalDate to) {
        String sql = "SELECT l.log_date,"
                + " count(*) FILTER (WHERE l.status = 'completed') AS completed,"
                + " count(*) FILTER (WHERE l.status = 'draft') AS draft,"
                + " bool_or(" + HAS_BEHAVIOR + ") AS has_behavior"
                + " FROM daily_log l"
                + " JOIN client c ON c.client_id = l.client_id"
                + " WHERE l.log_date BETWEEN :from AND :to AND c.status = 'active'"
                + " GROUP BY l.log_date"
                + " ORDER BY l.log_date";
        return jdbc.query(sql, new MapSqlParameterSource().addValue("from", from).addValue("to", to),
                (rs, i) -> new DayRow(rs.getObject("log_date", LocalDate.class), rs.getInt("completed"),
                        rs.getInt("draft"), rs.getBoolean("has_behavior")));
    }

    public int activeClientCount() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM client WHERE status = 'active'",
                new MapSqlParameterSource(), Integer.class);
        return n == null ? 0 : n;
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
