package kr.seoil.welfare.diary.dto;

import java.util.List;

/**
 * POST /api/diaries, PUT /api/diaries/{id} 요청 JSON (프론트 SaveDiaryInput에서 diaryId를 뺀 것)
 */
public record SaveDiaryRequest(
        String userId,                  // 대상 이용자 번호 (client_id)
        String date,                    // 일지 날짜 YYYY-MM-DD
        String status,                  // draft(작성 중) | completed(작성 완료)
        String weather,                 // 날씨 이름 ("맑음" ...)
        String condition,               // 컨디션 이름 ("양호" ...)
        String conditionOther,          // 컨디션 '기타' 직접 입력
        List<ActivityDto> activities,   // 활동 목록 (통째로 교체)
        String notes,                   // 특이사항 (remark)
        String updatedAt                // PUT일 때 필수. 불러올 때 받은 updatedAt 그대로 (동시 수정 확인, 명세서 4.6)
) {
}
