package kr.seoil.welfare.diary.dto;

import java.util.List;

/**
 * 도전적 행동 선택 1개 (프론트 BehaviorSelection)
 * 예: { "type": "selfHarm", "detailIds": ["sh-1", "sh-other"], "otherText": "직접 입력" }
 */
public record BehaviorDto(
        String type,              // otherHarm | selfHarm | destruction | other
        List<String> detailIds,   // 세부 항목 코드 (behavior_item.code). 상위 'other'는 빈 목록
        String otherText          // 세부 '기타' 또는 상위 '기타'의 직접 입력
) {
}
