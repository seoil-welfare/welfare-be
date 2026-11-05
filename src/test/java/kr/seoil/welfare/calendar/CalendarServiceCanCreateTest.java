package kr.seoil.welfare.calendar;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import kr.seoil.welfare.calendar.CalendarRepository.ClientRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 그날 이용자별 상태의 canCreate 규칙 검사 (CalendarService.clientStatuses, DB 없음).
 * CalendarRepository는 Mockito 가짜로 대신한다.
 */
class CalendarServiceCanCreateTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private CalendarService service;

    @BeforeEach
    void setUp() {
        CalendarRepository repo = mock(CalendarRepository.class);
        // 가상 이용자 1명, 그날 일지 없음 (logId, status, authorId = null)
        when(repo.findClientStatuses(any())).thenReturn(
                List.of(new ClientRow(1L, "가상 이용자", null, null, null, 0)));
        service = new CalendarService(repo);
    }

    @Test
    @DisplayName("오늘은 일지가 없으면 새로 쓸 수 있다")
    void todayCanCreate() {
        assertTrue(service.clientStatuses(TODAY.toString()).get(0).canCreate());
    }

    @Test
    @DisplayName("지난 날도 일지가 없으면 새로 쓸 수 있다")
    void pastCanCreate() {
        assertTrue(service.clientStatuses(TODAY.minusDays(1).toString()).get(0).canCreate());
    }

    @Test
    @DisplayName("내일은 일지가 없어도 새로 쓸 수 없다")
    void tomorrowCannotCreate() {
        assertFalse(service.clientStatuses(TODAY.plusDays(1).toString()).get(0).canCreate());
    }
}
