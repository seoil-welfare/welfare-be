package kr.seoil.welfare.card;

import java.util.Optional;

import kr.seoil.welfare.card.ai.CardGenerator;
import kr.seoil.welfare.card.ai.GeneratedCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 카드 생성 실행 (명세 5-1 '어떻게')
 *  1) 일지를 읽어 AI 입력을 만든다 (짧은 읽기 트랜잭션)
 *  2) AI를 부른다 (트랜잭션 밖 — DB 연결을 붙잡고 기다리지 않게)
 *  3) 검증해서 ready로 저장, 실패면 failed
 */
@Component
public class CardGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(CardGenerationWorker.class);

    private final TodayCardService cards;
    private final CardGenerator generator;

    public CardGenerationWorker(TodayCardService cards, CardGenerator generator) {
        this.cards = cards;
        this.generator = generator;
    }

    /** 일지 저장 응답과 따로 돈다 */
    @Async("cardExecutor")
    public void generateAsync(long logId) {
        generate(logId);
    }

    /** 실제 처리 (테스트는 이것을 바로 부른다) */
    public void generate(long logId) {
        Optional<TodayCardService.Prepared> prepared;
        try {
            prepared = cards.prepare(logId);
        } catch (RuntimeException e) {
            log.warn("오늘 카드 입력 준비 실패 logId={}", logId, e);
            cards.fail(logId, "입력 준비 실패: " + e.getMessage());
            return;
        }
        if (prepared.isEmpty()) {
            return;
        }
        try {
            GeneratedCard result = generator.generate(prepared.get().source());
            cards.complete(logId, prepared.get(), result);
        } catch (RuntimeException e) {
            log.warn("오늘 카드 생성 실패 logId={} generator={}: {}", logId, generator.name(), e.getMessage());
            cards.fail(logId, generator.name() + " 실패: " + e.getMessage());
        }
    }
}
