package kr.seoil.welfare.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kr.seoil.welfare.auth.dto.ChangePasswordRequest;
import kr.seoil.welfare.auth.dto.LoginRequest;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.staff.StaffDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 로그인 API의 입구 (명세서 3.1 LG, 3.18 SE / API 4.3).
 *
 *  POST /api/auth/login      로그인 — 성공하면 세션 쿠키가 만들어진다
 *  GET  /api/auth/me         로그인한 직원 정보 (안 했으면 401)
 *  POST /api/auth/logout     로그아웃 (세션 삭제)
 *  PUT  /api/auth/password   비밀번호 변경
 *
 * 인증 방식은 쿠키 세션이다 (명세서 4.1). 로그인에 성공하면 세션에
 * SessionStaff.KEY("staffId") 이름으로 직원 번호를 넣고, 다른 기능들은
 * @SessionAttribute 로 그 값을 꺼내 쓴다. 프론트는 모든 요청에 credentials: include 로 쿠키를 보낸다.
 *
 * 규칙 판단(비밀번호 비교, 중복 확인 등)은 AuthService가 한다. 여기서는 세션만 다룬다.
 * 회원가입은 없다. 계정은 팀장·국장이 계정 추가(POST /api/staff)로 만든다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * 세션 유지 시간(초) — 이 시간 동안 아무 요청이 없으면 자동으로 로그아웃된다.
     *
     * 자동 로그인 체크를 안 하면 1시간, 체크하면 30일. 공용 기기 정책과 정확한 기간은
     * 아직 회의에서 정하지 않았다 (명세서 B-9). 정해지면 이 두 숫자만 바꾸면 된다.
     *
     * ※ 주의: 지금은 '서버가 세션을 얼마나 들고 있는지'만 길어진다. 세션 쿠키 자체에는
     *   만료 시각이 없어서 브라우저를 완전히 닫으면 쿠키가 사라진다. 브라우저를 닫았다
     *   다시 열어도 로그인이 남아 있게 하려면 쿠키에 만료 시각을 넣어야 하는데,
     *   그건 공용 기기에서 위험할 수 있어 B-9에서 함께 정한다.
     */
    private static final int SESSION_SECONDS = 60 * 60;                    // 1시간
    private static final int AUTO_LOGIN_SESSION_SECONDS = 60 * 60 * 24 * 30;   // 30일

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login — 로그인 (LG-01, LG-02)
     * 실패하면 401 INVALID_CREDENTIALS, 사용 중지 계정이면 403 ACCOUNT_DISABLED.
     */
    @PostMapping("/login")
    public StaffDto login(@RequestBody(required = false) LoginRequest body, HttpServletRequest request) {
        long staffId = authService.authenticate(body);          // 틀리면 여기서 끝남

        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();       // 이전 세션은 버리고 새로 만든다 (세션 고정 공격 방지의 기본)
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionStaff.KEY, staffId);        // ★ 다른 기능들이 꺼내 쓰는 값
        session.setMaxInactiveInterval(
                Boolean.TRUE.equals(body.autoLogin()) ? AUTO_LOGIN_SESSION_SECONDS : SESSION_SECONDS);

        return authService.me(staffId);
    }

    /**
     * GET /api/auth/me — 로그인한 직원 정보 (LG-04, MY-01, SE-01)
     * 세션이 없으면 401. 로그인한 뒤에 계정이 사용 중지되었으면 세션을 끊고 403 (명세서 8.3).
     */
    @GetMapping("/me")
    public StaffDto me(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                       HttpServletRequest request) {
        long id = SessionStaff.require(staffId);                // 로그인 안 했으면 401
        try {
            return authService.me(id);
        } catch (ApiException e) {
            // 계정이 사용 중지·삭제되었다면 살아 있는 세션도 여기서 끊는다
            invalidateSession(request);
            throw e;
        }
    }

    /**
     * POST /api/auth/logout — 로그아웃 (MY-12)
     * 로그인 상태가 아니어도 204로 돌려준다. 프론트는 실패해도 이 기기의 로그인 상태를 지우므로
     * 여기서 401을 던지면 "로그아웃이 실패했다"는 혼란만 생긴다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        invalidateSession(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/auth/password — 비밀번호 변경 (SE-02)
     * 현재 비밀번호가 틀리면 400 CURRENT_PASSWORD_MISMATCH, 규칙 위반이면 400 PASSWORD_POLICY.
     * 바꾼 뒤에는 세션을 끊어서 새 비밀번호로 다시 로그인하게 한다.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
            @RequestBody(required = false) ChangePasswordRequest body,
            HttpServletRequest request) {
        long id = SessionStaff.require(staffId);
        authService.changePassword(id, body);
        invalidateSession(request);                             // 비밀번호가 바뀌었으니 다시 로그인
        return ResponseEntity.noContent().build();
    }

    /** 세션이 있으면 지운다 (없으면 아무것도 안 함) */
    private static void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
