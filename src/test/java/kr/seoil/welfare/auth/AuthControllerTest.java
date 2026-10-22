package kr.seoil.welfare.auth;

import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.common.GlobalExceptionHandler;
import kr.seoil.welfare.staff.StaffDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/auth 주소와 응답 모양 검사 (DB 없이 웹 계층만 띄운다).
 *
 * AuthService는 가짜(mock)로 바꿔 두고, 컨트롤러가
 *   - 주소·메서드를 제대로 받는지
 *   - 세션에 staffId를 넣고 지우는지
 *   - 오류를 명세서 4.2의 { message, code } 모양으로 돌려주는지
 * 만 본다. 비밀번호 비교 같은 규칙은 AuthService 쪽 책임이라 여기서 보지 않는다.
 */
@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)        // 오류를 JSON으로 바꾸는 공통 처리기
class AuthControllerTest {

    private static final StaffDto DEMO =
            new StaffDto("1", "가 담당", "주간활동지원팀", "사회복지사", "staff", "demo.staff1");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/login — 성공하면 200과 직원 정보, 세션에 staffId가 들어간다")
    void loginPutsStaffIdInSession() throws Exception {
        given(authService.authenticate(any())).willReturn(1L);
        given(authService.me(1L)).willReturn(DEMO);

        var result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"demo.staff1\",\"password\":\"demo1234\",\"autoLogin\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.name").value("가 담당"))
                .andReturn();

        var session = result.getRequest().getSession(false);
        assertNotNull(session, "로그인 성공 시 세션이 만들어져야 한다");
        assertEquals(Long.valueOf(1L), session.getAttribute(SessionStaff.KEY));
    }

    @Test
    @DisplayName("POST /api/auth/login — 비밀번호가 틀리면 401 INVALID_CREDENTIALS")
    void loginFailureReturnsErrorJson() throws Exception {
        willThrow(new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS", "아이디 또는 비밀번호가 일치하지 않아요."))
                .given(authService).authenticate(any());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"demo.staff1\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 일치하지 않아요."));
    }

    @Test
    @DisplayName("POST /api/auth/signup — 성공하면 201과 만들어진 직원 정보")
    void signupReturnsCreated() throws Exception {
        given(authService.signup(any())).willReturn(1L);
        given(authService.me(1L)).willReturn(DEMO);

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"demo.staff1\",\"password\":\"welfare2026\",\"name\":\"가 담당\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginId").value("demo.staff1"));
    }

    @Test
    @DisplayName("POST /api/auth/signup — 아이디가 겹치면 409 DUPLICATE_LOGIN_ID")
    void signupDuplicateReturnsConflict() throws Exception {
        willThrow(new ApiException(org.springframework.http.HttpStatus.CONFLICT,
                "DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디예요."))
                .given(authService).signup(any());

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"demo.staff1\",\"password\":\"welfare2026\",\"name\":\"가 담당\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));
    }

    @Test
    @DisplayName("GET /api/auth/me — 로그인 안 했으면 401 UNAUTHENTICATED")
    void meWithoutSessionIsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("GET /api/auth/me — 세션이 있으면 200과 내 정보")
    void meWithSessionReturnsStaff() throws Exception {
        given(authService.me(1L)).willReturn(DEMO);

        mvc.perform(get("/api/auth/me").session(sessionOf(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("staff"));
    }

    @Test
    @DisplayName("GET /api/auth/me — 사용 중지된 계정이면 403이고 세션도 끊긴다 (명세서 8.3)")
    void meDisabledAccountClearsSession() throws Exception {
        willThrow(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                "ACCOUNT_DISABLED", "사용이 중지된 계정이에요. 관리자에게 문의해 주세요."))
                .given(authService).me(anyLong());

        MockHttpSession session = sessionOf(1L);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        assertTrue(session.isInvalid(), "사용 중지 계정의 세션은 끊어야 한다");
    }

    @Test
    @DisplayName("POST /api/auth/logout — 로그인 상태가 아니어도 204")
    void logoutIsAlwaysNoContent() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());

        MockHttpSession session = sessionOf(1L);
        mvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
    }

    @Test
    @DisplayName("PUT /api/auth/password — 바꾸면 204이고 세션이 끊겨 다시 로그인해야 한다")
    void changePasswordClearsSession() throws Exception {
        MockHttpSession session = sessionOf(1L);

        mvc.perform(put("/api/auth/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"demo1234\",\"newPassword\":\"welfare2026\"}"))
                .andExpect(status().isNoContent());

        verify(authService).changePassword(eq(1L), any());
        assertTrue(session.isInvalid());
    }

    @Test
    @DisplayName("PUT /api/auth/password — 로그인 안 했으면 401")
    void changePasswordWithoutSessionIsUnauthorized() throws Exception {
        mvc.perform(put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"demo1234\",\"newPassword\":\"welfare2026\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("JSON 모양이 깨졌으면 400 VALIDATION_ERROR")
    void brokenJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\": }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /** staffId가 들어 있는 로그인된 세션 */
    private static MockHttpSession sessionOf(long staffId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionStaff.KEY, staffId);
        return session;
    }
}
