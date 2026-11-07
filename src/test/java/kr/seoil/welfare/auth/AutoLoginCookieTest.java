package kr.seoil.welfare.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.stream.Stream;
import kr.seoil.welfare.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AutoLoginCookieTest {
    static Stream<Arguments> cookies() {
        return Stream.of(true, false).flatMap(secure -> Stream.of(Boolean.TRUE, Boolean.FALSE, null)
                .map(auto -> Arguments.of(secure, auto)));
    }
    @ParameterizedTest @MethodSource("cookies")
    void 자동로그인_선택시에만_영속쿠키(boolean secure, Boolean auto) {
        AuthService service = mock(AuthService.class);
        when(service.authenticate(any())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(secure);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AuthController(service).login(new LoginRequest("demo", "password1", auto), request, response);
        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("HttpOnly", "SameSite=Lax", "Path=/");
        assertThat(header.contains("Secure")).isEqualTo(secure);
        if (Boolean.TRUE.equals(auto)) assertThat(header).contains("Max-Age=2592000");
        else assertThat(header).doesNotContain("Max-Age", "Expires");
        assertThat(request.getSession().getMaxInactiveInterval()).isEqualTo(Boolean.TRUE.equals(auto) ? 2592000 : 3600);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"/carelog", "/welfare"})
    void 앱경로와_쿠키이름_설정_준수(String context) {
        AuthService service = mock(AuthService.class);
        when(service.authenticate(any())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(context);
        request.getServletContext().getSessionCookieConfig().setName("CARELOG_SESSION");
        request.getServletContext().getSessionCookieConfig().setSecure(true); // 프록시 뒤 HTTP 요청도 운영 쿠키의 Secure 유지
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AuthController(service).login(new LoginRequest("demo", "password1", false), request, response);
        assertThat(response.getHeader("Set-Cookie")).startsWith("CARELOG_SESSION=").contains("Path=" + context, "Secure");
    }
    @Test void 로그아웃시_영속쿠키도_삭제() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        var session = (org.springframework.mock.web.MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AuthController(mock(AuthService.class)).logout(request, response);
        assertThat(session.isInvalid()).isTrue();
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }
    @Test void 자동로그인_해제시_기존세션교체() {
        AuthService service = mock(AuthService.class);
        when(service.authenticate(any())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        var old = (org.springframework.mock.web.MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AuthController(service).login(new LoginRequest("demo", "password1", false), request, response);
        assertThat(old.isInvalid()).isTrue();
        assertThat(response.getHeader("Set-Cookie")).doesNotContain("Max-Age");
        assertThat(request.getSession().getId()).isNotEqualTo(old.getId());
    }
}
