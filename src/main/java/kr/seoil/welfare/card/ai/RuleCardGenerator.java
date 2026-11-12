package kr.seoil.welfare.card.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 키가 없을 때 쓰는 생성기 (개발 서버·자동 테스트용)
 * 일지 내용을 정해진 문장 틀에 넣는다. 화면 흐름 확인용이라 문장이 단순하다.
 */
public class RuleCardGenerator implements CardGenerator {

    @Override
    public String name() {
        return "rule";
    }

    @Override
    public GeneratedCard generate(CardSource source) {
        List<GeneratedCard.Item> items = new ArrayList<>();
        for (CardSource.Activity a : source.activities()) {
            String word = shortWord(a.activity());
            String reaction = a.reaction().isEmpty() ? "" : a.reaction().get(0);
            String mood = reaction.startsWith("적극") ? "excited" : reaction.startsWith("안정") ? "good" : "tired";
            StringBuilder guardian = new StringBuilder(a.activity()).append(" 시간이었어요.");
            if (a.detail() != null && !a.detail().isBlank()) {
                guardian.append(' ').append(a.detail().trim());
            }
            if (!reaction.isEmpty()) {
                guardian.append(" 참여 모습: ").append(reaction).append('.');
            }
            if (!a.behaviors().isEmpty()) {
                guardian.append(" 활동 중 ").append(String.join(", ", a.behaviors())).append(" 행동이 있었어요.");
            }
            String text = guardian.toString();
            items.add(new GeneratedCard.Item(a.time(), word, a.defaultPictogram(), mood,
                    word + " 활동을 했어요.", text.length() > 500 ? text.substring(0, 500) : text, null));
        }
        return new GeneratedCard(source.nickname() + "님은 오늘 " + items.size() + "가지 활동을 했어요.", items);
    }

    /** "예술활동" → "예술" (10자 안으로) */
    private static String shortWord(String activity) {
        String w = activity.replace("활동", "").replace(" ", "");
        if (w.isEmpty()) {
            w = "활동";
        }
        return w.length() > 6 ? w.substring(0, 6) : w;
    }
}
