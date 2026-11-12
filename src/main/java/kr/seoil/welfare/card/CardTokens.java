package kr.seoil.welfare.card;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 공유 링크 토큰과 보호자 확인 쿠키 (명세 7장)
 *
 *  - 토큰: SecureRandom 32바이트 → Base64URL. 추측할 수 없다
 *  - DB에는 SHA-256 해시(찾기용)와 암호문(같은 링크 다시 복사용)만 둔다. 원문은 저장하지 않는다
 *  - 암호화: AES-256-GCM. 키는 서버 비밀값(card.secret)에서 만든다
 *  - 확인 쿠키: "만료시각.서명" — 서명은 HMAC-SHA256(토큰 해시 + 만료시각). DB를 보지 않고 확인한다
 */
@Component
public class CardTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] encKey;
    private final byte[] macKey;

    public CardTokens(@Value("${card.secret}") String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("card.secret(CARD_SECRET)은 16자 이상이어야 해요.");
        }
        // 비밀값 하나에서 용도별 키를 따로 만든다 (암호화 키와 서명 키를 같이 쓰지 않게)
        this.encKey = hmac(secret.getBytes(StandardCharsets.UTF_8), "card-token-encryption");
        this.macKey = hmac(secret.getBytes(StandardCharsets.UTF_8), "card-verify-cookie");
    }

    /** 새 토큰 원문 */
    public String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return B64.encodeToString(bytes);
    }

    /** 토큰 → SHA-256 hex (DB 검색용) */
    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 토큰 암호화 → Base64URL(iv 12바이트 + 암호문) */
    public String encrypt(String token) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return B64.encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 암호문 → 토큰 원문. 비밀값이 바뀌어 풀 수 없으면 null */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] all = B64D.decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encKey, "AES"), new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 확인 쿠키 값 */
    public String cookieValue(String tokenHash, long expiresEpochSecond) {
        return expiresEpochSecond + "." + B64.encodeToString(hmac(macKey, tokenHash + "." + expiresEpochSecond));
    }

    /** 쿠키 서명이 맞고 아직 유효한지 */
    public boolean cookieValid(String value, String tokenHash, long nowEpochSecond) {
        if (value == null) {
            return false;
        }
        int dot = value.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        long expires;
        try {
            expires = Long.parseLong(value.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        if (expires < nowEpochSecond) {
            return false;
        }
        byte[] expected = cookieValue(tokenHash, expires).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, value.getBytes(StandardCharsets.UTF_8));   // 시간 차이로 추측하지 못하게
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
