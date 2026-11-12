package kr.seoil.welfare.card.ai;

import java.util.List;

/**
 * AI에 보내는 일지 내용 (명세 5-1 '개인정보 최소화')
 * 이용자 실명·생년월일·직원 이름은 이 안에 없다. 이름 대신 보호자와 정한 별칭(nickname)만 쓴다.
 * 이 객체를 JSON으로 바꾼 것이 그대로 AI 요청에 들어간다.
 */
public record CardSource(
        String nickname,                // 카드 별칭 (없으면 '이용자')
        String date,                    // YYYY-MM-DD
        String condition,               // 컨디션 ('기타'면 직접 쓴 내용)
        String notes,                   // 특이사항
        List<Activity> activities       // 시작 시각 순서
) {

    /** 활동 1개 = 카드 항목 1개 */
    public record Activity(
            Long recordId,              // AI에는 보내지 않는다 (toPrompt()에서 뺀다)
            String time,                // HH:mm
            String activity,            // 대표 참여 활동 이름 ('기타'면 직접 쓴 글)
            String defaultPictogram,    // AI가 목록 밖의 그림을 고르면 쓸 기본 그림
            String detail,              // 활동 상세 내용
            List<String> preSupport,    // 사전 지원
            List<String> reaction,      // 이용자 반응
            List<String> positiveSupport,
            List<String> behaviors      // 도전적 행동 "자해: 머리 때리기" (없으면 빈 목록)
    ) {
    }

    /** AI에 보낼 모양: 서버 내부 값(recordId, 기본 그림)을 뺀다 */
    public Prompt toPrompt() {
        return new Prompt(nickname, date, condition, notes, activities.stream()
                .map(a -> new PromptActivity(a.time(), a.activity(), a.detail(), a.preSupport(), a.reaction(),
                        a.positiveSupport(), a.behaviors()))
                .toList());
    }

    public record Prompt(String nickname, String date, String condition, String notes, List<PromptActivity> activities) {
    }

    public record PromptActivity(String time, String activity, String detail, List<String> preSupport,
                                 List<String> reaction, List<String> positiveSupport, List<String> behaviors) {
    }
}
