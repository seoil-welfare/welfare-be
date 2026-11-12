package kr.seoil.welfare.card;

import java.time.Duration;

import kr.seoil.welfare.card.dto.CardDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오늘 카드 API (보호자, 로그인 없음) — 규칙은 GuardianCardService에 있다.
 * 직원 검문소(ActiveStaffInterceptor)에서 빠지고, CardGuardInterceptor가 캐시 금지·요청 수 제한을 건다.
 *
 *  GET  /api/cards/{token}          카드 보기 (확인 쿠키 필요)
 *  POST /api/cards/{token}/verify   생일 4자리 확인 → 확인 쿠키
 *  POST /api/cards/{token}/pick     제일 좋았던 것 고르기 (확인 쿠키 필요)
 */
@RestController
@RequestMapping("/api/cards/{token}")
public class GuardianCardController {

    static final String COOKIE = "card_verified";

    private final GuardianCardService guardianCards;
    private final boolean secureCookie;

    public GuardianCardController(GuardianCardService guardianCards,
                                  @Value("${card.cookie-secure:false}") boolean secureCookie) {
        this.guardianCards = guardianCards;
        this.secureCookie = secureCookie;
    }

    @GetMapping
    public CardDtos.GuardianCard get(@PathVariable String token,
                                     @CookieValue(name = COOKIE, required = false) String cookie) {
        return guardianCards.get(token, cookie);
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@PathVariable String token, @RequestBody CardDtos.VerifyRequest body) {
        GuardianCardService.Verified verified = guardianCards.verify(token, body == null ? null : body.birthMmdd());
        ResponseCookie cookie = ResponseCookie.from(COOKIE, verified.cookieValue())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/cards/" + token)                          // 이 링크에만 보내지는 쿠키
                .maxAge(Duration.ofSeconds(verified.maxAgeSeconds()))
                .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();   // 204
    }

    @PostMapping("/pick")
    public CardDtos.CardPick pick(@PathVariable String token,
                                  @CookieValue(name = COOKIE, required = false) String cookie,
                                  @RequestBody CardDtos.PickRequest body) {
        return guardianCards.pick(token, cookie, body);
    }
}
