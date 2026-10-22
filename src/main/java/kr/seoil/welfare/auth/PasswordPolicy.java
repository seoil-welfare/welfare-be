package kr.seoil.welfare.auth;

import java.nio.charset.StandardCharsets;

import kr.seoil.welfare.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 비밀번호 규칙 검사 (명세서 4.2 PASSWORD_POLICY)
 *
 * 규칙은 복지관 확인 전 임시 기준이다. 정해지면 이 파일의 숫자만 바꾸면 된다.
 *   - 8자 이상 (너무 짧으면 쉽게 추측됨)
 *   - 영문자와 숫자를 각각 1자 이상 포함
 *   - 공백 문자 금지 (앞뒤 공백 때문에 로그인이 안 되는 문의가 많다)
 *   - 72바이트 이하 — BCrypt는 72바이트를 넘는 부분을 조용히 버린다.
 *     막지 않으면 "뒤 글자를 아무렇게나 바꿔도 로그인이 되는" 상태가 된다.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    private static final int MAX_BYTES = 72;         // BCrypt가 처리할 수 있는 한계

    private PasswordPolicy() {
    }

    /** 규칙에 안 맞으면 400 PASSWORD_POLICY를 던진다 */
    public static void check(String password) {
        if (password == null || password.isEmpty()) {
            throw violation("비밀번호를 입력해 주세요.");
        }
        if (password.length() < MIN_LENGTH) {
            throw violation("비밀번호는 " + MIN_LENGTH + "자 이상이어야 해요.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw violation("비밀번호가 너무 길어요. 더 짧게 정해 주세요.");
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isWhitespace(c)) {
                throw violation("비밀번호에는 공백을 넣을 수 없어요.");
            }
            if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                hasLetter = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw violation("비밀번호에는 영문자와 숫자를 함께 넣어 주세요.");
        }
    }

    private static ApiException violation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", message);
    }
}
