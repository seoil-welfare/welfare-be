package kr.seoil.welfare.stats;

import java.util.HashMap;
import java.util.Map;

import kr.seoil.welfare.stats.StatsPeriod.Range;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 통계 집계 SQL — PostgreSQL이 GROUP BY로 바로 세어서 숫자만 돌려준다.
 *
 * 공통 규칙 (개발계획서 「통계 집계 공통 규칙」, 프론트 statsService.ts와 같음)
 *  - 작성 완료(completed) 일지만
 *  - 도전적 행동 = 세부 항목 선택 건수. 상위 '기타'는 직접 입력이 있을 때만 1건
 *  - 참여 활동·반응·사전 지원·긍정행동지원 = 활동 기준 / 컨디션 = 일지 기준
 */
@Repository
public class StatsRepository {

    /** 기간 안의 작성 완료 일지 조건 (l = daily_log) */
    private static final String LOG_FILTER = " l.client_id = :clientId AND l.status = 'completed'"
            + " AND l.log_date BETWEEN :start AND :end";

    /** 도전적 행동 1건씩 나오는 FROM ~ WHERE (rb = record_behavior 한 줄 = 1건) */
    private static final String BEHAVIOR_FROM = " FROM record_behavior rb"
            + " JOIN behavior_item bi ON bi.item_id = rb.item_id"
            + " JOIN behavior_area ba ON ba.area_id = bi.area_id"
            + " JOIN activity_record ar ON ar.record_id = rb.record_id"
            + " JOIN daily_log l ON l.log_id = ar.log_id"
            + " WHERE" + LOG_FILTER
            + " AND ar.has_behavior"
            + " AND (ba.code <> 'other' OR btrim(coalesce(rb.etc_text, '')) <> '')";   // 상위 '기타'는 내용 있을 때만

    private final NamedParameterJdbcTemplate jdbc;

    public StatsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static MapSqlParameterSource params(long clientId, Range range) {
        return new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("start", range.start())
                .addValue("end", range.end());
    }

    /** 도전적 행동 건수 */
    public int behaviorCount(long clientId, Range range) {
        Integer n = jdbc.queryForObject("SELECT count(*)" + BEHAVIOR_FROM, params(clientId, range), Integer.class);
        return n == null ? 0 : n;
    }

    /** 도전적 행동 발생 일수 (같은 날 여러 건이어도 1일) */
    public int behaviorDays(long clientId, Range range) {
        Integer n = jdbc.queryForObject("SELECT count(DISTINCT l.log_date)" + BEHAVIOR_FROM,
                params(clientId, range), Integer.class);
        return n == null ? 0 : n;
    }

    /** 영역별 도전적 행동 건수 { otherHarm: 3, selfHarm: 1, ... } (없는 영역은 빠짐) */
    public Map<String, Integer> behaviorByType(long clientId, Range range) {
        Map<String, Integer> result = new HashMap<>();
        jdbc.query("SELECT ba.code, count(*) AS cnt" + BEHAVIOR_FROM + " GROUP BY ba.code",
                params(clientId, range),
                rs -> { result.put(rs.getString("code"), rs.getInt("cnt")); });   // 한 줄씩 Map에 담기
        return result;
    }

    /**
     * 활동 기준 선택 항목별 건수 { 항목 번호: 건수 }
     * junction = record_activity_type 등, idColumn = activity_type_id 등
     */
    public Map<Short, Integer> activityPickCounts(String junction, String idColumn, long clientId, Range range) {
        Map<Short, Integer> result = new HashMap<>();
        jdbc.query("SELECT x." + idColumn + " AS item_id, count(*) AS cnt FROM " + junction + " x"
                        + " JOIN activity_record ar ON ar.record_id = x.record_id"
                        + " JOIN daily_log l ON l.log_id = ar.log_id"
                        + " WHERE" + LOG_FILTER
                        + " GROUP BY x." + idColumn,
                params(clientId, range),
                rs -> { result.put(rs.getShort("item_id"), rs.getInt("cnt")); });
        return result;
    }

    /** 일지 기준 컨디션별 건수 { 컨디션 번호: 건수 } */
    public Map<Short, Integer> conditionCounts(long clientId, Range range) {
        Map<Short, Integer> result = new HashMap<>();
        jdbc.query("SELECT l.condition_id AS item_id, count(*) AS cnt FROM daily_log l"
                        + " WHERE" + LOG_FILTER + " GROUP BY l.condition_id",
                params(clientId, range),
                rs -> { result.put(rs.getShort("item_id"), rs.getInt("cnt")); });
        return result;
    }
}
