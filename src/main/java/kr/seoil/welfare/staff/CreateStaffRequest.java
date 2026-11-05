package kr.seoil.welfare.staff;

/**
 * POST /api/staff 요청 JSON (프론트 StaffInput과 같은 모양)
 * position은 받지만 쓰지 않는다. 계정은 항상 담당자(사회복지사)로 만든다 (명세서 SF-02).
 */
public record CreateStaffRequest(
        String name,            // 이름 (필수)
        String loginId,         // 로그인 아이디 (필수, 영문 소문자·숫자·. _ - 4~50자)
        String department,      // 부서 (비우면 '주간활동지원팀')
        String position,        // 무시 — role로 정한다
        String role,            // 비우거나 staff만 (그 밖의 값은 400)
        String birthDate        // 생년월일 YYYY-MM-DD (선택)
) {
}