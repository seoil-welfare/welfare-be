package kr.seoil.welfare.dev;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kr.seoil.welfare.auth.SessionStaff;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.staff.StaffDto;
import kr.seoil.welfare.staff.StaffQueries;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * ⚠ 임시 로그인 — 내 컴퓨터(local 프로필)에서 프론트로 일지 기능을 테스트하기 위한 것.
 *
 * - 비밀번호는 해시 검사 없이 demo1234 하나만 통과한다 (프론트 mock과 같음).
 * - local 프로필에서만 켜진다 (@Profile). 운영 서버에서는 이 클래스가 아예 없는 것처럼 동작.
 * - ★ 지민씨의 진짜 로그인(feature/login)이 main에 합쳐지면 이 파일을 삭제한다.
 *   (둘 다 있으면 /api/auth/login 주소가 겹쳐서 서버가 켜지지 않음)
 */
@Profile("local")
@RestController
@RequestMapping("/api/auth")
public class DevAuthController {

    private static final String DEV_PASSWORD = "demo1234";

    private final JdbcTemplate jdbc;
    private final StaffQueries staffQueries;

    public DevAuthController(JdbcTemplate jdbc, StaffQueries staffQueries) {
        this.jdbc = jdbc;
        this.staffQueries = staffQueries;
    }

    /** 로그인 요청 JSON: { loginId, password, autoLogin } */
    public record LoginRequest(String loginId, String password, Boolean autoLogin) {
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public StaffDto login(@RequestBody LoginRequest body, HttpServletRequest request) {
        if (body.loginId() == null || body.loginId().isBlank() || body.password() == null) {
            throw ApiException.badRequest("아이디와 비밀번호를 입력해 주세요.");
        }
        // 아이디로 직원 번호와 사용 여부 찾기
        var rows = jdbc.queryForList(
                "SELECT staff_id, is_active FROM staff WHERE login_id = ?", body.loginId().trim());
        if (rows.isEmpty() || !DEV_PASSWORD.equals(body.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "아이디 또는 비밀번호가 일치하지 않아요. 데모 계정을 확인해 주세요.");
        }
        Map<String, Object> row = rows.get(0);
        if (!Boolean.TRUE.equals(row.get("is_active"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "사용이 중지된 계정이에요.");
        }
        long staffId = ((Number) row.get("staff_id")).longValue();

        HttpSession old = request.getSession(false);           // 이전 로그인 세션이 있으면
        if (old != null) {
            old.invalidate();                                   // 버리고 새로 만든다 (보안 기본)
        }
        request.getSession(true).setAttribute(SessionStaff.KEY, staffId);   // ★ 세션에 직원 번호 저장
        return staffQueries.findById(staffId).orElseThrow();
    }

    /** GET /api/auth/me — 로그인한 직원 정보 (안 했으면 401) */
    @GetMapping("/me")
    public StaffDto me(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId) {
        long id = SessionStaff.require(staffId);
        return staffQueries.findById(id).orElseThrow(ApiException::unauthenticated);
    }

    /** POST /api/auth/logout — 세션 삭제, 204 응답 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
