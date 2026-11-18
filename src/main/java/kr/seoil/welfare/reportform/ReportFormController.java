package kr.seoil.welfare.reportform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import kr.seoil.welfare.auth.SessionStaff;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;

/**
 *  GET  /api/report-forms             양식 목록 (숨긴 것 포함, 직원 누구나)
 *  POST /api/report-forms             양식 올리기 multipart: kind, name, file (팀장·국장만)
 *  PUT  /api/report-forms/{id}        { "active": false } 숨기기 / true 다시 사용 (팀장·국장만)
 *  GET  /api/report-forms/{id}/file   양식 파일 (직원 누구나, 출력할 때 쓴다)
 * 규칙은 ReportFormService에 있다.
 */
@RestController
@RequestMapping("/api/report-forms")
public class ReportFormController {

    static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ReportFormService service;

    public ReportFormController(ReportFormService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReportFormResponse> list(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId) {
        return service.list(SessionStaff.require(staffId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportFormResponse> upload(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String name,
            @RequestPart(required = false) MultipartFile file) throws IOException {
        long actor = SessionStaff.require(staffId);
        ReportFormResponse created = service.upload(actor, kind, name,
                file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** { "active": false } */
    public record ActiveRequest(Boolean active) {
    }

    @PutMapping("/{id}")
    public ReportFormResponse setActive(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
            @PathVariable String id,
            @RequestBody ActiveRequest body) {
        return service.setActive(SessionStaff.require(staffId), id, body == null ? null : body.active());
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
            @PathVariable String id) {
        ReportFormRepository.StoredFile stored = service.file(SessionStaff.require(staffId), id);
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(stored.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(stored.content());
    }
}
