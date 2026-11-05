package kr.seoil.welfare.notification.controller;

import kr.seoil.welfare.auth.AuthRepository;
import kr.seoil.welfare.auth.SessionStaff;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.common.GlobalExceptionHandler;
import kr.seoil.welfare.notification.dto.NotificationSettingsDto;
import kr.seoil.welfare.notification.dto.NotificationSummaryResponse;
import kr.seoil.welfare.notification.service.NotificationService;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/notifications 주소와 응답 모양 검사 (DB 없이 웹 계층만 띄운다).
 * 안 읽은 수 계산 같은 규칙은 NotificationService 쪽 책임이라 여기서 보지 않는다.
 */
@WebMvcTest(NotificationController.class)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean   // ActiveStaffInterceptor가 요청마다 계정 상태를 본다
    private AuthRepository authRepository;

    @BeforeEach
    void activeAccount() {
        given(authRepository.findById(anyLong()))
                .willAnswer(inv -> Optional.of(new AuthRepository.Account(inv.getArgument(0), "hash", true)));
    }

    private static MockHttpSession loggedIn(long staffId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionStaff.KEY, staffId);
        return session;
    }

    @Test
    @DisplayName("GET /api/notifications/summary — 로그인하지 않았으면 401 UNAUTHENTICATED")
    void summaryRequiresLogin() throws Exception {
        mvc.perform(get("/api/notifications/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("GET /api/notifications/summary — 안 읽은 수와 마지막 읽은 시각")
    void summaryReturnsUnreadCount() throws Exception {
        given(notificationService.summary(1L))
                .willReturn(new NotificationSummaryResponse(3, "2026-10-30T09:00+09:00"));

        mvc.perform(get("/api/notifications/summary").session(loggedIn(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.lastReadAt").value("2026-10-30T09:00+09:00"));
    }

    @Test
    @DisplayName("GET /api/notifications/summary — 한 번도 읽지 않았으면 lastReadAt이 응답에서 빠진다")
    void summaryOmitsNullLastReadAt() throws Exception {
        given(notificationService.summary(1L)).willReturn(new NotificationSummaryResponse(0, null));

        mvc.perform(get("/api/notifications/summary").session(loggedIn(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.lastReadAt").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/notifications/read — 204, 본인 번호로 읽음 처리")
    void markAllReadReturnsNoContent() throws Exception {
        mvc.perform(post("/api/notifications/read").session(loggedIn(2L)))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllRead(2L);
    }

    @Test
    @DisplayName("PUT /api/notifications/settings — 저장한 설정을 돌려준다")
    void updateSettingsReturnsSaved() throws Exception {
        given(notificationService.updateSettings(eq(1L), any()))
                .willReturn(new NotificationSettingsDto(true));

        mvc.perform(put("/api/notifications/settings").session(loggedIn(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notifyNewLog\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyNewLog").value(true));
    }

    @Test
    @DisplayName("PUT /api/notifications/settings — 값이 비면 400 VALIDATION_ERROR")
    void updateSettingsRejectsEmptyBody() throws Exception {
        given(notificationService.updateSettings(eq(1L), any()))
                .willThrow(ApiException.badRequest("알림 받기 여부(notifyNewLog)를 보내 주세요."));

        mvc.perform(put("/api/notifications/settings").session(loggedIn(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
