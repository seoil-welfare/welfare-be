package kr.seoil.welfare.calendar;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

import kr.seoil.welfare.calendar.CalendarRepository.DayRow;
import kr.seoil.welfare.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 달력 규칙을 처리하는 곳.
 * CalendarController가 받은 요청 값을 검사하고, CalendarRepository에서 DB 결과를 받아
 * 프론트가 쓸 응답 모양(CalendarDayCount, CalendarClientStatus)으로 바꾼다.
 *
 * 규칙은 프론트 diaryService.ts의 dayCompletion과 같게 맞춘다.
 *  - 완료 수에 작성 중 일지는 넣지 않는다
 *  - 분모는 현재 이용 중인 이용자 수 (지난 날짜를 어떻게 셀지는 회의에서 확인)
 *  - 종결된 이용자의 일지는 세지 않는다
 *  - 일지가 하나도 없는 날은 응답에서 뺀다
 *  - 새 일지는 그날 일지가 없는 이용자만 쓸 수 있다 (이용자 1명당 하루 1건)
 */
@Service                                // 서버가 켜질 때 Spring이 이 객체를 하나 만들어 Controller에 넣어 줌
public class CalendarService {

    static final int MAX_DAYS = 42;     // 한 번에 조회할 수 있는 최대 날짜 수 (달력 6주)

    // DB 조회를 맡는 객체. Spring이 생성자로 넣어 준다
    private final CalendarRepository calendarRepository;

    public CalendarService(CalendarRepository calendarRepository) {
        this.calendarRepository = calendarRepository;
    }

    /**
     * 날짜별 일지 수. CalendarController.dayCounts(GET /api/calendar)가 부른다.
     * from, to를 검사한 뒤 Repository에서 날짜별 숫자와 대상 이용자 수를 받아 합친다.
     * 잘못된 값이면 ApiException을 던지고, GlobalExceptionHandler가 400 응답으로 바꾼다.
     */
    @Transactional(readOnly = true)     // 아래 두 번의 DB 조회를 하나의 읽기 작업으로 묶음 (중간에 데이터가 바뀌어도 서로 어긋나지 않게)
    public List<CalendarDayCount> dayCounts(String from, String to) {
        if (blank(from) || blank(to)) {
            throw ApiException.badRequest("from과 to 날짜가 필요해요.");
        }
        LocalDate start = parseDate(from);                      // 글자 "2026-10-01" → 날짜
        LocalDate end = parseDate(to);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("from이 to보다 늦을 수 없어요.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {   // 양 끝 날짜를 포함하므로 +1
            throw ApiException.badRequest("한 번에 " + MAX_DAYS + "일까지만 조회할 수 있어요.");
        }

        int target = calendarRepository.activeClientCount();    // 분모. 모든 날짜에 같은 값이라 한 번만 셈
        List<DayRow> rows = calendarRepository.countByDate(start, end);
        return rows.stream()                                    // DB 결과 한 줄 → 응답 한 칸
                .map(r -> new CalendarDayCount(r.date().toString(), r.completed(), r.draft(), target, r.hasBehavior()))
                .toList();
    }

    /**
     * 그날 이용자별 일지 상태. CalendarController.clientStatuses(GET /api/calendar/days/{date})가 부른다.
     * Repository 결과에 상태 이름(none)과 새로 쓸 수 있는지(canCreate)를 붙여서 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<CalendarClientStatus> clientStatuses(String date) {
        return calendarRepository.findClientStatuses(parseDate(date)).stream()
                .map(r -> {
                    boolean hasLog = r.logId() != null;         // 그날 일지가 있는지
                    return new CalendarClientStatus(
                            String.valueOf(r.clientId()),
                            r.clientName(),
                            hasLog ? r.status() : "none",       // DB에는 '미작성' 상태가 없어서 여기서 이름을 붙임
                            hasLog ? String.valueOf(r.logId()) : null,
                            hasLog ? String.valueOf(r.authorId()) : null,
                            r.behaviorCount(),
                            !hasLog);                           // 하루 1건이라 일지가 없을 때만 새로 쓸 수 있음
                })
                .toList();
    }

    /** 값이 없거나 공백뿐인지 */
    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** "YYYY-MM-DD" 글자를 날짜로 바꾼다. 형식이 틀리면 400 */
    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("날짜는 YYYY-MM-DD 형식이어야 해요.");
        }
    }
}
