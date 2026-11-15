package kr.seoil.welfare.card.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * '기타' 활동명이 길 때 앞면 단어(word)가 음절 중간에서 잘리지 않는지 확인 (QA 4번)
 */
class RuleCardGeneratorTest {

    private final RuleCardGenerator generator = new RuleCardGenerator();

    private GeneratedCard.Item generateFirst(String activityName) {
        CardSource.Activity activity = new CardSource.Activity(1L, "09:00", activityName, "daily", null,
                List.of(), List.of(), List.of(), List.of());
        CardSource source = new CardSource("별명", "2026-11-14", "보통", null, List.of(activity));
        return generator.generate(source).items().get(0);
    }

    @Test
    void 기타_직접입력이_길면_단어_단위로_6글자까지만_남긴다() {
        GeneratedCard.Item item = generateFirst("하원 준비 및 하루 돌아보기");

        assertThat(item.word()).isEqualTo("하원 준비");
        assertThat(item.easyText()).isEqualTo("하원 준비 및 하루 돌아보기 활동을 했어요.");
    }

    @Test
    void 첫_단어부터_6글자를_넘으면_그_단어만_6글자로_자른다() {
        GeneratedCard.Item item = generateFirst("일상생활훈련프로그램");

        assertThat(item.word()).isEqualTo("일상생활훈련");
    }

    @Test
    void 활동_글자는_빼고_남긴다() {
        assertThat(generateFirst("예술활동").word()).isEqualTo("예술");
        assertThat(generateFirst("요리활동").word()).isEqualTo("요리");
        assertThat(generateFirst("일상생활훈련").word()).isEqualTo("일상생활훈련");
    }

    @Test
    void 활동이라는_글자만_있으면_활동으로_남는다() {
        assertThat(generateFirst("활동").word()).isEqualTo("활동");
    }

    @Test
    void 공백이_많아도_하나로_줄여서_단어_단위로_자른다() {
        GeneratedCard.Item item = generateFirst("  하원   준비   및   하루   돌아보기  ");

        assertThat(item.word()).isEqualTo("하원 준비");
    }
}
