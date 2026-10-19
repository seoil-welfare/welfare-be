package kr.seoil.welfare.calendar;

import java.util.List;

import kr.seoil.welfare.auth.SessionStaff;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 달력 API
 *
 *  GET /api/calendar?from=2026-09-27&to=2026-11-07   날짜별 일지 수
 *  GET /api/calendar/days/2026-10-19                 그날 이용자별 일지 상태
 *
 * 일지 내용은 GET /api/diaries/:id 에서 본다.
 */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    public List<CalendarDayCount> dayCounts(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
            @RequestParam(required = false) String from,         // 달력 첫날 (포함)
            @RequestParam(required = false) String to) {         // 달력 마지막 날 (포함)
        SessionStaff.require(staffId);                           // 로그인 안 했으면 401
        return calendarService.dayCounts(from, to);
    }

    @GetMapping("/days/{date}")
    public List<CalendarClientStatus> clientStatuses(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
            @PathVariable String date) {
        SessionStaff.require(staffId);
        return calendarService.clientStatuses(date);
    }
}
