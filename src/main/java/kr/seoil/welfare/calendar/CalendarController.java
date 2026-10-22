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
 * 달력 화면용 API의 입구.
 * 프론트 달력 탭(CalendarPage)이 보내는 요청을 받아서 로그인을 확인하고,
 * 실제 계산은 CalendarService에 넘긴 뒤 결과를 JSON으로 돌려준다.
 *
 *  GET /api/calendar?from=2026-09-27&to=2026-11-07   날짜별 일지 수 (달력 칸의 "2/3"과 점)
 *  GET /api/calendar/days/2026-10-19                 그날 이용자별 일지 상태 (날짜를 눌렀을 때 아래 목록)
 *
 * 일지 내용 자체는 여기서 주지 않는다. 목록에서 이용자를 누르면 프론트가 GET /api/diaries/:id 로 따로 불러온다.
 */
@RestController                         // 이 클래스의 메서드가 돌려주는 값을 JSON으로 바꿔 응답
@RequestMapping("/api/calendar")        // 이 클래스의 모든 주소 앞에 /api/calendar 를 붙임
public class CalendarController {

    // 달력 규칙과 계산을 맡는 객체. 서버가 켜질 때 Spring이 만들어서 아래 생성자로 넣어 준다
    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    /**
     * GET /api/calendar?from=...&to=...
     * 달력 한 화면(6주)의 날짜별 숫자를 돌려준다. 프론트가 월을 바꿀 때마다 부른다.
     * 흐름: 로그인 확인 → CalendarService.dayCounts → CalendarRepository.countByDate (DB)
     * 응답: CalendarDayCount 목록. 일지가 없는 날은 빠진다
     */
    @GetMapping
    public List<CalendarDayCount> dayCounts(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,  // 로그인한 직원 번호 (세션에서 꺼냄, 없으면 null)
            @RequestParam(required = false) String from,         // 달력 첫날 YYYY-MM-DD (포함)
            @RequestParam(required = false) String to) {         // 달력 마지막 날 YYYY-MM-DD (포함)
        SessionStaff.require(staffId);                           // 로그인 안 했으면 여기서 401로 끝남
        return calendarService.dayCounts(from, to);              // 날짜 검사와 계산은 Service에서
    }

    /**
     * GET /api/calendar/days/{date}
     * 달력에서 날짜를 눌렀을 때 아래 목록에 보여 줄 이용자별 상태를 돌려준다.
     * 흐름: 로그인 확인 → CalendarService.clientStatuses → CalendarRepository.findClientStatuses (DB)
     * 응답: CalendarClientStatus 목록. 이용 중인 이용자 전원이 나온다
     */
    @GetMapping("/days/{date}")
    public List<CalendarClientStatus> clientStatuses(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,  // 로그인한 직원 번호
            @PathVariable String date) {                         // 주소의 {date} 자리 값 (예: 2026-10-19)
        SessionStaff.require(staffId);                           // 로그인 안 했으면 401
        return calendarService.clientStatuses(date);
    }
}
