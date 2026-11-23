package kr.seoil.welfare.reportform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.common.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 출력 양식(기관이 올린 docx) 규칙.
 *
 *  - 목록·파일 받기: 로그인한 직원 누구나 (출력할 때 쓴다)
 *  - 올리기·숨기기·다시 사용: 팀장·국장만
 *  - docx만, 5MB 이하. 이름만 docx인 파일도 막는다 (zip 안에 word/document.xml이 있어야 한다)
 *  - 지우지 않는다. 숨기면 출력할 때 고르는 목록에서만 빠진다
 *  - 양식 안의 칸 이름 검사(앱이 모르는 칸 안내 등)는 프론트가 올리기 전에 한다. 서버는 파일이 docx인지만 본다
 */
@Service
public class ReportFormService {

    private static final Set<String> KINDS = Set.of("daily", "monthly");
    /** 파일 최대 크기. 프론트(REPORT_FORM_MAX_BYTES)·application.yml·nginx와 맞춘다 */
    static final int MAX_BYTES = 5 * 1024 * 1024;
    /** report_form.name 칸 길이 */
    static final int MAX_NAME = 30;
    static final String INVALID_FILE = "INVALID_FORM_FILE";

    private final ReportFormRepository repo;
    private final AuthService authService;

    public ReportFormService(ReportFormRepository repo, AuthService authService) {
        this.repo = repo;
        this.authService = authService;
    }

    /** 숨긴 양식까지 두 종류 모두 */
    public List<ReportFormResponse> list(long actorId) {
        authService.me(actorId);                                    // 사용 중지된 계정이면 403
        return repo.list();
    }

    /** 양식 올리기 → 만들어진 양식 */
    public ReportFormResponse upload(long actorId, String kind, String name, String fileName, byte[] content) {
        checkManager(actorId, "양식은 팀장·국장만 올릴 수 있어요.");
        if (kind == null || !KINDS.contains(kind)) {
            throw ApiException.badRequest("양식 종류가 올바르지 않아요.");
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("양식 이름을 입력해 주세요.");
        }
        if (trimmed.length() > MAX_NAME) {
            throw ApiException.badRequest("양식 이름은 " + MAX_NAME + "자 이내로 입력해 주세요.");
        }
        checkFile(fileName, content);
        long id = repo.insert(kind, trimmed, cleanFileName(fileName), content, actorId);
        return repo.find(id).orElseThrow();
    }

    /** 숨기기(false)·다시 사용(true) → 바뀐 양식 */
    public ReportFormResponse setActive(long actorId, String formId, Boolean active) {
        checkManager(actorId, "양식은 팀장·국장만 바꿀 수 있어요.");
        if (active == null) {
            throw ApiException.badRequest("숨길지 다시 사용할지 정해 주세요.");
        }
        long id = parseId(formId);
        if (repo.setActive(id, active) == 0) {
            throw ApiException.notFound("양식");
        }
        return repo.find(id).orElseThrow();
    }

    /** 양식 파일 내용 */
    public ReportFormRepository.StoredFile file(long actorId, String formId) {
        authService.me(actorId);
        return repo.file(parseId(formId)).orElseThrow(() -> ApiException.notFound("양식"));
    }

    // ------------------------------------------------------------------

    private void checkManager(long actorId, String message) {
        String role = authService.me(actorId).role();
        if (!Roles.isManager(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
        }
    }

    /** docx인지: 확장자, 크기, zip 안의 word/document.xml */
    static void checkFile(String fileName, byte[] content) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw invalid("Word(docx) 파일만 올릴 수 있어요.");
        }
        if (content == null || content.length == 0) {
            throw invalid("빈 파일이에요. 다시 골라 주세요.");
        }
        if (content.length > MAX_BYTES) {
            throw invalid("파일이 너무 커요. 5MB 이하로 올려 주세요.");
        }
        // 압축을 풀지 않고 항목 이름만 훑는다
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return;
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // 아래에서 같은 오류로 막는다
        }
        throw invalid("Word(docx) 파일이 아니에요. docx 파일만 올릴 수 있어요.");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, INVALID_FILE, message);
    }

    /** 경로가 붙어 오는 브라우저도 있어 파일 이름만 남긴다 */
    private static String cleanFileName(String fileName) {
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static long parseId(String formId) {
        try {
            return Long.parseLong(formId);
        } catch (NumberFormatException e) {
            throw ApiException.notFound("양식");
        }
    }
}
