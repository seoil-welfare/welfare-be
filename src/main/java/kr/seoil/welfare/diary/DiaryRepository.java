package kr.seoil.welfare.diary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * 일지 관련 SQL 모음 (daily_log, activity_record, record_*, log_history).
 * :이름 자리는 NamedParameterJdbcTemplate이 값을 안전하게 끼워 넣는다 (SQL 인젝션 방지).
 */
@Repository
public class DiaryRepository {

    // ---- 조회 결과를 담는 그릇 ----

    /** daily_log 한 줄 */
    public record LogRow(long id, long clientId, LocalDate date, String status,
                         short weatherId, short conditionId, String conditionEtc, String remark,
                         long createdBy, Long updatedBy, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    /** activity_record 한 줄 */
    public record RecordRow(long id, long logId, LocalTime start, LocalTime end,
                            String content, boolean hasBehavior) {
    }

    /** 연결 테이블(record_*) 한 줄: 어느 활동에서 어느 항목을 골랐는지 */
    public record PickRow(long recordId, short itemId, String etcText) {
    }

    /** 저장할 선택 항목 1개 */
    public record NewPick(short itemId, String etcText) {
    }

    /** 수정 전에 잠가서 읽는 일지 정보 (createdBy = 처음 쓴 직원, 작성 중 일지 수정 권한 확인용) */
    public record LockedLog(String status, long clientId, LocalDate date, long createdBy) {
    }

    /** log_history + 일지 요약 (오늘의 소식) */
    public record HistoryRow(long id, long logId, String kind, long actorId, OffsetDateTime occurredAt,
                             long clientId, LocalDate date) {
    }

    private static final String LOG_COLUMNS = "log_id, client_id, log_date, status, weather_id, condition_id,"
            + " condition_etc, remark, created_by, updated_by, created_at, updated_at";

    private final NamedParameterJdbcTemplate jdbc;

    public DiaryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================================================================
    // 조회
    // ==================================================================

    /** 조건에 맞는 일지 목록. 조건이 null이면 그 조건은 빼고 찾는다. 날짜 → 이용자 순 */
    public List<LogRow> findLogs(Long clientId, LocalDate from, LocalDate to, String status) {
        StringBuilder sql = new StringBuilder("SELECT " + LOG_COLUMNS + " FROM daily_log WHERE 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (clientId != null) { sql.append(" AND client_id = :clientId"); p.addValue("clientId", clientId); }
        if (from != null)     { sql.append(" AND log_date >= :from");     p.addValue("from", from); }
        if (to != null)       { sql.append(" AND log_date <= :to");       p.addValue("to", to); }
        if (status != null)   { sql.append(" AND status = :status");      p.addValue("status", status); }
        sql.append(" ORDER BY log_date, client_id");
        return jdbc.query(sql.toString(), p, (rs, n) -> mapLog(rs));
    }

    /** 일지 1건 (없으면 null) */
    public LogRow findLog(long logId) {
        List<LogRow> rows = jdbc.query("SELECT " + LOG_COLUMNS + " FROM daily_log WHERE log_id = :id",
                new MapSqlParameterSource("id", logId), (rs, n) -> mapLog(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 여러 일지의 활동을 한 번에 (일지마다 따로 부르면 느려지므로) */
    public List<RecordRow> findRecords(Collection<Long> logIds) {
        return jdbc.query("SELECT record_id, log_id, start_time, end_time, content, has_behavior"
                        + " FROM activity_record WHERE log_id IN (:ids) ORDER BY log_id, record_id",
                new MapSqlParameterSource("ids", logIds),
                (rs, n) -> new RecordRow(rs.getLong("record_id"), rs.getLong("log_id"),
                        rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class),
                        rs.getString("content"), rs.getBoolean("has_behavior")));
    }

    /** 연결 테이블에서 여러 활동의 선택 항목을 한 번에. idColumn = 그 테이블의 항목 번호 칸 */
    public List<PickRow> findPicks(String junction, String idColumn, Collection<Long> recordIds) {
        return jdbc.query("SELECT record_id, " + idColumn + " AS item_id, etc_text FROM " + junction
                        + " WHERE record_id IN (:ids)",
                new MapSqlParameterSource("ids", recordIds),
                (rs, n) -> new PickRow(rs.getLong("record_id"), rs.getShort("item_id"), rs.getString("etc_text")));
    }

    /** 이용자가 있는지 */
    public boolean clientExists(long clientId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM client WHERE client_id = :id",
                new MapSqlParameterSource("id", clientId), Integer.class);
        return count != null && count > 0;
    }

    /** 같은 이용자·같은 날짜 일지 번호 (없으면 null) */
    public Long findLogIdByClientAndDate(long clientId, LocalDate date) {
        List<Long> ids = jdbc.queryForList(
                "SELECT log_id FROM daily_log WHERE client_id = :c AND log_date = :d",
                new MapSqlParameterSource().addValue("c", clientId).addValue("d", date), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 수정 전에 그 일지를 잠그고 읽기 (동시에 두 명이 저장해도 순서대로 처리되게). 없으면 null */
    public LockedLog lockLog(long logId) {
        List<LockedLog> rows = jdbc.query(
                "SELECT status, client_id, log_date, created_by FROM daily_log WHERE log_id = :id FOR UPDATE",
                new MapSqlParameterSource("id", logId),
                (rs, n) -> new LockedLog(rs.getString("status"), rs.getLong("client_id"),
                        rs.getObject("log_date", LocalDate.class), rs.getLong("created_by")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 오늘의 소식: since 날짜(한국 시간, 포함) 이후의 등록·수정 이력, 최신순 */
    public List<HistoryRow> findHistorySince(LocalDate since) {
        return jdbc.query("SELECT h.history_id, h.log_id, h.kind, h.actor_id, h.occurred_at, l.client_id, l.log_date"
                        + " FROM log_history h JOIN daily_log l ON l.log_id = h.log_id"
                        + " WHERE (h.occurred_at AT TIME ZONE 'Asia/Seoul')::date >= :since"
                        + " ORDER BY h.occurred_at DESC, h.history_id DESC",
                new MapSqlParameterSource("since", since),
                (rs, n) -> new HistoryRow(rs.getLong("history_id"), rs.getLong("log_id"), rs.getString("kind"),
                        rs.getLong("actor_id"), rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getLong("client_id"), rs.getObject("log_date", LocalDate.class)));
    }

    // ==================================================================
    // 저장
    // ==================================================================

    /** 새 일지 한 줄 추가 → 발급된 log_id 반환 */
    public long insertLog(long clientId, LocalDate date, String status, short weatherId, short conditionId,
                          String conditionEtc, String remark, long actorId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("date", date)
                .addValue("status", status)
                .addValue("weatherId", weatherId)
                .addValue("conditionId", conditionId)
                .addValue("conditionEtc", conditionEtc, Types.VARCHAR)   // null일 수 있어서 타입을 알려 줌
                .addValue("remark", remark, Types.VARCHAR)
                .addValue("actor", actorId);
        Long id = jdbc.queryForObject("INSERT INTO daily_log"
                + " (client_id, log_date, status, weather_id, condition_id, condition_etc, remark, created_by)"
                + " VALUES (:clientId, :date, :status, :weatherId, :conditionId, :conditionEtc, :remark, :actor)"
                + " RETURNING log_id", p, Long.class);                   // RETURNING: 새 번호를 바로 돌려받기
        return id;
    }

    /** 기존 일지 내용 수정. markEditor가 true면 수정자(updated_by)도 기록 */
    public void updateLog(long logId, String status, short weatherId, short conditionId,
                          String conditionEtc, String remark, long actorId, boolean markEditor) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", logId)
                .addValue("status", status)
                .addValue("weatherId", weatherId)
                .addValue("conditionId", conditionId)
                .addValue("conditionEtc", conditionEtc, Types.VARCHAR)
                .addValue("remark", remark, Types.VARCHAR)
                .addValue("actor", actorId);
        String editor = markEditor ? ", updated_by = :actor" : "";
        jdbc.update("UPDATE daily_log SET status = :status, weather_id = :weatherId, condition_id = :conditionId,"
                + " condition_etc = :conditionEtc, remark = :remark, updated_at = now()" + editor
                + " WHERE log_id = :id", p);
    }

    /** 그 일지의 활동 전부 삭제 (연결 테이블은 ON DELETE CASCADE로 같이 지워짐) */
    public void deleteRecords(long logId) {
        jdbc.update("DELETE FROM activity_record WHERE log_id = :id", new MapSqlParameterSource("id", logId));
    }

    /** 활동 한 줄 추가 → 발급된 record_id 반환 */
    public long insertRecord(long logId, LocalTime start, LocalTime end, String content, boolean hasBehavior) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("logId", logId)
                .addValue("start", start, Types.TIME)
                .addValue("end", end, Types.TIME)
                .addValue("content", content, Types.VARCHAR)
                .addValue("hasBehavior", hasBehavior);
        Long id = jdbc.queryForObject("INSERT INTO activity_record (log_id, start_time, end_time, content, has_behavior)"
                + " VALUES (:logId, :start, :end, :content, :hasBehavior) RETURNING record_id", p, Long.class);
        return id;
    }

    /** 연결 테이블에 선택 항목 여러 개를 한 번에 추가 */
    public void insertPicks(String junction, String idColumn, long recordId, List<NewPick> picks) {
        if (picks.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = picks.stream()
                .map(pick -> new MapSqlParameterSource()
                        .addValue("recordId", recordId)
                        .addValue("itemId", pick.itemId())
                        .addValue("etcText", pick.etcText(), Types.VARCHAR))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT INTO " + junction + " (record_id, " + idColumn + ", etc_text)"
                + " VALUES (:recordId, :itemId, :etcText)", batch);
    }

    /** 오늘의 소식용 이력 한 줄 추가 (kind: created | updated) */
    public void insertHistory(long logId, String kind, long actorId) {
        jdbc.update("INSERT INTO log_history (log_id, kind, actor_id) VALUES (:logId, :kind, :actor)",
                new MapSqlParameterSource().addValue("logId", logId).addValue("kind", kind).addValue("actor", actorId));
    }

    // ---- 조회 결과 한 줄 → LogRow ----
    private static LogRow mapLog(ResultSet rs) throws SQLException {
        long updatedBy = rs.getLong("updated_by");
        Long updatedByOrNull = rs.wasNull() ? null : updatedBy;     // NULL이면 null로
        return new LogRow(rs.getLong("log_id"), rs.getLong("client_id"), rs.getObject("log_date", LocalDate.class),
                rs.getString("status"), rs.getShort("weather_id"), rs.getShort("condition_id"),
                rs.getString("condition_etc"), rs.getString("remark"), rs.getLong("created_by"), updatedByOrNull,
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }
}