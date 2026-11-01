package kr.seoil.welfare.category;

/**
 * 선택 항목 1개를 프론트에 보낼 때의 모양 (명세서 5.1).
 * 예: { "code": "art", "label": "예술활동", "order": 2, "active": true, "etc": false }
 */
public record CategoryItem(
        String code,        // 저장·통계 기준 코드. 이름을 바꿔도 그대로
        String label,       // 화면에 보이는 이름 (DB name)
        int order,          // 화면 순서 (DB sort_order)
        boolean active,     // false면 새 일지에서는 숨김. 과거 일지·통계에는 그대로 남음
        boolean etc         // '기타'면 true → 화면에 직접 입력칸을 보여 줌
) {
}
