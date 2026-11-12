package kr.seoil.welfare.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import kr.seoil.welfare.card.ai.CardSource;
import kr.seoil.welfare.card.ai.GeneratedCard;
import kr.seoil.welfare.card.CardRepository.NewItem;

/**
 * 카드에 쓰는 정해진 값과 AI 결과 검증 (명세 3장, 5-1 '결과 검증')
 */
public final class CardRules {

    public static final List<String> PICTOGRAMS =
            List.of("music", "meal", "art", "sports", "cooking", "walk", "outing", "daily", "favorite", "rest");
    public static final List<String> MOODS = List.of("excited", "good", "tired");
    public static final Set<String> HOWS = Set.of("pointed", "observed");

    /** DB 칸 길이와 같다 */
    public static final int WORD_MAX = 10;
    public static final int EASY_MAX = 100;
    public static final int GUARDIAN_MAX = 500;
    public static final int TIP_MAX = 200;
    public static final int LABEL_MAX = 50;

    /** 앞면에 나오면 안 되는 도전적 행동 영역 이름 */
    private static final List<String> BEHAVIOR_AREA_WORDS = List.of("타해", "자해", "파괴행동", "도전적 행동");

    /** AI가 '없음'을 글자로 쓰는 경우 → 빈 값 */
    private static final Set<String> NO_TIP = Set.of("", "없음", "null", "-");

    private CardRules() {
    }

    /** 검증 실패 — 카드는 failed가 된다 */
    public static class InvalidCard extends RuntimeException {
        public InvalidCard(String reason) {
            super(reason);
        }
    }

    /** 검증된 결과 */
    public record Valid(String summary, List<NewItem> items) {
    }

    /**
     * AI 결과 검증
     *  - 항목 수·시각이 일지 활동과 다름, 빈 문장, 길이 초과 → 실패
     *  - 그림·표정이 목록 밖 → 기본 그림 / good으로 바꾼다
     *  - 앞면(word, easyText)에 그날 기록된 도전적 행동이 들어 있음 → 실패
     */
    public static Valid validate(CardSource source, GeneratedCard card, List<String> behaviorWords) {
        if (card == null || card.items() == null) {
            throw new InvalidCard("결과가 비어 있음");
        }
        String summary = required(card.summary(), "summary", GUARDIAN_MAX);
        List<CardSource.Activity> activities = source.activities();
        if (card.items().size() != activities.size()) {
            throw new InvalidCard("항목 수가 다름: 활동 " + activities.size() + "개, 카드 " + card.items().size() + "개");
        }
        List<String> forbidden = new ArrayList<>(BEHAVIOR_AREA_WORDS);
        behaviorWords.stream().filter(w -> w != null && !w.isBlank() && !"기타".equals(w)).forEach(forbidden::add);

        List<NewItem> items = new ArrayList<>();
        for (int i = 0; i < activities.size(); i++) {
            CardSource.Activity a = activities.get(i);
            GeneratedCard.Item g = card.items().get(i);
            if (g == null || !a.time().equals(g.time() == null ? null : g.time().trim())) {
                throw new InvalidCard((i + 1) + "번째 항목 시각이 다름");
            }
            String word = required(g.word(), "word", WORD_MAX);
            String easy = required(g.easyText(), "easyText", EASY_MAX);
            String guardian = required(g.guardianText(), "guardianText", GUARDIAN_MAX);
            String tip = g.homeTip() == null || NO_TIP.contains(g.homeTip().trim()) ? null : g.homeTip().trim();
            if (tip != null && tip.length() > TIP_MAX) {
                throw new InvalidCard("homeTip 길이 초과");
            }
            for (String f : forbidden) {
                if (word.contains(f) || easy.contains(f)) {
                    throw new InvalidCard("앞면에 도전적 행동이 들어감: " + f);
                }
            }
            String pictogram = PICTOGRAMS.contains(g.pictogram()) ? g.pictogram() : a.defaultPictogram();
            String mood = MOODS.contains(g.mood()) ? g.mood() : "good";
            items.add(new NewItem(a.recordId(), i + 1, java.time.LocalTime.parse(a.time()), truncate(a.activity(), LABEL_MAX),
                    word, pictogram, mood, easy, guardian, tip));
        }
        return new Valid(summary, items);
    }

    private static String required(String value, String field, int max) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new InvalidCard(field + "가 비어 있음");
        }
        if (v.length() > max) {
            throw new InvalidCard(field + " 길이 초과 (" + v.length() + "자)");
        }
        return v;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
