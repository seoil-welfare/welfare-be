package kr.seoil.welfare.card.ai;

import java.util.List;

/**
 * AI가 돌려준 카드 (검증 전). 항목은 CardSource.activities와 같은 순서·같은 시각이어야 한다.
 */
public record GeneratedCard(String summary, List<Item> items) {

    public record Item(String time, String word, String pictogram, String mood,
                       String easyText, String guardianText, String homeTip) {
    }
}
