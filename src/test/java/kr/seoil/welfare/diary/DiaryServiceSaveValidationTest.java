package kr.seoil.welfare.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.Catalog.Item;
import kr.seoil.welfare.diary.dto.ActivityDto;
import kr.seoil.welfare.diary.dto.BehaviorDto;
import kr.seoil.welfare.diary.dto.SaveDiaryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 저장 검사 규칙 10(미래 날짜)·11(도전적 행동 세부 누락) — DB 없이 가짜 저장소로 확인.
 * 검사를 통과하면 그 다음 단계(이용자 확인)까지 가므로, 가짜 저장소가 '없는 이용자'라고 답하게 해 두고
 * "없는 이용자예요." 오류가 나면 '검사를 통과했다'고 본다.
 */
class DiaryServiceSaveValidationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String PASSED = "없는 이용자예요.";          // 검사를 통과해 이용자 확인까지 갔다는 표시
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private DiaryRepository repo;
    private DiaryService service;

    @BeforeEach
    void setUp() {
        repo = mock(DiaryRepository.class);
        Catalog catalog = mock(Catalog.class);
        when(catalog.byLabel(any(), any())).thenAnswer(inv ->
                new Item((short) 1, "x", inv.getArgument(1), false, 1));
        // 세부 코드 → 항목 (oh-는 타해, sh-는 자해 영역, other는 상위 기타)
        when(catalog.behaviorByCode(any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            String area = "other".equals(code) ? "other" : code.startsWith("oh-") ? "otherHarm" : "selfHarm";
            return new Catalog.BehaviorItem((short) 1, code, area, false, 1);
        });
        Clock fixed = Clock.fixed(TODAY.atTime(12, 0).atZone(KST).toInstant(), KST);   // 오늘 = 2026-11-06 (한국 시간)
        service = new DiaryService(repo, catalog, mock(AuthService.class), fixed);
        when(repo.clientExists(anyLong())).thenReturn(false);
    }

    // ---- 도우미 ----

    private static ActivityDto activity(boolean hasBehavior, List<BehaviorDto> behaviors) {
        return new ActivityDto(null, "10:00", "10:30", List.of(), null, "", List.of(), null, List.of(), null,
                List.of(), null, hasBehavior, behaviors);
    }

    private static SaveDiaryRequest request(String date, String status, ActivityDto... activities) {
        return new SaveDiaryRequest("1", date, status, "맑음", "양호", null, List.of(activities), "", null);
    }

    private void assertBadRequest(SaveDiaryRequest req, String message) {
        assertThatThrownBy(() -> service.create(req, 1L)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getMessage()).isEqualTo(message);
        });
        verify(repo, never()).insertLog(anyLong(), any(), any(), anyShort(), anyShort(), any(), any(), anyLong());
    }

    private static short anyShort() {
        return org.mockito.ArgumentMatchers.anyShort();
    }

    // ---- 규칙 10: 미래 날짜 ----

    @Test
    void 내일_날짜는_작성_중이어도_400() {
        assertBadRequest(request("2026-11-07", "draft"), "오늘 이후 날짜에는 일지를 쓸 수 없어요.");
    }

    @Test
    void 내일_날짜는_작성_완료도_400() {
        assertBadRequest(request("2026-11-07", "completed"), "오늘 이후 날짜에는 일지를 쓸 수 없어요.");
    }

    @Test
    void 수정할_때도_미래_날짜면_400_잠금_전에_거절() {
        SaveDiaryRequest req = request("2026-12-01", "draft");
        assertThatThrownBy(() -> service.update("5", req, 1L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getMessage()).isEqualTo("오늘 이후 날짜에는 일지를 쓸 수 없어요."));
        verify(repo, never()).lockLog(anyLong());
    }

    @Test
    void 오늘_날짜는_통과() {
        assertBadRequest(request("2026-11-06", "draft"), PASSED);
    }

    @Test
    void 과거_날짜는_통과() {
        assertBadRequest(request("2026-11-01", "completed"), PASSED);
    }

    // ---- 규칙 11: 도전적 행동 세부 누락 ----

    @Test
    void 완료인데_유형만_있고_세부가_0개면_400() {
        ActivityDto a = activity(true, List.of(new BehaviorDto("selfHarm", List.of(), null)));
        assertBadRequest(request("2026-11-05", "completed", a), "10:00~10:30 활동: 자해의 세부 행동을 골라 주세요.");
    }

    @Test
    void 세부_목록이_null이어도_400() {
        ActivityDto a = activity(true, List.of(new BehaviorDto("otherHarm", null, null)));
        assertBadRequest(request("2026-11-05", "completed", a), "10:00~10:30 활동: 타해의 세부 행동을 골라 주세요.");
    }

    @Test
    void 유형이_여러_개면_세부가_빈_유형_이름이_안내된다() {
        ActivityDto a = activity(true, List.of(
                new BehaviorDto("otherHarm", List.of("oh-1"), null),
                new BehaviorDto("destruction", List.of(), null)));
        assertBadRequest(request("2026-11-05", "completed", a), "10:00~10:30 활동: 파괴행동의 세부 행동을 골라 주세요.");
    }

    @Test
    void 상위_기타는_직접_입력이_비면_400() {
        ActivityDto a = activity(true, List.of(new BehaviorDto("other", List.of(), "  ")));
        assertBadRequest(request("2026-11-05", "completed", a), "10:00~10:30 활동: 기타 행동 내용을 적어 주세요.");
    }

    @Test
    void 둘_이상_걸리면_문장을_이어서_알려_준다() {
        ActivityDto first = activity(true, List.of(
                new BehaviorDto("selfHarm", List.of(), null),
                new BehaviorDto("other", List.of(), null)));
        ActivityDto second = new ActivityDto(null, "11:00", "11:30", List.of(), null, "", List.of(), null,
                List.of(), null, List.of(), null, true, List.of(new BehaviorDto("destruction", List.of(), null)));
        assertBadRequest(request("2026-11-05", "completed", first, second),
                "10:00~10:30 활동: 자해의 세부 행동을 골라 주세요. 10:00~10:30 활동: 기타 행동 내용을 적어 주세요."
                        + " 11:00~11:30 활동: 파괴행동의 세부 행동을 골라 주세요.");
    }

    @Test
    void 작성_중이면_같은_내용도_통과() {
        ActivityDto a = activity(true, List.of(new BehaviorDto("selfHarm", List.of(), null)));
        assertBadRequest(request("2026-11-05", "draft", a), PASSED);
    }

    @Test
    void 완료라도_세부가_1개_이상이면_통과() {
        ActivityDto a = activity(true, List.of(new BehaviorDto("selfHarm", List.of("sh-1"), null)));
        assertBadRequest(request("2026-11-05", "completed", a), PASSED);
    }

    @Test
    void 상위_기타는_직접_입력이_있으면_통과() {
        ActivityDto a = activity(true, List.of(new BehaviorDto("other", List.of(), "소리 지름")));
        assertBadRequest(request("2026-11-05", "completed", a), PASSED);
    }

    @Test
    void 도전적_행동_없음이면_남은_선택이_있어도_검사하지_않는다() {
        ActivityDto a = activity(false, List.of(new BehaviorDto("selfHarm", List.of(), null)));
        assertBadRequest(request("2026-11-05", "completed", a), PASSED);
    }
}
