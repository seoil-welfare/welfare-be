package kr.seoil.welfare.diary;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.Catalog.Item;
import kr.seoil.welfare.diary.dto.SaveDiaryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 새 일지 날짜 규칙 검사 (DiaryService 규칙 10, DB 없음).
 * DiaryRepository와 Catalog는 Mockito 가짜로 대신한다.
 */
class DiaryServiceDateTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private DiaryRepository repo;
    private DiaryService service;

    @BeforeEach
    void setUp() {
        repo = mock(DiaryRepository.class);
        Catalog catalog = mock(Catalog.class);
        // 날씨·컨디션 이름 → 항목 변환은 이 테스트의 관심사가 아니라 아무 항목이나 돌려준다
        when(catalog.byLabel(any(), any())).thenReturn(new Item((short) 1, "x", "가상 항목", false, 1));
        service = new DiaryService(repo, catalog, mock(AuthService.class), Clock.system(ZoneId.of("Asia/Seoul")));
    }

    /** 날짜만 다르게 한 작성 중 일지 저장 요청 (가상 이용자 1번) */
    private static SaveDiaryRequest draftOn(LocalDate date) {
        return new SaveDiaryRequest("1", date.toString(), "draft", "맑음", "양호", null,
                List.of(), null, null);
    }

    @Test
    @DisplayName("내일 날짜로 새 일지를 쓰면 400이고 아무것도 저장하지 않는다")
    void tomorrowIsRejected() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.create(draftOn(TODAY.plusDays(1)), 1L));

        assertEquals(400, e.getStatus().value());
        assertEquals("오늘 이후 날짜에는 일지를 쓸 수 없어요.", e.getMessage());
        verify(repo, never()).insertLog(anyLong(), any(), any(), anyShort(), anyShort(),
                any(), any(), anyLong());
    }

    @Test
    @DisplayName("오늘 날짜는 날짜 검사를 통과한다")
    void todayPassesDateCheck() {
        // 이용자가 없다고 답하게 해서, 날짜 검사 다음 단계(이용자 확인)까지 갔는지만 본다
        when(repo.clientExists(1L)).thenReturn(false);

        ApiException e = assertThrows(ApiException.class,
                () -> service.create(draftOn(TODAY), 1L));

        assertEquals("없는 이용자예요.", e.getMessage());
    }
}
