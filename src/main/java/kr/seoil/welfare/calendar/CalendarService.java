package kr.seoil.welfare.calendar;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 달력 규칙. 프론트 diaryService.ts의 dayCompletion과 같게 맞춘다.
 *
 *  - 완료 수에 작성 중 일지는 넣지 않는다
 *  - 분모는 현재 이용 중인 이용자 수 (지난 날짜를 어떻게 셀지는 회의에서 확인)
 *  - 종결된 이용자의 일지는 세지 않는다
 *  - 일지가 하나도 없는 날은 응답에서 뺀다
 *  - 새 일지는 그날 일지가 없는 이용자만 쓸 수 있다 (이용자 1명당 하루 1건)
 */
@Service
public class CalendarService {

    static final int MAX_DAYS = 42;     // 달력 6주

    private final CalendarRepository calendarRepository;

    public CalendarService(CalendarRepository calendarRepository) {
        this.calendarRepository = calendarRepository;
    }

    public List<CalendarDayCount> dayCounts(String from, String to) {
        // TODO 날짜 형식 검사, from > to 또는 MAX_DAYS 초과면 400
        // TODO countByDate + activeClientCount로 응답 만들기
        throw new UnsupportedOperationException("TODO: 날짜별 일지 수");
    }

    public List<CalendarClientStatus> clientStatuses(String date) {
        // TODO 날짜 형식 검사
        // TODO findClientStatuses 결과에 canCreate 붙이기
        throw new UnsupportedOperationException("TODO: 이용자별 일지 상태");
    }
}
