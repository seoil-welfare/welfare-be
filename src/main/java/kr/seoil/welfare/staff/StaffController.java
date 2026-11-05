package kr.seoil.welfare.staff;

import java.util.List;

import jakarta.servlet.http.HttpSession;
import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.auth.SessionStaff;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 *  GET  /api/staff   직원 목록 (일지 화면에서 작성자 이름을 보여 줄 때 씀)
 *  POST /api/staff   계정 추가 (팀장·국장만, SF-02). 규칙은 AuthService.createStaff
 *  POST /api/staff/{id}/deactivate   계정 사용 중지 (팀장·국장만). 규칙은 AuthService.setStaffActive
 *  POST /api/staff/{id}/activate     계정 다시 사용 (팀장·국장만)
 *  POST /api/staff/{id}/role         역할 변경 staff ↔ teamLead (팀장·국장만). 규칙은 AuthService.changeRole
 *  POST /api/staff/{id}/transfer-director   관리 권한 이양 (국장만). 규칙은 AuthService.transferDirector
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

    // POST 요청이 오면 실행: 계정 사용 중지 (삭제가 아님, is_active = false)
    @PostMapping("/{id}/deactivate") //
    public StaffDto deactivate(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                               @PathVariable String id) {          // 사용 중지할 직원 번호
        long actor = SessionStaff.require(staffId);              // 로그인 확인 + 요청한 직원 번호
        return authService.setStaffActive(actor, id, false);
    }

    // POST 요청이 오면 실행: 사용 중지된 계정을 다시 사용
    @PostMapping("/{id}/activate")
    public StaffDto activate(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                             @PathVariable String id) {            // 다시 사용할 직원 번호
        long actor = SessionStaff.require(staffId);
        return authService.setStaffActive(actor, id, true);
    }

    // POST 요청이 오면 실행: 담당자 ↔ 팀장 역할 변경
    @PostMapping("/{id}/role")
    public StaffDto changeRole(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                               @PathVariable String id,            // 역할을 바꿀 직원 번호
                               @RequestBody(required = false) ChangeRoleRequest body) {   // 본문이 없으면 서비스에서 400
        long actor = SessionStaff.require(staffId);
        return authService.changeRole(actor, id, body);
    }

    // POST 요청이 오면 실행: 국장이 관리 권한(국장 역할)을 다른 직원에게 넘김
    @PostMapping("/{id}/transfer-director")
    public TransferDirectorResponse transferDirector(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                                     @PathVariable String id,      // 국장을 받을 직원 번호
                                                     @RequestBody(required = false) TransferDirectorRequest body,
                                                     HttpSession session) {
        long actor = SessionStaff.require(staffId);
        TransferDirectorResponse result = authService.transferDirector(actor, id, body);
        if (!result.previous().active()) {                         // 내 계정을 사용 중지했으면 로그인도 끊는다
            session.invalidate();
        }
        return result;
    }
}