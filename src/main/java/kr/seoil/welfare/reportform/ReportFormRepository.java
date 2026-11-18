package kr.seoil.welfare.reportform;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** report_form 테이블 SQL 모음. 목록에는 파일 내용(content)을 읽지 않는다 */
@Repository
public class ReportFormRepository {

    private static final String SELECT = "SELECT f.form_id, f.kind, f.name, f.file_name, f.active, f.uploaded_at, s.name AS uploader"
            + " FROM report_form f JOIN staff s ON s.staff_id = f.uploaded_by";

    private static final RowMapper<ReportFormResponse> ROW = (rs, rowNum) -> new ReportFormResponse(
            String.valueOf(rs.getLong("form_id")),
            rs.getString("kind"),
            rs.getString("name"),
            rs.getString("file_name"),
            rs.getBoolean("active"),
            rs.getObject("uploaded_at", java.time.OffsetDateTime.class),
            rs.getString("uploader"));

    /** 받을 때 필요한 것만 */
    public record StoredFile(String fileName, byte[] content) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ReportFormRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 숨긴 양식까지 전부. 종류별, 최근에 올린 순 */
    public List<ReportFormResponse> list() {
        return jdbc.query(SELECT + " ORDER BY f.kind, f.uploaded_at DESC, f.form_id DESC", ROW);
    }

    public Optional<ReportFormResponse> find(long formId) {
        return jdbc.query(SELECT + " WHERE f.form_id = :id", new MapSqlParameterSource("id", formId), ROW)
                .stream().findFirst();
    }

    /** 새 양식 → 만들어진 번호 */
    public long insert(String kind, String name, String fileName, byte[] content, long uploadedBy) {
        var keys = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO report_form (kind, name, file_name, content, file_size, uploaded_by)"
                        + " VALUES (:kind, :name, :fileName, :content, :size, :by)",
                new MapSqlParameterSource()
                        .addValue("kind", kind)
                        .addValue("name", name)
                        .addValue("fileName", fileName)
                        .addValue("content", content)
                        .addValue("size", content.length)
                        .addValue("by", uploadedBy),
                keys, new String[] {"form_id"});
        return keys.getKey().longValue();
    }

    /** 숨기기·다시 사용 → 바꾼 줄 수 (없는 양식이면 0) */
    public int setActive(long formId, boolean active) {
        return jdbc.update("UPDATE report_form SET active = :active, updated_at = now() WHERE form_id = :id",
                new MapSqlParameterSource().addValue("active", active).addValue("id", formId));
    }

    public Optional<StoredFile> file(long formId) {
        return jdbc.query("SELECT file_name, content FROM report_form WHERE form_id = :id",
                new MapSqlParameterSource("id", formId),
                (rs, rowNum) -> new StoredFile(rs.getString("file_name"), rs.getBytes("content"))).stream().findFirst();
    }
}
