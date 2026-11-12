package kr.seoil.welfare.card;

/** 이 일지의 카드를 AI로 만들어 달라는 요청. 트랜잭션이 커밋된 뒤에 처리한다 */
public record CardGenerationRequested(long logId) {
}
