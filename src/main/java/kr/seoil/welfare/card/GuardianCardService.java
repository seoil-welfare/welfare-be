package kr.seoil.welfare.card;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import kr.seoil.welfare.card.CardRepository.CardRow;
import kr.seoil.welfare.card.CardRepository.LogInfo;
import kr.seoil.welfare.card.dto.CardDtos;
import kr.seoil.welfare.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘 카드 — 보호자 쪽 (로그인 없이 공유 링크 토큰으로, 명세 5-6~5-8, 7장)
 *
 *  - 401은 쓰지 않는다. 확인 전이면 403 CARD_VERIFY_REQUIRED (프론트는 401을 직원 로그인 만료로 본다)
 *  - 확인: 이용자 생일 월·일 4자리. 5번 틀리면 30분 잠금 (429 CARD_VERIFY_LOCKED)
 *  - 확인이 맞으면 이 링크 전용 서명 쿠키를 준다. 링크가 만료되는 날 끝까지 유효
 *  - 만료(expiresOn 다음 날부터) → 410 CARD_EXPIRED. 없는 토큰 → 404
 *  - 응답에 실명·생년월일·직원 이름을 넣지 않는다
 */
@Service
public class GuardianCardService {

    static final int VERIFY_MAX_FAILS = 5;
    static final int VERIFY_LOCK_MINUTES = 30;

    private final CardRepository repo;
    private final CardTokens tokens;
    private final TodayCardService cards;
    private final Clock clock;

    public GuardianCardService(CardRepository repo, CardTokens tokens, TodayCardService cards, Clock clock) {
        this.repo = repo;
        this.tokens = tokens;
        this.cards = cards;
        this.clock = clock;
    }

    /** 확인 쿠키를 줄 때 쓰는 값 */
    public record Verified(String cookieValue, long maxAgeSeconds) {
    }

    /** GET /api/cards/{token} */
    @Transactional(readOnly = true)
    public CardDtos.GuardianCard get(String token, String cookie) {
        CardRow card = open(repo.findByTokenHash(hashOf(token)));
        requireVerified(card, cookie);
        LogInfo info = repo.logInfo(card.logId());
        return new CardDtos.GuardianCard(TodayCardService.nickname(info), info.date().toString(),
                TodayCardService.PROGRAM, card.summary() == null ? "" : card.summary(), cards.items(card.cardId()),
                card.needsCall(), card.expiresOn().toString(), cards.pick(card.cardId()));
    }

    /** POST /api/cards/{token}/verify */
    @Transactional(noRollbackFor = ApiException.class)              // 틀린 횟수는 오류를 돌려줘도 저장한다
    public Verified verify(String token, String birthMmdd) {
        CardRow card = open(repo.lockByTokenHash(hashOf(token)));
        OffsetDateTime now = now();
        if (card.verifyLockedUntil() != null && card.verifyLockedUntil().isAfter(now)) {
            throw locked();
        }
        LogInfo info = repo.logInfo(card.logId());
        String expected = String.format("%02d%02d", info.birthDate().getMonthValue(), info.birthDate().getDayOfMonth());
        String given = birthMmdd == null ? "" : birthMmdd.replaceAll("\\D", "");
        if (!expected.equals(given)) {
            int fails = card.verifyFailCount() + 1;
            if (fails >= VERIFY_MAX_FAILS) {
                repo.setVerifyState(card.cardId(), 0, now.plusMinutes(VERIFY_LOCK_MINUTES));
                throw locked();
            }
            repo.setVerifyState(card.cardId(), fails, null);
            throw new ApiException(HttpStatus.BAD_REQUEST, "CARD_VERIFY_FAILED", "생일이 맞지 않아요. 다시 입력해 주세요.");
        }
        repo.setVerifyState(card.cardId(), 0, null);
        long expires = card.expiresOn().plusDays(1).atStartOfDay(TodayCardService.KST).toEpochSecond();
        long maxAge = Math.max(0, expires - now.toEpochSecond());
        return new Verified(tokens.cookieValue(card.tokenHash(), expires), maxAge);
    }

    /** POST /api/cards/{token}/pick */
    @Transactional
    public CardDtos.CardPick pick(String token, String cookie, CardDtos.PickRequest req) {
        CardRow card = open(repo.lockByTokenHash(hashOf(token)));
        requireVerified(card, cookie);
        if (req == null) {
            throw ApiException.badRequest("고른 내용을 보내 주세요.");
        }
        boolean skipped = Boolean.TRUE.equals(req.skipped());
        Long itemId = null;
        String how = null;
        if (!skipped) {
            List<Long> ids = repo.items(card.cardId()).stream().map(CardRepository.ItemRow::itemId).toList();
            try {
                itemId = Long.parseLong(req.itemId() == null ? "" : req.itemId().trim());
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("이 카드에 없는 활동이에요.");
            }
            if (!ids.contains(itemId)) {
                throw ApiException.badRequest("이 카드에 없는 활동이에요.");
            }
            how = req.how();
            if (how == null || !CardRules.HOWS.contains(how)) {
                throw ApiException.badRequest("어떻게 골랐는지(pointed 또는 observed)를 보내 주세요.");
            }
        }
        repo.upsertPick(card.cardId(), itemId, how, skipped, now());
        return cards.pick(card.cardId());
    }

    // ------------------------------------------------------------------

    /** 없는 토큰 404, 만료 410 */
    private CardRow open(CardRow card) {
        if (card == null || !"sent".equals(card.status())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "찾을 수 없는 카드예요.");
        }
        if (today().isAfter(card.expiresOn())) {
            throw new ApiException(HttpStatus.GONE, "CARD_EXPIRED", "링크 기간이 지났어요.");
        }
        return card;
    }

    private void requireVerified(CardRow card, String cookie) {
        if (!tokens.cookieValid(cookie, card.tokenHash(), now().toEpochSecond())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CARD_VERIFY_REQUIRED", "보호자 확인이 필요해요.");
        }
    }

    private static ApiException locked() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CARD_VERIFY_LOCKED",
                "여러 번 틀려서 잠시 잠겼어요. " + VERIFY_LOCK_MINUTES + "분 뒤에 다시 시도해 주세요.");
    }

    private String hashOf(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "찾을 수 없는 카드예요.");
        }
        return tokens.hash(token);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(TodayCardService.KST));
    }
}
