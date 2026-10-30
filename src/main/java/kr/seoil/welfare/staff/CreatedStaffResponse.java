package kr.seoil.welfare.staff;

/**
 * POST /api/staff 응답 JSON — 프론트 Staff 모양 + 임시 비밀번호.
 *
 * 임시 비밀번호는 이 응답에서 한 번만 보여 준다 (DB에는 해시만 저장).
 * 초기 비밀번호 전달 방법은 아직 미정(명세서 SF-02)이라, 지금 프론트는 이 값을 화면에 보여 주지 않는다.
 */
public record CreatedStaffResponse(
        String id,
        String name,
        String department,
        String position,
        String role,
        String loginId,
        String temporaryPassword    // 새 계정의 임시 비밀번호 (첫 로그인 후 비밀번호 변경 권장)
) {

    public static CreatedStaffResponse of(StaffDto staff, String temporaryPassword) {
        return new CreatedStaffResponse(staff.id(), staff.name(), staff.department(), staff.position(),
                staff.role(), staff.loginId(), temporaryPassword);
    }
}
