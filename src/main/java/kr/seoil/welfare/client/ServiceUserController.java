package kr.seoil.welfare.client;

import java.util.List;

import kr.seoil.welfare.auth.SessionStaff;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 이용자 API — 규칙은 ServiceUserService에 있다.
 *
 *  GET  /api/service-users              목록 (일지 작성 화면의 '대상 이용자' 선택 등)
 *  POST /api/service-users              등록
 *  PUT  /api/service-users/{id}         수정
 *  POST /api/service-users/{id}/close   이용 종결 (삭제 아님)
 */
@RestController
@RequestMapping("/api/service-users")
public class ServiceUserController {

    private final ServiceUserService serviceUserService;

    public ServiceUserController(ServiceUserService serviceUserService) {
        this.serviceUserService = serviceUserService;
    }

    @GetMapping
    public List<ServiceUserDto> list(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId) {
        SessionStaff.require(staffId);                          // 로그인 안 했으면 401
        return serviceUserService.list();
    }

    @PostMapping
    public ResponseEntity<ServiceUserDto> create(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                                 @RequestBody ServiceUserInput body) {
        SessionStaff.require(staffId);
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceUserService.create(body));   // 201
    }

    @PutMapping("/{id}")
    public ServiceUserDto update(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                 @PathVariable String id,
                                 @RequestBody ServiceUserInput body) {
        SessionStaff.require(staffId);
        return serviceUserService.update(id, body);
    }

    @PostMapping("/{id}/close")
    public ServiceUserDto close(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                @PathVariable String id) {
        return serviceUserService.close(id, SessionStaff.require(staffId));
    }

    @PostMapping("/{id}/reopen")
    public ServiceUserDto reopen(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                 @PathVariable String id) {
        return serviceUserService.reopen(id, SessionStaff.require(staffId));
    }
}
