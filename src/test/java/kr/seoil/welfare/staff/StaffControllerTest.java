package kr.seoil.welfare.staff;

import java.util.Optional;

import kr.seoil.welfare.auth.AuthRepository;
import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.auth.SessionStaff;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/staff 주소와 응답 모양 검사 (DB 없이 웹 계층만).
 * 규칙은 AuthServiceStaffRoleTest에서 보고, 여기서는 주소·세션·오류 JSON만 본다.
 */
@WebMvcTest(StaffController.class)
@Import(GlobalExceptionHandler.class)
class StaffControllerTest {

    private static final StaffDto LEAD3 =
            new StaffDto("3", "다 담당", "주간활동지원팀", "팀장", "teamLead", "demo.staff3", true, null);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private StaffQueries staffQueries;

    @MockitoBean   // ActiveStaffInterceptor가 요청마다 계정 상태를 본다
    private AuthRepository authRepository;

    @BeforeEach
    void activeAccount() {
        given(authRepository.findById(anyLong()))
                .willAnswer(inv -> Optional.of(new AuthRepository.Account(inv.getArgument(0), "hash", true)));
    }

    private static MockHttpSession sessionOf(long staffId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionStaff.KEY, staffId);
        return session;
    }

    // ---- 역할 변경 ----

    @Test
    @DisplayName("POST /api/staff/{id}/role — 로그인 안 했으면 401")
    void changeRole_noSession_401() throws Exception {
        mvc.perform(post("/api/staff/3/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"teamLead\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("POST /api/staff/{id}/role — 200과 바뀐 StaffDto")
    void changeRole_ok_returnsStaffDto() throws Exception {
        given(authService.changeRole(eq(2L), eq("3"), any())).willReturn(LEAD3);

        mvc.perform(post("/api/staff/3/role").session(sessionOf(2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"teamLead\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("teamLead"))
                .andExpect(jsonPath("$.position").value("팀장"));

        ArgumentCaptor<ChangeRoleRequest> body = ArgumentCaptor.forClass(ChangeRoleRequest.class);
        verify(authService).changeRole(eq(2L), eq("3"), body.capture());
        assertEquals("teamLead", body.getValue().role());
    }

    @Test
    @DisplayName("POST /api/staff/{id}/role — 서비스의 403을 { message, code }로 그대로")
    void changeRole_serviceForbidden_passesThrough() throws Exception {
        willThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "역할 변경은 팀장·국장만 할 수 있어요."))
                .given(authService).changeRole(anyLong(), any(), any());

        mvc.perform(post("/api/staff/5/role").session(sessionOf(3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"staff\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("역할 변경은 팀장·국장만 할 수 있어요."));
    }

    // ---- 관리 권한 이양 ----

    private static final StaffDto NEW_DIRECTOR =
            new StaffDto("2", "라 팀장", "주간활동지원팀", "국장", "director", "demo.lead", true, null);

    private static StaffDto previous(boolean active) {
        return new StaffDto("1", "마 국장", "사무국", "팀장", "teamLead", "demo.director", active, null);
    }

    @Test
    @DisplayName("POST /api/staff/{id}/transfer-director — deactivate면 200 뒤 세션이 끊기고 다음 요청은 401")
    void transfer_deactivate_invalidatesSession() throws Exception {
        given(authService.transferDirector(eq(1L), eq("2"), any()))
                .willReturn(new TransferDirectorResponse(NEW_DIRECTOR, previous(false)));
        MockHttpSession session = sessionOf(1);

        mvc.perform(post("/api/staff/2/transfer-director").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previousRole\":\"deactivate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.director.role").value("director"))
                .andExpect(jsonPath("$.previous.active").value(false));

        assertTrue(session.isInvalid(), "deactivate 뒤에는 세션을 끊어야 한다");
        mvc.perform(post("/api/staff/3/role")                      // 세션이 끊긴 브라우저의 다음 요청
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"teamLead\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/staff/{id}/transfer-director — teamLead면 세션 유지")
    void transfer_teamLead_keepsSession() throws Exception {
        given(authService.transferDirector(eq(1L), eq("2"), any()))
                .willReturn(new TransferDirectorResponse(NEW_DIRECTOR, previous(true)));
        MockHttpSession session = sessionOf(1);

        mvc.perform(post("/api/staff/2/transfer-director").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previousRole\":\"teamLead\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previous.role").value("teamLead"));

        assertFalse(session.isInvalid());
    }

    @Test
    @DisplayName("POST /api/staff/{id}/transfer-director — 본문 없이 보내도 200 (서비스가 teamLead로 처리)")
    void transfer_noBody_ok() throws Exception {
        given(authService.transferDirector(eq(1L), eq("2"), isNull()))
                .willReturn(new TransferDirectorResponse(NEW_DIRECTOR, previous(true)));

        mvc.perform(post("/api/staff/2/transfer-director").session(sessionOf(1)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/staff/{id}/transfer-director — 로그인 안 했으면 401")
    void transfer_noSession_401() throws Exception {
        mvc.perform(post("/api/staff/2/transfer-director")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previousRole\":\"teamLead\"}"))
                .andExpect(status().isUnauthorized());
    }
}
