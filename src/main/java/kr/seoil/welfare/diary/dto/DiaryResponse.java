package kr.seoil.welfare.diary.dto;

import java.util.List;

/**
 * 일지 응답 JSON (프론트 Diary 타입과 같은 모양)
 */
public record DiaryResponse(
        String id,                      // log_id
        String userId,                  // client_id
        String date,                    // YYYY-MM-DD
        String authorId,                // 처음 작성한 직원 (created_by)
        String lastEditorId,            // 작성 완료 후 마지막으로 수정한 직원 (없으면 응답에서 빠짐)
        String status,                  // draft | completed
        String weather,
        String condition,
        String conditionOther,
        List<ActivityDto> activities,
        String notes,
        String createdAt,               // 2026-10-16T22:40:00+09:00 형식
        String updatedAt
) {
}
