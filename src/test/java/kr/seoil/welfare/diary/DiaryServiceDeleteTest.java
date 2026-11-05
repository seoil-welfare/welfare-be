package kr.seoil.welfare.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.DiaryRepository.LockedLog;
import kr.seoil.welfare.staff.StaffDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 작성 중 일지 삭제 규칙 (DiaryService 규칙 9) — DB 없이 가짜 저장소로 확인 */
class DiaryServiceDeleteTest {

    private static final long AUTHOR = 1L;
    private static final long OTHER = 2L;
    private static final long LOG_ID = 10L;

    private DiaryRepository repo;
    private AuthService authService;
    private DiaryService service;

    @BeforeEach
    void setUp() {
        repo = mock(DiaryRepository.class);
        authService = mock(AuthService.class);
        service = new DiaryService(repo, mock(Catalog.class), authService, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    private void givenLog(String status) {
        when(repo.lockLog(LOG_ID)).thenReturn(new LockedLog(status, 5L, LocalDate.of(2026, 12, 1), AUTHOR));
    }

    private void givenRole(String role) {
        StaffDto me = mock(StaffDto.class);
        when(me.role()).thenReturn(role);
        when(authService.me(OTHER)).thenReturn(me);
    }

    private void assertApi(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
        verify(repo, never()).deleteLog(anyLong());
    }

    @Test
    void 없는_일지는_404() {
        assertApi(() -> service.delete(String.valueOf(LOG_ID), AUTHOR), HttpStatus.NOT_FOUND);
    }

    @Test
    void 숫자가_아닌_id는_404() {
        assertApi(() -> service.delete("abc", AUTHOR), HttpStatus.NOT_FOUND);
    }

    @Test
    void 작성_완료_일지는_작성자라도_400() {
        givenLog("completed");
        assertApi(() -> service.delete(String.valueOf(LOG_ID), AUTHOR), HttpStatus.BAD_REQUEST);
    }

    @Test
    void 작성_완료_일지는_팀장이라도_400() {
        givenLog("completed");
        givenRole("teamLead");
        assertApi(() -> service.delete(String.valueOf(LOG_ID), OTHER), HttpStatus.BAD_REQUEST);
    }

    @Test
    void 작성_중_일지를_다른_담당자가_지우면_403() {
        givenLog("draft");
        givenRole("staff");
        assertApi(() -> service.delete(String.valueOf(LOG_ID), OTHER), HttpStatus.FORBIDDEN);
    }

    @Test
    void 작성자는_작성_중_일지를_지운다() {
        givenLog("draft");
        service.delete(String.valueOf(LOG_ID), AUTHOR);
        verify(repo).deleteLog(LOG_ID);
    }

    @Test
    void 팀장은_남의_작성_중_일지를_지운다() {
        givenLog("draft");
        givenRole("teamLead");
        service.delete(String.valueOf(LOG_ID), OTHER);
        verify(repo).deleteLog(LOG_ID);
    }

    @Test
    void 국장은_남의_작성_중_일지를_지운다() {
        givenLog("draft");
        givenRole("director");
        service.delete(String.valueOf(LOG_ID), OTHER);
        verify(repo).deleteLog(LOG_ID);
    }
}
