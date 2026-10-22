package kr.seoil.welfare.auth.dto;

/**
 * 비밀번호 변경 요청 JSON (SE-02)
 * { "currentPassword": "demo1234", "newPassword": "welfare2026" }
 *
 * '새 비밀번호 확인'은 프론트에서 검사한다 (명세서 SE-02). 서버는 두 칸만 받는다.
 */
public record ChangePasswordRequest(
        String currentPassword, // 지금 쓰는 비밀번호 (틀리면 400 CURRENT_PASSWORD_MISMATCH)
        String newPassword      // 새 비밀번호 (규칙 위반이면 400 PASSWORD_POLICY)
) {
}
