package kr.seoil.welfare.stats;

import kr.seoil.welfare.auth.SessionStaff;
import kr.seoil.welfare.common.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 통계 API (명세서 5.2 제안 — 서버 집계)
 *
 *  GET /api/stats/summary?userId=1&unit=month&year=2026&month=10
 *  GET /api/stats/summary?userId=1&unit=week&year=2026&month=10&week=2
 *  GET /api/stats/summary?userId=1&unit=quarter&year=2026&quarter=3
 *  GET /api/stats/summary?userId=1&unit=year&year=2026
 *
 * 프론트 통계 화면 4개(useStatsSummary)가 이 응답 하나로 그린다. 일지 수백 건 대신 숫자만 받는다.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/summary")
    public StatsSummaryResponse summary(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
            @RequestParam(required = false) String userId,       // 이용자 번호 (필수)
            @RequestParam(required = false) String unit,         // week | month | quarter | year
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) Integer week) {
        SessionStaff.require(staffId);                           // 로그인 안 했으면 401
        if (userId == null || userId.isBlank()) {
            throw ApiException.badRequest("userId가 필요해요.");
        }
        long clientId;
        try {
            clientId = Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("이용자 번호가 올바르지 않아요.");
        }
        return statsService.summary(clientId, statsService.toPeriod(unit, year, month, quarter, week));
    }
}
