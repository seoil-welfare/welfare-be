package kr.seoil.welfare.card.ai;

/**
 * 카드 문장을 만드는 쪽.
 *  - OpenAiCardGenerator: OPENAI_API_KEY가 있으면 실제 AI
 *  - RuleCardGenerator : 키가 없을 때(개발·테스트) 일지 내용으로 정해진 문장을 만든다
 * 실패하면 예외를 던진다. 던지면 카드는 failed가 된다.
 */
public interface CardGenerator {

    GeneratedCard generate(CardSource source);

    /** 로그용 이름 */
    String name();
}
