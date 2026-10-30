package kr.seoil.welfare.stats;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
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

    /** 영역별 도전적 행동 발생 일수 { otherHarm: 2, ... } (없는 영역은 빠짐) */
    public Map<String, Integer> behaviorDaysByType(long clientId, Range range) {
        Map<String, Integer> result = new HashMap<>();
        jdbc.query("SELECT ba.code, count(DISTINCT l.log_date) AS cnt" + BEHAVIOR_FROM + " GROUP BY ba.code",
                params(clientId, range),
                rs -> { result.put(rs.getString("code"), rs.getInt("cnt")); });
        return result;
    }

    /** 세부 행동 1개의 건수 (영역 코드, 세부 항목 코드) */
    public record DetailRow(String type, String detailId, int count) {
    }

    /** 세부 행동별 건수 — 영역 순서 → 목록 순서 (1건 이상만) */
    public List<DetailRow> behaviorDetailCounts(long clientId, Range range) {
        return jdbc.query("SELECT ba.code AS type, bi.code AS detail, count(*) AS cnt" + BEHAVIOR_FROM
                        + " GROUP BY ba.code, bi.code, ba.sort_order, bi.sort_order"
                        + " ORDER BY ba.sort_order, bi.sort_order",
                params(clientId, range),
                (rs, n) -> new DetailRow(rs.getString("type"), rs.getString("detail"), rs.getInt("cnt")));
    }

    /** 발생 기록 1줄 = 도전적 행동 1건 (같은 활동이면 recordId가 같다) */
    public record BehaviorRow(long logId, LocalDate date, long recordId, LocalTime start, LocalTime end,
                              String type, String detailId, String etcText) {
    }

    /** 발생 기록 — 날짜·시작 시각 최신순, 같은 활동 안에서는 영역 순서 → 목록 순서 */
    public List<BehaviorRow> behaviorRows(long clientId, Range range) {
        return jdbc.query("SELECT l.log_id, l.log_date, ar.record_id, ar.start_time, ar.end_time,"
                        + " ba.code AS type, bi.code AS detail, rb.etc_text" + BEHAVIOR_FROM
                        + " ORDER BY l.log_date DESC, ar.start_time DESC NULLS LAST, ar.record_id,"
                        + " ba.sort_order, bi.sort_order",
                params(clientId, range),
                (rs, n) -> new BehaviorRow(rs.getLong("log_id"), rs.getObject("log_date", LocalDate.class),
                        rs.getLong("record_id"), rs.getObject("start_time", LocalTime.class),
                        rs.getObject("end_time", LocalTime.class), rs.getString("type"),
                        rs.getString("detail"), rs.getString("etc_text")));
    }

    /** 작성 완료 일지 수 */
    public int completedCount(long clientId, Range range) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM daily_log l WHERE" + LOG_FILTER,
                params(clientId, range), Integer.class);
        return n == null ? 0 : n;
    }

    /** 참여 활동 '기타'를 고르고 내용을 직접 쓴 건수 (FR-53) */
    public int participationOtherCount(long clientId, Range range) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM record_activity_type x"
                        + " JOIN activity_type t ON t.activity_type_id = x.activity_type_id"
                        + " JOIN activity_record ar ON ar.record_id = x.record_id"
                        + " JOIN daily_log l ON l.log_id = ar.log_id"
                        + " WHERE" + LOG_FILTER
                        + " AND t.is_etc AND btrim(coalesce(x.etc_text, '')) <> ''",
                params(clientId, range), Integer.class);
        return n == null ? 0 : n;
    }

    /**
     * 참여 활동별 도전적 행동 건수 { 활동 번호: 건수 } (FR-55)
     * 한 활동에서 참여 활동을 여러 개 골랐으면 고른 활동마다 같은 건수를 더한다 (프론트와 같음, 배분 방식은 미확정)
     */
    public Map<Short, Integer> behaviorByParticipation(long clientId, Range range) {
        Map<Short, Integer> result = new HashMap<>();
        jdbc.query("SELECT x.activity_type_id AS item_id, sum(b.cnt) AS cnt FROM record_activity_type x"
                        + " JOIN (SELECT ar.record_id, count(*) AS cnt" + BEHAVIOR_FROM + " GROUP BY ar.record_id) b"
                        + " ON b.record_id = x.record_id"
                        + " GROUP BY x.activity_type_id",
                params(clientId, range),
                rs -> { result.put(rs.getShort("item_id"), rs.getInt("cnt")); });
        return result;
    }

    /** 참여 활동별 이용자 반응 건수 { 활동 번호: { 반응 번호: 건수 } } (FR-56) */
    public Map<Short, Map<Short, Integer>> reactionByParticipation(long clientId, Range range) {
        Map<Short, Map<Short, Integer>> result = new HashMap<>();
        jdbc.query("SELECT x.activity_type_id AS activity, r.reaction_id AS reaction, count(*) AS cnt"
                        + " FROM record_activity_type x"
                        + " JOIN record_reaction r ON r.record_id = x.record_id"
                        + " JOIN activity_record ar ON ar.record_id = x.record_id"
                        + " JOIN daily_log l ON l.log_id = ar.log_id"
                        + " WHERE" + LOG_FILTER
                        + " GROUP BY x.activity_type_id, r.reaction_id",
                params(clientId, range),
                rs -> {
                    result.computeIfAbsent(rs.getShort("activity"), k -> new HashMap<>())
                            .put(rs.getShort("reaction"), rs.getInt("cnt"));
                });
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
