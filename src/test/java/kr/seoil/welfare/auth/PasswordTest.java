package kr.seoil.welfare.auth;

import kr.seoil.welfare.common.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 비밀번호 해시·규칙 검사 (DB와 서버 없이 도는 테스트).
 *
 * 특히 첫 테스트가 중요하다. 가상 데이터의 데모 계정 해시가 정말 demo1234인지 확인한다.
 * 이게 깨지면 데모 계정으로 로그인이 안 되는데, 서버를 켜 보기 전에는 알아채기 어렵다.
 */
class PasswordTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    /** db/dev/V2__dev_seed.sql 의 데모 계정 5개가 모두 쓰는 해시 */
    private static final String SEED_HASH =
            "$2a$10$CS5blBJo5wROAHWXkE7zXumKFskFpKe8B8BRZI/iseMfS0EBEhRHm";

    @Test
    @DisplayName("가상 데이터의 데모 계정 해시는 demo1234와 맞는다")
    void seedHashMatchesDemoPassword() {
        assertTrue(encoder.matches("demo1234", SEED_HASH));
        assertFalse(encoder.matches("demo12345", SEED_HASH));
    }

    @Test
    @DisplayName("같은 비밀번호라도 해시는 매번 달라지고, 둘 다 확인은 된다")
    void hashIsSaltedButVerifiable() {
        String a = encoder.encode("welfare2026");
        String b = encoder.encode("welfare2026");
        assertFalse(a.equals(b));                       // 소금(salt) 때문에 문자열은 다르다
        assertTrue(encoder.matches("welfare2026", a));
        assertTrue(encoder.matches("welfare2026", b));
    }

    @Test
    @DisplayName("규칙에 맞는 비밀번호는 통과한다")
    void allowsValidPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.check("welfare2026"));
        assertDoesNotThrow(() -> PasswordPolicy.check("a1b2c3d4"));
    }

    @Test
    @DisplayName("짧거나 영문·숫자가 빠지거나 공백이 있으면 PASSWORD_POLICY")
    void rejectsWeakPassword() {
        assertEquals("PASSWORD_POLICY", policyErrorOf(null));
        assertEquals("PASSWORD_POLICY", policyErrorOf(""));
        assertEquals("PASSWORD_POLICY", policyErrorOf("ab1"));              // 8자 미만
        assertEquals("PASSWORD_POLICY", policyErrorOf("abcdefgh"));         // 숫자 없음
        assertEquals("PASSWORD_POLICY", policyErrorOf("12345678"));         // 영문 없음
        assertEquals("PASSWORD_POLICY", policyErrorOf("abc 1234"));         // 공백
        assertEquals("PASSWORD_POLICY", policyErrorOf("a1".repeat(40)));    // 72바이트 초과
    }

    @Test
    @DisplayName("권한에 따라 직위 표기가 정해진다 (명세서 SF-02)")
    void positionFollowsRole() {
        assertEquals("사회복지사", AuthService.positionOf("staff"));
        assertEquals("팀장", AuthService.positionOf("teamLead"));
        assertEquals("국장", AuthService.positionOf("director"));
    }

    private static String policyErrorOf(String password) {
        ApiException e = assertThrows(ApiException.class, () -> PasswordPolicy.check(password));
        return e.getCode();
    }
}
