package kr.seoil.welfare.staff;

import java.util.List;

import kr.seoil.welfare.auth.SessionStaff;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * GET /api/staff — 직원 목록 (일지 화면에서 작성자 이름을 보여 줄 때 씀)
 * 계정 추가(POST)는 아직 없음. 담당자가 정해지면 여기에 추가한다.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffQueries staffQueries;

    public StaffController(StaffQueries staffQueries) {
        this.staffQueries = staffQueries;
    }

    @GetMapping
    public List<StaffDto> list(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId) {
        SessionStaff.require(staffId);                          // 로그인 안 했으면 401
        return staffQueries.findAll();
    }
}
