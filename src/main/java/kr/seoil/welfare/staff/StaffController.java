package kr.seoil.welfare.staff;

import java.util.List;

import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.auth.SessionStaff;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 *  GET  /api/staff   직원 목록 (일지 화면에서 작성자 이름을 보여 줄 때 씀)
 *  POST /api/staff   계정 추가 (팀장·국장만, SF-02). 규칙은 AuthService.createStaff
 */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffQueries staffQueries;
    private final AuthService authService;

    public StaffController(StaffQueries staffQueries, AuthService authService) {
        this.staffQueries = staffQueries;
        this.authService = authService;
    }

    @GetMapping
    public List<StaffDto> list(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId) {
        SessionStaff.require(staffId);                          // 로그인 안 했으면 401
        return staffQueries.findAll();
    }

    @PostMapping
    public ResponseEntity<CreatedStaffResponse> create(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                                       @RequestBody CreateStaffRequest body) {
        long actor = SessionStaff.require(staffId);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createStaff(actor, body));   // 201
    }
}
