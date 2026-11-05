package kr.seoil.welfare.auth;

import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.staff.ChangeRoleRequest;
import kr.seoil.welfare.staff.StaffDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 직원 역할 변경·관리 권한 이양·계정 추가 규칙 검사 (AuthService, DB 없음).
 * staff 테이블은 FakeStaffStore로 대신한다.
 */
class AuthServiceStaffRoleTest {

    // 1 국장, 2 팀장, 3 담당자, 4 담당자(사용 중지), 5 팀장
    private FakeStaffStore store;
    private AuthService service;

    @BeforeEach
    void setUp() {
        store = new FakeStaffStore()
                .put(1, "director", true)
                .put(2, "teamLead", true)
                .put(3, "staff", true)
                .put(4, "staff", false)
                .put(5, "teamLead", true);
        PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "hash:" + raw;
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                return encoded.equals("hash:" + raw);
            }
        };
        service = new AuthService(store.authRepository(), store.queries(), encoder);
    }

    /** 던진 ApiException의 상태·code 확인 후 돌려준다 */
    private static ApiException expectError(int status, String code, Executable call) {
        ApiException e = assertThrows(ApiException.class, call);
        assertEquals(status, e.getStatus().value(), e.getMessage());
        assertEquals(code, e.getCode());
        return e;
    }

    private static ChangeRoleRequest role(String role) {
        return new ChangeRoleRequest(role);
    }

    // ---- 역할 변경 ----

    @Test
    @DisplayName("팀장이 담당자를 팀장으로 올린다 (position도 팀장)")
    void changeRole_leadPromotesStaff() {
        StaffDto result = service.changeRole(2, "3", role("teamLead"));
        assertEquals("teamLead", result.role());
        assertEquals("팀장", result.position());
        assertEquals("teamLead", store.get(3).role());
    }

    @Test
    @DisplayName("팀장이 다른 팀장을 담당자로 내린다 (position 사회복지사)")
    void changeRole_leadDemotesLead() {
        StaffDto result = service.changeRole(2, "5", role("staff"));
        assertEquals("staff", result.role());
        assertEquals("사회복지사", result.position());
    }

    @Test
    @DisplayName("국장도 역할을 바꿀 수 있다")
    void changeRole_directorCanChange() {
        assertEquals("teamLead", service.changeRole(1, "3", role("teamLead")).role());
    }

    @Test
    @DisplayName("담당자가 요청하면 403")
    void changeRole_actorStaff_403() {
        ApiException e = expectError(403, "FORBIDDEN", () -> service.changeRole(3, "5", role("staff")));
        assertEquals("역할 변경은 팀장·국장만 할 수 있어요.", e.getMessage());
    }

    @Test
    @DisplayName("국장 계정은 대상이 될 수 없다 403")
    void changeRole_targetDirector_403() {
        expectError(403, "FORBIDDEN", () -> service.changeRole(2, "1", role("staff")));
        assertEquals("director", store.get(1).role());
    }

    @Test
    @DisplayName("내 계정은 400")
    void changeRole_self_400() {
        expectError(400, "VALIDATION_ERROR", () -> service.changeRole(2, "2", role("staff")));
    }

    @Test
    @DisplayName("국장이 자기 자신을 대상으로 해도 403이 아니라 400")
    void changeRole_selfDirector_400() {
        expectError(400, "VALIDATION_ERROR", () -> service.changeRole(1, "1", role("teamLead")));
    }

    @Test
    @DisplayName("director는 줄 수 없다 400")
    void changeRole_director_400() {
        expectError(400, "VALIDATION_ERROR", () -> service.changeRole(2, "3", role("director")));
        assertEquals(1, store.countRole("director"));
    }

    @Test
    @DisplayName("role이 비었거나 본문이 없으면 400")
    void changeRole_blank_400() {
        expectError(400, "VALIDATION_ERROR", () -> service.changeRole(2, "3", role("")));
        expectError(400, "VALIDATION_ERROR", () -> service.changeRole(2, "3", role(null)));
        expectError(400, "VALIDATION_ERROR", () -> service.changeRole(2, "3", null));
    }

    @Test
    @DisplayName("대소문자가 다르면 400, 앞뒤 공백은 허용")
    void changeRole_caseAndTrim() {
        expectError(400, "VALIDATION_ERROR", () -> service.changeRole(2, "3", role("TeamLead")));
        assertEquals("teamLead", service.changeRole(2, "3", role(" teamLead ")).role());
    }

    @Test
    @DisplayName("사용 중지된 계정은 400")
    void changeRole_targetInactive_400() {
        expectError(400, "VALIDATION_ERROR", () -> service.changeRole(2, "4", role("teamLead")));
    }

    @Test
    @DisplayName("없는 직원·숫자 아닌 번호는 404")
    void changeRole_notFound_404() {
        expectError(404, "NOT_FOUND", () -> service.changeRole(2, "999", role("teamLead")));
        expectError(404, "NOT_FOUND", () -> service.changeRole(2, "abc", role("teamLead")));
    }

    @Test
    @DisplayName("이미 그 역할이면 바꾸지 않고 지금 정보 그대로")
    void changeRole_sameRole_noUpdate() {
        StaffDto result = service.changeRole(2, "3", role("staff"));
        assertEquals("staff", result.role());
        assertTrue(store.log.isEmpty(), "UPDATE가 없어야 한다: " + store.log);
    }
}
