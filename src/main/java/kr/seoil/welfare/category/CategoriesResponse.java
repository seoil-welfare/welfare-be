package kr.seoil.welfare.category;

import java.util.List;
import java.util.Map;

/**
 * GET /api/categories 응답 (명세서 5.1). 일지 화면의 선택 항목 전부.
 * 숨긴 항목(active: false)도 함께 보낸다. 과거 일지를 보여 줄 때 이름이 필요하기 때문이다.
 */
public record CategoriesResponse(
        List<CategoryItem> weather,             // 날씨
        List<CategoryItem> condition,           // 컨디션
        List<CategoryItem> participation,       // 참여 활동
        List<CategoryItem> preSupport,          // 사전 지원
        List<CategoryItem> reaction,            // 이용자 반응
        List<CategoryItem> positiveSupport,     // 긍정행동지원
        List<BehaviorType> behaviorTypes,       // 도전적 행동 영역 (타해·자해·파괴행동·기타)
        Map<String, List<CategoryItem>> behaviorDetails   // 영역 코드 → 세부 항목 { otherHarm: [...], ... }
) {

    /** 도전적 행동 영역 1개: { code: "otherHarm", label: "타해", order: 1 } */
    public record BehaviorType(String code, String label, int order) {
    }
}
