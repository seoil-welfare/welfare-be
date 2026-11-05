package kr.seoil.welfare.auth.dto;

/**
 * 회원가입 요청 JSON
 * { "loginId": "hong.gildong", "password": "welfare2026", "name": "홍길동", "department": "주간활동지원팀",
 *   "birthDate": "1995-03-14" }
 *
 * department는 안 보내면 '주간활동지원팀'이 된다 (명세서 SF-02의 기본값).
 * birthDate는 선택이다. 안 보내면 비워 둔다 (화면에 입력란이 생기기 전까지).
 * role(권한)과 position(직위)은 요청으로 받지 않는다 — 이유는 AuthService.signup 주석 참고.
 */
public record SignupRequest(
        String loginId,         // 로그인 아이디 (중복이면 409 DUPLICATE_LOGIN_ID)
        String password,        // 비밀번호 원문 (BCrypt 해시로 바꿔 저장)
        String name,            // 담당자 이름
        String department,      // 소속 팀. 비어 있으면 기본값
        String birthDate        // 생년월일 YYYY-MM-DD (선택)
) {
}