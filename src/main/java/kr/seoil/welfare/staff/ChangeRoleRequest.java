package kr.seoil.welfare.staff;

/**
 * POST /api/staff/{id}/role 요청 JSON
 * StaffController.changeRole → AuthService.changeRole 로 넘어간다.
 */
public record ChangeRoleRequest(
        String role             // staff | teamLead (director는 관리 권한 이양으로만)
) {
}
