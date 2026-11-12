package kr.seoil.welfare.card;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 오늘 카드 SQL 모음 (today_card, today_card_item, today_card_pick)
 * 시각은 서비스가 Clock으로 정해서 넘긴다. 테스트에서 '5분 지남', '7일 지남'을 고정 시각으로 확인하기 위해서다.
 */
@Repository
public class CardRepository {

    /** 카드 1장 */
    public record CardRow(long cardId, long logId, String status, String summary, boolean needsCall,
                          String tokenHash, String tokenEnc, OffsetDateTime sentAt, LocalDate expiresOn,
                          int verifyFailCount, OffsetDateTime verifyLockedUntil, OffsetDateTime updatedAt) {
    }

    /** 카드 항목 1개 */
    public record ItemRow(long itemId, int sortOrder, LocalTime startTime, String activityLabel, String word,
                          String pictogram, String mood, String easyText, String guardianText, String homeTip) {
    }

    /** 새로 넣을 카드 항목 (AI 결과를 검증한 것) */
    public record NewItem(Long recordId, int sortOrder, LocalTime startTime, String activityLabel, String word,
                          String pictogram, String mood, String easyText, String guardianText, String homeTip) {
    }

    /** 담당자가 고친 문장 */
    public record TextEdit(long itemId, String easyText, String guardianText, String homeTip) {
    }

    /** 보호자가 고른 결과 */
    public record PickRow(Long itemId, String how, boolean skipped, OffsetDateTime pickedAt) {
    }

    /** 카드에 필요한 일지·이용자 정보 */
    public record LogInfo(long logId, long clientId, LocalDate date, String status, long createdBy, Long clientStaffId,
                          LocalDate birthDate, String nickname, boolean consent) {
    }

    private static final String CARD_COLUMNS = "card_id, log_id, status, summary, needs_call, token_hash, token_enc,"
            + " sent_at, expires_on, verify_fail_count, verify_locked_until, updated_at";

    private static final RowMapper<CardRow> CARD = (rs, n) -> new CardRow(
            rs.getLong("card_id"), rs.getLong("log_id"), rs.getString("status"), rs.getString("summary"),
            rs.getBoolean("needs_call"), rs.getString("token_hash"), rs.getString("token_enc"),
            rs.getObject("sent_at", OffsetDateTime.class), rs.getObject("expires_on", LocalDate.class),
            rs.getInt("verify_fail_count"), rs.getObject("verify_locked_until", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private static final RowMapper<ItemRow> ITEM = (rs, n) -> new ItemRow(
            rs.getLong("item_id"), rs.getInt("sort_order"), rs.getObject("start_time", LocalTime.class),
            rs.getString("activity_label"), rs.getString("word"), rs.getString("pictogram"), rs.getString("mood"),
            rs.getString("easy_text"), rs.getString("guardian_text"), rs.getString("home_tip"));

    private final NamedParameterJdbcTemplate jdbc;

    public CardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    public CardRow findByLogId(long logId) {
        return one("SELECT " + CARD_COLUMNS + " FROM today_card WHERE log_id = :id", logId);
    }

    /** 고치기·보내기·다시 만들기는 잠그고 읽는다 (동시에 두 사람이 고칠 때 순서대로 처리) */
    public CardRow lockByLogId(long logId) {
        return one("SELECT " + CARD_COLUMNS + " FROM today_card WHERE log_id = :id FOR UPDATE", logId);
    }

    public CardRow findByTokenHash(String tokenHash) {
        return jdbc.query("SELECT " + CARD_COLUMNS + " FROM today_card WHERE token_hash = :hash",
                new MapSqlParameterSource("hash", tokenHash), CARD).stream().findFirst().orElse(null);
    }

    public CardRow lockByTokenHash(String tokenHash) {
        return jdbc.query("SELECT " + CARD_COLUMNS + " FROM today_card WHERE token_hash = :hash FOR UPDATE",
                new MapSqlParameterSource("hash", tokenHash), CARD).stream().findFirst().orElse(null);
    }

    public List<ItemRow> items(long cardId) {
        return jdbc.query("SELECT item_id, sort_order, start_time, activity_label, word, pictogram, mood,"
                        + " easy_text, guardian_text, home_tip FROM today_card_item WHERE card_id = :id ORDER BY sort_order",
                new MapSqlParameterSource("id", cardId), ITEM);
    }

    public PickRow pick(long cardId) {
        return jdbc.query("SELECT item_id, how, skipped, picked_at FROM today_card_pick WHERE card_id = :id",
                new MapSqlParameterSource("id", cardId), (rs, n) -> new PickRow(
                        nullableLong(rs, "item_id"), rs.getString("how"), rs.getBoolean("skipped"),
                        rs.getObject("picked_at", OffsetDateTime.class))).stream().findFirst().orElse(null);
    }

    /** 일지와 이용자 정보 (없으면 null) */
    public LogInfo logInfo(long logId) {
        return jdbc.query("SELECT l.log_id, l.client_id, l.log_date, l.status, l.created_by, c.staff_id, c.birth_date,"
                        + " c.card_nickname, c.card_consent"
                        + " FROM daily_log l JOIN client c ON c.client_id = l.client_id WHERE l.log_id = :id",
                new MapSqlParameterSource("id", logId), (rs, n) -> new LogInfo(
                        rs.getLong("log_id"), rs.getLong("client_id"), rs.getObject("log_date", LocalDate.class), rs.getString("status"), rs.getLong("created_by"),
                        nullableLong(rs, "staff_id"), rs.getObject("birth_date", LocalDate.class),
                        rs.getString("card_nickname"), rs.getBoolean("card_consent")))
                .stream().findFirst().orElse(null);
    }

    /** 일지에 활동이 있는지 (활동은 저장될 때 항상 시작 시각이 있다) */
    public boolean hasActivities(long logId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM activity_record WHERE log_id = :id",
                new MapSqlParameterSource("id", logId), Integer.class);
        return n != null && n > 0;
    }

    /** 참여 활동 이름 → 기본 그림 */
    public Map<String, String> participationPictograms() {
        Map<String, String> result = new HashMap<>();
        jdbc.query("SELECT name, pictogram FROM activity_type", rs -> {
            result.put(rs.getString("name"), rs.getString("pictogram"));
        });
        return result;
    }

    /** 도전적 행동 세부 코드 → 이름 (sh-1 → 머리 때리기) */
    public Map<String, String> behaviorDetailNames() {
        Map<String, String> result = new HashMap<>();
        jdbc.query("SELECT code, name FROM behavior_item", rs -> {
            result.put(rs.getString("code"), rs.getString("name"));
        });
        return result;
    }

    // ------------------------------------------------------------------
    // 저장
    // ------------------------------------------------------------------

    /** 일지를 작성 완료하면 카드를 '만드는 중'으로 만든다. 이미 있으면 아무것도 하지 않고 false */
    public boolean insertGenerating(long logId, OffsetDateTime now) {
        return jdbc.update("INSERT INTO today_card (log_id, status, created_at, updated_at)"
                        + " VALUES (:id, 'generating', :now, :now) ON CONFLICT (log_id) DO NOTHING",
                new MapSqlParameterSource().addValue("id", logId).addValue("now", now)) > 0;
    }

    public void setStatus(long cardId, String status, String failReason, OffsetDateTime now) {
        jdbc.update("UPDATE today_card SET status = :status, fail_reason = :reason, updated_at = :now WHERE card_id = :id",
                new MapSqlParameterSource().addValue("id", cardId).addValue("status", status)
                        .addValue("reason", failReason, Types.VARCHAR).addValue("now", now));
    }

    /** 만드는 중인 카드만 실패로 바꾼다. 그사이 결과가 저장됐으면 바꾸지 않는다 */
    public boolean failIfGenerating(long cardId, String reason, OffsetDateTime now) {
        return jdbc.update("UPDATE today_card SET status = 'failed', fail_reason = :reason, updated_at = :now"
                        + " WHERE card_id = :id AND status = 'generating'",
                new MapSqlParameterSource().addValue("id", cardId).addValue("reason", reason).addValue("now", now)) > 0;
    }

    /** AI 결과 저장: 항목을 새로 넣고 '확인 대기'로 */
    public void saveResult(long cardId, String summary, List<NewItem> items, OffsetDateTime now) {
        deleteItems(cardId);
        for (NewItem item : items) {
            jdbc.update("INSERT INTO today_card_item (card_id, record_id, sort_order, start_time, activity_label, word,"
                            + " pictogram, mood, easy_text, guardian_text, home_tip)"
                            + " VALUES (:card, :record, :sort, :start, :label, :word, :pic, :mood, :easy, :guardian, :tip)",
                    new MapSqlParameterSource()
                            .addValue("card", cardId).addValue("record", item.recordId(), Types.BIGINT)
                            .addValue("sort", item.sortOrder()).addValue("start", item.startTime())
                            .addValue("label", item.activityLabel()).addValue("word", item.word())
                            .addValue("pic", item.pictogram()).addValue("mood", item.mood())
                            .addValue("easy", item.easyText()).addValue("guardian", item.guardianText())
                            .addValue("tip", item.homeTip(), Types.VARCHAR));
        }
        jdbc.update("UPDATE today_card SET status = 'ready', summary = :summary, fail_reason = NULL, updated_at = :now"
                        + " WHERE card_id = :id",
                new MapSqlParameterSource().addValue("id", cardId).addValue("summary", summary).addValue("now", now));
    }

    /** 다시 만들기: 항목과 고른 결과를 지우고 '만드는 중'으로 */
    public void resetForRegenerate(long cardId, OffsetDateTime now) {
        deleteItems(cardId);
        jdbc.update("UPDATE today_card SET status = 'generating', summary = NULL, fail_reason = NULL, updated_at = :now"
                + " WHERE card_id = :id", new MapSqlParameterSource().addValue("id", cardId).addValue("now", now));
    }

    private void deleteItems(long cardId) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", cardId);
        jdbc.update("DELETE FROM today_card_pick WHERE card_id = :id", p);
        jdbc.update("DELETE FROM today_card_item WHERE card_id = :id", p);
    }

    /** 담당자가 고친 문장과 '따로 연락' 표시. 카드만 바꾸고 일지는 건드리지 않는다 */
    public void updateTexts(long cardId, List<TextEdit> edits, boolean needsCall, OffsetDateTime now) {
        for (TextEdit e : edits) {
            jdbc.update("UPDATE today_card_item SET easy_text = :easy, guardian_text = :guardian, home_tip = :tip"
                            + " WHERE item_id = :item AND card_id = :card",
                    new MapSqlParameterSource().addValue("item", e.itemId()).addValue("card", cardId)
                            .addValue("easy", e.easyText()).addValue("guardian", e.guardianText())
                            .addValue("tip", e.homeTip(), Types.VARCHAR));
        }
        jdbc.update("UPDATE today_card SET needs_call = :call, updated_at = :now WHERE card_id = :id",
                new MapSqlParameterSource().addValue("id", cardId).addValue("call", needsCall).addValue("now", now));
    }

    /** 공유 링크를 만들고 '보냄'으로 */
    public void markSent(long cardId, String tokenHash, String tokenEnc, OffsetDateTime now, LocalDate expiresOn) {
        jdbc.update("UPDATE today_card SET status = 'sent', token_hash = :hash, token_enc = :enc, sent_at = :now,"
                        + " expires_on = :expires, updated_at = :now WHERE card_id = :id",
                new MapSqlParameterSource().addValue("id", cardId).addValue("hash", tokenHash).addValue("enc", tokenEnc)
                        .addValue("now", now).addValue("expires", expiresOn));
    }

    /** 보호자 확인 실패 횟수와 잠금. 성공하면 0과 null로 되돌린다 */
    public void setVerifyState(long cardId, int failCount, OffsetDateTime lockedUntil) {
        jdbc.update("UPDATE today_card SET verify_fail_count = :count, verify_locked_until = :until WHERE card_id = :id",
                new MapSqlParameterSource().addValue("id", cardId).addValue("count", failCount)
                        .addValue("until", lockedUntil, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    /** 보호자가 고른 결과 (다시 고르면 덮어씀) */
    public void upsertPick(long cardId, Long itemId, String how, boolean skipped, OffsetDateTime now) {
        jdbc.update("INSERT INTO today_card_pick (card_id, item_id, how, skipped, picked_at)"
                        + " VALUES (:card, :item, :how, :skipped, :now)"
                        + " ON CONFLICT (card_id) DO UPDATE SET item_id = EXCLUDED.item_id, how = EXCLUDED.how,"
                        + " skipped = EXCLUDED.skipped, picked_at = EXCLUDED.picked_at",
                new MapSqlParameterSource().addValue("card", cardId).addValue("item", itemId, Types.BIGINT)
                        .addValue("how", how, Types.VARCHAR).addValue("skipped", skipped).addValue("now", now));
    }

    // ------------------------------------------------------------------

    private CardRow one(String sql, long logId) {
        return jdbc.query(sql, new MapSqlParameterSource("id", logId), CARD).stream().findFirst().orElse(null);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }
}
