package kr.seoil.welfare.calendar;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 달력에 필요한 숫자를 DB(PostgreSQL)에서 꺼내는 곳.
 * CalendarService가 부르고, 결과는 아래 DayRow, ClientRow 모양으로 돌려준다.
 * daily_log(일지), client(이용자), 도전적 행동 관련 테이블만 읽고 아무것도 저장하지 않는다.
 *
 * 작성자 이름은 staff를 JOIN하지 않고 authorId만 넘긴다 (직원 계정 재사용 여부 미정).
 */
@Repository                             // DB를 다루는 클래스라는 표시. Spring이 만들어서 Service에 넣어 줌
public class CalendarRepository {

    /** countByDate 결과 한 줄: 날짜, 완료 수, 작성 중 수, 도전적 행동이 있었는지 */
    public record DayRow(LocalDate date, int completed, int draft, boolean hasBehavior) {
    }

    /** findClientStatuses 결과 한 줄. 그날 일지가 없으면 logId, status, authorId는 null, behaviorCount는 0 */
    public record ClientRow(long clientId, String clientName, Long logId, String status, Long authorId,
                            int behaviorCount) {
    }

    /**
     * 일지 l의 도전적 행동이 1건씩 나오는 FROM ~ WHERE 부분. 아래 두 조각이 같이 쓴다.
     * 건수 규칙은 통계(StatsRepository)와 같다. 상위 '기타'는 내용이 있을 때만 1건이고, 작성 중 일지도 포함한다.
     */
    private static final String BEHAVIOR_OF_LOG = " FROM record_behavior rb"     // 활동 1건에서 고른 행동 항목들
            + " JOIN activity_record ar ON ar.record_id = rb.record_id"           // 그 항목이 속한 활동
            + " JOIN behavior_item bi ON bi.item_id = rb.item_id"                 // 항목 정보
            + " JOIN behavior_area ba ON ba.area_id = bi.area_id"                 // 항목의 영역 (타해, 자해, 파괴, 기타)
            + " WHERE ar.log_id = l.log_id AND ar.has_behavior"                   // 바깥 쿼리의 일지 l에 속하고 '도전적 행동 있음'인 활동만
            + " AND (ba.code <> 'other' OR btrim(coalesce(rb.etc_text, '')) <> '')";  // 상위 '기타'는 내용이 있을 때만

    private static final String HAS_BEHAVIOR = "EXISTS (SELECT 1" + BEHAVIOR_OF_LOG + ")";      // 1건이라도 있는지 (달력 점)
    private static final String BEHAVIOR_COUNT = "(SELECT count(*)" + BEHAVIOR_OF_LOG + ")";    // 몇 건인지 (날짜 목록)

    // SQL을 실행하는 Spring 도구. :from 같은 자리에 값을 안전하게 넣어 준다 (SQL 인젝션 방지)
    private final NamedParameterJdbcTemplate jdbc;

    public CalendarRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 기간 안 날짜별 완료·작성 중 수와 도전적 행동 여부. CalendarService.dayCounts가 부른다.
     * GROUP BY로 날짜마다 한 줄씩 나오므로 일지가 있는 날만 결과에 들어간다.
     */
    public List<DayRow> countByDate(LocalDate from, LocalDate to) {
        String sql = "SELECT l.log_date,"
                + " count(*) FILTER (WHERE l.status = 'completed') AS completed,"    // 완료 일지 수
                + " count(*) FILTER (WHERE l.status = 'draft') AS draft,"            // 작성 중 일지 수
                + " bool_or(" + HAS_BEHAVIOR + ") AS has_behavior"                   // 그날 일지 중 하나라도 행동이 있으면 true
                + " FROM daily_log l"
                + " JOIN client c ON c.client_id = l.client_id"                      // 이용자 상태를 보려고 붙임
                + " WHERE l.log_date BETWEEN :from AND :to AND c.status = 'active'"  // 기간 안, 종결된 이용자 제외
                + " GROUP BY l.log_date"
                + " ORDER BY l.log_date";
        return jdbc.query(sql, new MapSqlParameterSource().addValue("from", from).addValue("to", to),
                (rs, i) -> new DayRow(rs.getObject("log_date", LocalDate.class), rs.getInt("completed"),
                        rs.getInt("draft"), rs.getBoolean("has_behavior")));       // DB 결과 한 줄 → DayRow
    }

    /** 현재 이용 중인 이용자 수 (달력 "2/3"의 3). CalendarService.dayCounts가 부른다 */
    public int activeClientCount() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM client WHERE status = 'active'",
                new MapSqlParameterSource(), Integer.class);
        return n == null ? 0 : n;
    }

    /**
     * 이용 중인 이용자 전원과 그 사람의 그날 일지. CalendarService.clientStatuses가 부른다.
     * 일지가 없는 이용자도 '미작성'으로 나와야 해서 LEFT JOIN을 쓴다.
     * 날짜 조건을 WHERE가 아니라 ON에 넣어야 일지가 없는 이용자가 결과에서 빠지지 않는다.
     */
    public List<ClientRow> findClientStatuses(LocalDate date) {
        String sql = "SELECT c.client_id, c.name, l.log_id, l.status, l.created_by,"
                + " " + BEHAVIOR_COUNT + " AS behavior_count"                        // 일지가 없으면 0
                + " FROM client c"
                + " LEFT JOIN daily_log l ON l.client_id = c.client_id AND l.log_date = :date"
                + " WHERE c.status = 'active'"
                + " ORDER BY c.client_id";                                           // 프론트 이용자 목록과 같은 순서
        return jdbc.query(sql, new MapSqlParameterSource("date", date),
                (rs, i) -> new ClientRow(rs.getLong("client_id"), rs.getString("name"),
                        rs.getObject("log_id", Long.class), rs.getString("status"),     // 일지가 없으면 null
                        rs.getObject("created_by", Long.class), rs.getInt("behavior_count")));
    }
}
