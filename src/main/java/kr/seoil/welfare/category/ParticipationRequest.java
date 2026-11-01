package kr.seoil.welfare.category;

/**
 * 참여 활동 추가·수정 요청 JSON.
 *  추가 POST: { "label": "원예활동" }
 *  수정 PUT : { "label": "원예 활동" } 또는 { "active": false } (둘 다 보내도 됨)
 * 보내지 않은 칸은 null → 그 칸은 바꾸지 않는다.
 */
public record ParticipationRequest(
        String label,       // 화면에 보일 이름
        Boolean active      // 숨기기(false) / 다시 보이기(true). 추가할 때는 쓰지 않음
) {
}
