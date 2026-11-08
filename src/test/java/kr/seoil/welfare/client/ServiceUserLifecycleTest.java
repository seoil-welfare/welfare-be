package kr.seoil.welfare.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.List;
import java.util.stream.Stream;
import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.staff.StaffDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ServiceUserLifecycleTest {
    JdbcTemplate jdbc;
    AuthService auth;
    ServiceUserService service;

    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class);
        auth = mock(AuthService.class);
        service = new ServiceUserService(jdbc, auth);
    }

    void role(String role) {
        when(auth.me(1L)).thenReturn(new StaffDto("1", "직원", "팀", "직위", role, "demo", true, null, false));
    }

    static Stream<Arguments> managers() {
        return Stream.of("teamLead", "director").flatMap(role -> Stream.of(false, true)
                .flatMap(reopen -> Stream.of("active", "closed").map(status -> Arguments.of(role, reopen, status))));
    }

    @ParameterizedTest @MethodSource("managers")
    void 관리자는_종결_재개_가능_이미_같은_상태도_안전(String role, boolean reopen, String original) {
        role(role);
        ServiceUserDto expected = new ServiceUserDto("7", "이용자", "2000-01-01", "1", reopen ? "active" : "closed");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ServiceUserDto>>any(), eq(7L)))
                .thenReturn(List.of(expected));
        when(jdbc.update(anyString(), eq(7L))).thenReturn(original.equals(expected.status()) ? 0 : 1);
        assertThat(reopen ? service.reopen("7", 1) : service.close("7", 1)).isEqualTo(expected);
        verify(jdbc).update(eq(reopen
                ? "UPDATE client SET status = 'active', closed_at = NULL WHERE client_id = ? AND status = 'closed'"
                : "UPDATE client SET status = 'closed', closed_at = now() WHERE client_id = ? AND status = 'active'"), eq(7L));
        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ServiceUserDto>>any(), eq(7L));
        verifyNoMoreInteractions(jdbc);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void 일반_직원은_DB변경_전에_403(boolean reopen) {
        role("staff");
        assertThatThrownBy(() -> { if (reopen) service.reopen("7", 1); else service.close("7", 1); })
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "", "0", "-1", "9999999999999999999999"})
    void 잘못된_번호는_404_변경없음(String id) {
        role("teamLead");
        assertThatThrownBy(() -> service.close(id, 1)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void 없는_이용자는_404(boolean reopen) {
        role("director");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ServiceUserDto>>any(), eq(7L))).thenReturn(List.of());
        assertThatThrownBy(() -> { if (reopen) service.reopen("7", 1); else service.close("7", 1); })
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test void 비활성_관리자는_인증_서비스에서_차단() {
        when(auth.me(1)).thenThrow(ApiException.unauthenticated());
        assertThatThrownBy(() -> service.reopen("7", 1)).isInstanceOf(ApiException.class);
        verifyNoInteractions(jdbc);
    }
}
