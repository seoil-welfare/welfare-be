package kr.seoil.welfare.card;

import kr.seoil.welfare.diary.DiaryCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 일지 저장 ↔ 오늘 카드 연결
 *  - 일지 작성 완료: 일지 저장 트랜잭션 안에서 바로 카드 행을 만든다 (일지 저장이 취소되면 카드도 함께 취소)
 *  - AI 호출: 트랜잭션이 커밋된 뒤에 비동기로 (일지 저장 응답을 AI 때문에 늦추지 않게)
 */
@Component
public class CardEvents {

    private final TodayCardService cards;
    private final CardGenerationWorker worker;

    public CardEvents(TodayCardService cards, CardGenerationWorker worker) {
        this.cards = cards;
        this.worker = worker;
    }

    @EventListener
    public void onDiaryCompleted(DiaryCompletedEvent event) {
        cards.onDiaryCompleted(event.logId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGenerationRequested(CardGenerationRequested event) {
        worker.generateAsync(event.logId());
    }
}
