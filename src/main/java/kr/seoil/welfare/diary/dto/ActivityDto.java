package kr.seoil.welfare.diary.dto;

import java.util.List;

/**
 * 시간대별 활동 1개 (프론트 Activity). 요청과 응답에 같은 모양을 쓴다.
 * 선택 항목은 지금 프론트가 한글 이름("일상생활훈련")으로 주고받는다. 코드화는 명세서 5.1 제안.
 */
public record ActivityDto(
        String id,                      // 응답: record_id / 요청: 프론트 임시 id(act-...) → 무시
        String startTime,               // HH:mm, 비어 있을 수 있음
        String endTime,                 // HH:mm, 비어 있을 수 있음
        List<String> participation,     // 참여 활동 (activity_type)
        String participationOther,      // 참여 활동 '기타' 직접 입력
        String detail,                  // 활동 내용 (content)
        List<String> preSupport,        // 사전 지원 (pre_support)
        String preSupportOther,
        List<String> reaction,          // 이용자 반응 (reaction)
        String reactionOther,
        List<String> positiveSupport,   // 긍정행동지원 (pbs_support)
        String positiveSupportOther,
        boolean hasBehavior,            // 도전적 행동 '있음'
        List<BehaviorDto> behaviors     // 도전적 행동 선택 목록
) {
}
