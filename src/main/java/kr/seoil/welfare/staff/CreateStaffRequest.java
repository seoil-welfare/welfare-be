package kr.seoil.welfare.staff;

/**
 * POST /api/staff 요청 JSON (프론트 StaffInput과 같은 모양)
 * position은 받지만 쓰지 않는다. 직위는 role에서 정해진다 (명세서 SF-02).
 */
public record CreateStaffRequest(
        String name,            // 이름 (필수)
        String loginId,         // 로그인 아이디 (필수, 영문 소문자·숫자·. _ - 4~50자)
        String department,      // 부서 (비우면 '주간활동지원팀')
        String position,        // 무시 — role로 정한다
        String role             // staff | teamLead | director
) {
}
