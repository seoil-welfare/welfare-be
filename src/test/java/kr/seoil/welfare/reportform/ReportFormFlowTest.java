package kr.seoil.welfare.reportform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import kr.seoil.welfare.auth.AuthRepository;
import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.staff.StaffQueries;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 출력 양식 — 올리기·목록·숨기기·파일 받기와 권한·파일 검사를 실제 PostgreSQL과 운영 마이그레이션으로 확인한다.
 */
class ReportFormFlowTest {

    static EmbeddedPostgres pg;
    static JdbcTemplate jdbc;
    static ReportFormService service;

    static final long STAFF = 1;
    static final long LEAD = 2;
    static final long DIRECTOR = 3;

    @BeforeAll
    static void database() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        var source = pg.getPostgresDatabase();
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO staff (staff_id, login_id, password_hash, name, department, position, role) VALUES "
                + "(1,'qa.staff','unused','담당자','팀','담당자','staff'),"
                + "(2,'qa.lead','unused','팀장님','팀','팀장','teamLead'),"
                + "(3,'qa.director','unused','국장님','팀','국장','director')");
        AuthService auth = new AuthService(new AuthRepository(jdbc), new StaffQueries(jdbc), new BCryptPasswordEncoder());
        service = new ReportFormService(new ReportFormRepository(new NamedParameterJdbcTemplate(source)), auth);
    }

    @AfterAll
    static void stop() throws Exception {
        if (pg != null) pg.close();
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM report_form");
    }

    /** word/document.xml이 든 최소 docx */
    static byte[] docx(String text) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<w:document><w:body><w:p><w:r><w:t>" + text + "</w:t></w:r></w:p></w:body></w:document>")
                    .getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    static void expectError(Runnable action, HttpStatus status, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getStatus()).isEqualTo(status);
                    assertThat(((ApiException) e).getCode()).isEqualTo(code);
                });
    }

    @Test
    void 팀장이_올리면_목록에_나오고_같은_파일을_받는다() throws Exception {
        byte[] file = docx("{이용자이름}");
        ReportFormResponse created = service.upload(LEAD, "daily", "  우리 기관 일지  ", "C:\\fakepath\\우리기관.docx", file);

        assertThat(created.name()).isEqualTo("우리 기관 일지");             // 앞뒤 공백 제거
        assertThat(created.fileName()).isEqualTo("우리기관.docx");          // 경로 제거
        assertThat(created.kind()).isEqualTo("daily");
        assertThat(created.active()).isTrue();
        assertThat(created.uploadedBy()).isEqualTo("팀장님");
        assertThat(created.uploadedAt()).isNotNull();

        assertThat(service.list(STAFF)).extracting(ReportFormResponse::id).containsExactly(created.id());
        var stored = service.file(STAFF, created.id());                       // 담당자도 출력용으로 받을 수 있다
        assertThat(stored.content()).isEqualTo(file);
        assertThat(stored.fileName()).isEqualTo("우리기관.docx");
    }

    @Test
    void 숨겼다가_다시_사용한다_지우지는_않는다() throws Exception {
        ReportFormResponse created = service.upload(DIRECTOR, "monthly", "월간", "월간.docx", docx("{월}"));

        ReportFormResponse hidden = service.setActive(LEAD, created.id(), false);
        assertThat(hidden.active()).isFalse();
        assertThat(service.list(STAFF)).hasSize(1);                           // 숨긴 양식도 목록에 남는다
        assertThat(service.file(STAFF, created.id()).content()).isNotEmpty(); // 파일도 그대로

        assertThat(service.setActive(LEAD, created.id(), true).active()).isTrue();
    }

    @Test
    void 담당자는_올리거나_숨길_수_없다() throws Exception {
        expectError(() -> service.upload(STAFF, "daily", "양식", "a.docx", docxUnchecked()), HttpStatus.FORBIDDEN, "FORBIDDEN");
        ReportFormResponse created = service.upload(LEAD, "daily", "양식", "a.docx", docx("{날씨}"));
        expectError(() -> service.setActive(STAFF, created.id(), false), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void docx가_아니면_막는다() throws Exception {
        byte[] ok = docx("{날씨}");
        expectError(() -> service.upload(LEAD, "daily", "양식", "기관.hwp", ok), HttpStatus.BAD_REQUEST, "INVALID_FORM_FILE");
        expectError(() -> service.upload(LEAD, "daily", "양식", "가짜.docx", "not a zip".getBytes(StandardCharsets.UTF_8)),
                HttpStatus.BAD_REQUEST, "INVALID_FORM_FILE");
        expectError(() -> service.upload(LEAD, "daily", "양식", "빈.docx", new byte[0]), HttpStatus.BAD_REQUEST, "INVALID_FORM_FILE");
        expectError(() -> service.upload(LEAD, "daily", "양식", "큰.docx", new byte[ReportFormService.MAX_BYTES + 1]),
                HttpStatus.BAD_REQUEST, "INVALID_FORM_FILE");
        expectError(() -> service.upload(LEAD, "daily", "양식", null, ok), HttpStatus.BAD_REQUEST, "INVALID_FORM_FILE");
        assertThat(service.list(LEAD)).isEmpty();
    }

    @Test
    void 종류와_이름을_검사한다() throws Exception {
        byte[] ok = docx("{날씨}");
        expectError(() -> service.upload(LEAD, "weekly", "양식", "a.docx", ok), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(() -> service.upload(LEAD, "daily", "   ", "a.docx", ok), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(() -> service.upload(LEAD, "daily", "가".repeat(31), "a.docx", ok), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expectError(() -> service.setActive(LEAD, "1", null), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void 없는_양식은_404() {
        expectError(() -> service.setActive(LEAD, "999", false), HttpStatus.NOT_FOUND, "NOT_FOUND");
        expectError(() -> service.file(STAFF, "999"), HttpStatus.NOT_FOUND, "NOT_FOUND");
        expectError(() -> service.file(STAFF, "abc"), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static byte[] docxUnchecked() {
        try {
            return docx("{날씨}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
