package kr.seoil.welfare.diary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActivityRulesTest {
    DiaryRepository repo;
    DiaryService service;
    @BeforeEach void setup() {
        repo = mock(DiaryRepository.class);
        Catalog catalog = mock(Catalog.class);
        Map<Short, Catalog.Item> items = new HashMap<>();
        when(catalog.byLabel(any(), any())).thenAnswer(inv -> {
            String name = inv.getArgument(1);
            short id = (short) (Math.abs(name.hashCode() % 30000) + 1);
            Catalog.Item item = new Catalog.Item(id, name, name, "기타".equals(name), 1);
            items.put(id, item);
            return item;
        });
        when(catalog.byId(any(), anyShort())).thenAnswer(inv -> items.get(inv.getArgument(1)));
        service = new DiaryService(repo, catalog, mock(AuthService.class),
                Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneId.of("Asia/Seoul")));
    }
    ActivityDto activity(String start, String end, List<String> reaction) {
        return new ActivityDto(null, start, end, List.of(), null, "활동", List.of(), null, reaction, "내용", List.of(), null, false, List.of());
    }
    SaveDiaryRequest request(ActivityDto... activities) {
        return new SaveDiaryRequest("1", "2026-11-07", "completed", "맑음", "양호", null, List.of(activities), "", null);
    }
    void valid(ActivityDto... activities) {
        // 검사 다음 단계의 존재하지 않는 이용자 오류까지 갔는지 확인한다. DB 저장은 없어야 한다.
        assertThatThrownBy(() -> service.create(request(activities), 1)).hasMessage("없는 이용자예요.");
        verify(repo).clientExists(1);
        verify(repo, never()).insertRecord(anyLong(), any(), any(), any(), anyBoolean());
    }
    @ParameterizedTest @CsvSource({"00:00,00:01", "00:00,24:00", "09:00,10:00", "17:00,18:00", "23:59,24:00",
            "12:15,13:45", "10:00,11:00", "01:01,01:59", "18:30,21:17", "22:45,23:55"})
    void 하루_전체_임의_분_길이_검사통과(String start, String end) { valid(activity(start, end, List.of())); }

    @ParameterizedTest @CsvSource({"12:00,12:00", "13:00,12:00", "24:00,24:00", "25:00,26:00", "10:60,11:00",
            "9:00,10:00", "12:00,24:01", "10:00:30,11:00", "-1:00,10:00", "10:00,25:00"})
    void 잘못된_시간은_저장전에_거절(String start, String end) {
        assertThatThrownBy(() -> service.create(request(activity(start, end, List.of())), 1)).isInstanceOf(ApiException.class);
        verifyNoInteractions(repo);
    }
    @ParameterizedTest @CsvSource({"적극적 참여,안정적 참여", "적극적 참여,부분적 참여", "적극적 참여,참여하지 않음",
            "안정적 참여,적극적 참여", "안정적 참여,부분적 참여", "안정적 참여,참여하지 않음",
            "부분적 참여,적극적 참여", "부분적 참여,안정적 참여", "부분적 참여,참여하지 않음", "참여하지 않음,적극적 참여"})
    void 참여정도_여러개_직접요청_거절(String first, String second) {
        assertThatThrownBy(() -> service.create(request(activity("10:00", "11:00", List.of(first, second, "기타"))), 1))
                .hasMessageContaining("참여 정도는 하나만");
        verifyNoInteractions(repo);
    }
    @ParameterizedTest @ValueSource(strings = {"적극적 참여", "안정적 참여", "부분적 참여", "참여하지 않음"})
    void 참여정도와_기타_허용(String level) { valid(activity("10:00", "11:00", List.of(level, "기타"))); }

    @ParameterizedTest @CsvSource({"23:00,24:00,23:30,24:00", "00:00,24:00,12:00,13:00", "10:00,11:00,10:59,12:00"})
    void 자정포함_겹침_거절(String start, String end, String otherStart, String otherEnd) {
        assertThatThrownBy(() -> service.create(request(activity(start, end, List.of()), activity(otherStart, otherEnd, List.of())), 1))
                .hasMessageContaining("겹쳐요");
        verifyNoInteractions(repo);
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 15, 30, 60, 600, 720, 1020, 1439, 1440})
    void SQL시각_응답_정규화_자정유지(int minute) {
        String time = ActivityTime.format(minute);
        assertThat(ActivityTime.fromSql(time + ":00")).isEqualTo(time);
        assertThat(ActivityTime.parse(time, true)).isEqualTo(minute);
    }
}
