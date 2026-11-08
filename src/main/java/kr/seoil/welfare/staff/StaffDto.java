package kr.seoil.welfare.staff;

/**
 * 프론트 Staff 타입과 같은 모양의 JSON.
 * id는 DB에서는 숫자지만 프론트는 문자열로 다루므로 문자열로 보낸다.
 */
// 직원 정보를 프론트에 보낼 때 담는 상자 (Spring이 이 객체를 JSON으로 바꿔서 보냄)
public record StaffDto(
        String id,              // 직원 번호 (staff_id)
        String name,            // 이름
        String department,      // 부서
        String position,        // 직위 표기 (사회복지사, 팀장 ...)
        String role,            // staff | teamLead | director
        String loginId,         // 로그인 아이디
        boolean active,         // 사용 중이면 true, 사용 중지된 계정이면 false (staff.is_active)
        String birthDate,       // 생년월일 YYYY-MM-DD (없으면 응답에서 빠짐)
        boolean mustChangePassword  // 임시 비밀번호 상태라 비밀번호를 바꿔야 하면 true (staff.must_change_password)
) {
}