package kr.seoil.welfare.auth;

import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.staff.ChangeRoleRequest;
import kr.seoil.welfare.staff.CreateStaffRequest;
import kr.seoil.welfare.staff.StaffDto;
import kr.seoil.welfare.staff.TransferDirectorRequest;
import kr.seoil.welfare.staff.TransferDirectorResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // ---- 관리 권한 이양 ----

    private static TransferDirectorRequest prev(String previousRole) {
        return new TransferDirectorRequest(previousRole);
    }

    @Test
    @DisplayName("이양 teamLead: 원래 국장은 팀장으로 남고 받은 사람이 국장, 국장은 1명")
    void transfer_keepTeamLead() {
        TransferDirectorResponse result = service.transferDirector(1, "2", prev("teamLead"));
        assertEquals("director", result.director().role());
        assertEquals("국장", result.director().position());
        assertEquals("2", result.director().id());
        assertEquals("teamLead", result.previous().role());
        assertEquals("팀장", result.previous().position());
        assertTrue(result.previous().active());
        assertEquals(1, store.countRole("director"));
    }

    @Test
    @DisplayName("이양 deactivate: 원래 국장은 팀장 + 사용 중지")
    void transfer_deactivate() {
        TransferDirectorResponse result = service.transferDirector(1, "3", prev("deactivate"));
        assertEquals("teamLead", result.previous().role());
        assertFalse(result.previous().active());
        assertFalse(store.get(1).active());
        assertEquals("director", store.get(3).role());
        assertEquals(1, store.countRole("director"));
    }

    @Test
    @DisplayName("요청한 국장 줄을 먼저 잠그고, 원래 국장을 내린 다음 대상을 올린다")
    void transfer_order_lockThenDemoteFirst() {
        service.transferDirector(1, "2", prev("deactivate"));
        assertEquals(List.of("lock:1", "role:1:teamLead", "active:1:false", "role:2:director"), store.log);
    }

    @Test
    @DisplayName("previousRole이 없거나 본문이 없으면 teamLead로 처리")
    void transfer_nullPreviousRole_defaultsTeamLead() {
        TransferDirectorResponse result = service.transferDirector(1, "2", prev(null));
        assertTrue(result.previous().active());
        assertEquals("teamLead", result.previous().role());

        TransferDirectorResponse back = service.transferDirector(2, "1", null);
        assertEquals("director", back.director().role());
        assertEquals(1, store.countRole("director"));
    }

    @Test
    @DisplayName("국장이 아니면 403 (팀장·담당자)")
    void transfer_actorNotDirector_403() {
        ApiException e = expectError(403, "FORBIDDEN", () -> service.transferDirector(2, "3", prev("teamLead")));
        assertEquals("관리 권한 이양은 국장만 할 수 있어요.", e.getMessage());
        expectError(403, "FORBIDDEN", () -> service.transferDirector(3, "2", prev("teamLead")));
        assertEquals("director", store.get(1).role());
    }

    @Test
    @DisplayName("요청한 국장이 사용 중지 상태면 403 ACCOUNT_DISABLED")
    void transfer_actorInactive_403() {
        store.put(1, "director", false);
        expectError(403, "ACCOUNT_DISABLED", () -> service.transferDirector(1, "2", prev("teamLead")));
    }

    @Test
    @DisplayName("대상이 나·사용 중지 계정이면 400, 아무것도 안 바뀜")
    void transfer_badTarget_400() {
        expectError(400, "VALIDATION_ERROR", () -> service.transferDirector(1, "1", prev("teamLead")));
        expectError(400, "VALIDATION_ERROR", () -> service.transferDirector(1, "4", prev("teamLead")));
        assertEquals("director", store.get(1).role());
        assertFalse(store.log.stream().anyMatch(s -> s.startsWith("role:") || s.startsWith("active:")));
    }

    @Test
    @DisplayName("previousRole 값이 이상하면 400")
    void transfer_badPreviousRole_400() {
        expectError(400, "VALIDATION_ERROR", () -> service.transferDirector(1, "2", prev("staff")));
        expectError(400, "VALIDATION_ERROR", () -> service.transferDirector(1, "2", prev("director")));
    }

    @Test
    @DisplayName("대상 없음·숫자 아님은 404")
    void transfer_notFound_404() {
        expectError(404, "NOT_FOUND", () -> service.transferDirector(1, "999", prev("teamLead")));
        expectError(404, "NOT_FOUND", () -> service.transferDirector(1, "abc", prev("teamLead")));
    }

    @Test
    @DisplayName("담당자에게도 넘길 수 있다")
    void transfer_targetStaff_ok() {
        assertEquals("director", service.transferDirector(1, "3", prev("teamLead")).director().role());
    }

    // ---- 계정 추가 ----

    private static CreateStaffRequest newStaff(String loginId, String role) {
        return new CreateStaffRequest("새 직원", loginId, null, null, role, null);
    }

    @Test
    @DisplayName("계정 추가: role이 비었거나 staff면 담당자로 만든다")
    void create_roleBlankOrStaff_staff() {
        assertEquals("staff", service.createStaff(2, newStaff("new.a", null)).role());
        assertEquals("staff", service.createStaff(2, newStaff("new.b", "")).role());
        assertEquals("staff", service.createStaff(1, newStaff("new.c", "staff")).role());
        assertEquals("사회복지사", store.get(store.rows.size()).position());
    }

    @Test
    @DisplayName("계정 추가: teamLead·director·잘못된 값은 400 (국장이 요청해도)")
    void create_otherRole_400() {
        ApiException e = expectError(400, "VALIDATION_ERROR", () -> service.createStaff(1, newStaff("new.d", "teamLead")));
        assertEquals("계정 추가는 담당자로만 할 수 있어요. 팀장은 역할 변경으로 바꿔 주세요.", e.getMessage());
        expectError(400, "VALIDATION_ERROR", () -> service.createStaff(1, newStaff("new.e", "director")));
        expectError(400, "VALIDATION_ERROR", () -> service.createStaff(2, newStaff("new.f", "boss")));
        assertEquals(5, store.rows.size());
        assertEquals(1, store.countRole("director"));
    }

    @Test
    @DisplayName("국장 계정은 사용 중지 API로 막을 수 없고 이양 deactivate로만 중지된다")
    void directorDeactivatedOnlyByTransfer() {
        expectError(403, "FORBIDDEN", () -> service.setStaffActive(2, "1", false));   // 팀장이 국장을
        expectError(400, "VALIDATION_ERROR", () -> service.setStaffActive(1, "1", false));   // 국장 본인
        assertTrue(store.get(1).active());

        service.transferDirector(1, "2", prev("deactivate"));
        assertFalse(store.get(1).active());
    }

    @Test
    @DisplayName("이양 뒤 원래 국장은 더 이상 이양할 수 없다 (두 번 눌러도 국장 1명)")
    void transfer_twice_secondForbidden() {
        service.transferDirector(1, "2", prev("teamLead"));
        expectError(403, "FORBIDDEN", () -> service.transferDirector(1, "3", prev("teamLead")));
        assertEquals(1, store.countRole("director"));
    }
}
