package kr.seoil.welfare.staff;

/**
 * 프론트 Staff 타입과 같은 모양의 JSON.
 * id는 DB에서는 숫자지만 프론트는 문자열로 다루므로 문자열로 보낸다.
 */
public record StaffDto(
        String id,              // 직원 번호 (staff_id)
        String name,            // 이름
        String department,      // 부서
        String position,        // 직위 표기 (사회복지사, 팀장 ...)
        String role,            // staff | teamLead | director
        String loginId          // 로그인 아이디
) {
}
