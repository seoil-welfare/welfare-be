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

    @Transactional(readOnly = true)
    public List<CalendarDayCount> dayCounts(String from, String to) {
        if (blank(from) || blank(to)) {
            throw ApiException.badRequest("from과 to 날짜가 필요해요.");
        }
        LocalDate start = parseDate(from);
        LocalDate end = parseDate(to);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("from이 to보다 늦을 수 없어요.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ApiException.badRequest("한 번에 " + MAX_DAYS + "일까지만 조회할 수 있어요.");
        }

        int target = calendarRepository.activeClientCount();
        List<DayRow> rows = calendarRepository.countByDate(start, end);
        return rows.stream()
                .map(r -> new CalendarDayCount(r.date().toString(), r.completed(), r.draft(), target, r.hasBehavior()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CalendarClientStatus> clientStatuses(String date) {
        return calendarRepository.findClientStatuses(parseDate(date)).stream()
                .map(r -> {
                    boolean hasLog = r.logId() != null;
                    return new CalendarClientStatus(
                            String.valueOf(r.clientId()),
                            r.clientName(),
                            hasLog ? r.status() : "none",
                            hasLog ? String.valueOf(r.logId()) : null,
                            hasLog ? String.valueOf(r.authorId()) : null,
                            r.behaviorCount(),
                            !hasLog);                           // 하루 1건이라 일지가 없을 때만 새로 쓸 수 있음
                })
                .toList();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim());                   // YYYY-MM-DD
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("날짜는 YYYY-MM-DD 형식이어야 해요.");
        }
    }
}
