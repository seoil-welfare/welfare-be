package kr.seoil.welfare.card;

import kr.seoil.welfare.auth.SessionStaff;
import kr.seoil.welfare.card.dto.CardDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 오늘 카드 API (담당자, 로그인 필요) — 규칙은 TodayCardService에 있다.
 *
 *  GET  /api/diaries/{diaryId}/card             카드 조회 (generating이면 프론트가 3초마다 다시 부름)
 *  PUT  /api/diaries/{diaryId}/card             문장·'따로 연락' 고치기
 *  POST /api/diaries/{diaryId}/card/send        공유 링크 만들기 (응답 shareUrl을 복사해 보호자에게 보냄)
 *  POST /api/diaries/{diaryId}/card/regenerate  다시 만들기 (failed·outdated)
 */
@RestController
@RequestMapping("/api/diaries/{diaryId}/card")
public class TodayCardController {

    private final TodayCardService cards;

    public TodayCardController(TodayCardService cards) {
        this.cards = cards;
    }

    @GetMapping
    public CardDtos.TodayCard get(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                  @PathVariable String diaryId) {
        return cards.get(diaryId, SessionStaff.require(staffId));
    }

    @PutMapping
    public CardDtos.TodayCard update(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                     @PathVariable String diaryId,
                                     @RequestBody CardDtos.CardUpdate body) {
        return cards.update(diaryId, body, SessionStaff.require(staffId));
    }

    @PostMapping("/send")
    public CardDtos.TodayCard send(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                   @PathVariable String diaryId) {
        return cards.send(diaryId, SessionStaff.require(staffId));
    }

    @PostMapping("/regenerate")
    public CardDtos.TodayCard regenerate(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                         @PathVariable String diaryId) {
        return cards.regenerate(diaryId, SessionStaff.require(staffId));
    }
}
