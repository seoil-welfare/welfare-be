package kr.seoil.welfare.auth.dto;

/**
 * 로그인 요청 JSON (LG-01, LG-02)
 * { "loginId": "demo.staff1", "password": "demo1234", "autoLogin": true }
 *
 * autoLogin은 체크박스 값이다. 안 보내면 null이 되고, null은 '체크 안 함'으로 본다.
 */
public record LoginRequest(
        String loginId,         // 로그인 아이디
        String password,        // 비밀번호 원문 (해시는 서버에서 비교만 하고 저장하지 않음)
        Boolean autoLogin       // 자동 로그인 체크 여부 (세션 유지 기간을 길게 잡는다)
) {
}
