package kr.seoil.welfare.client;

/**
 * POST /api/service-users, PUT /api/service-users/{id} 요청 JSON (프론트 ServiceUserInput과 같은 모양)
 */
public record ServiceUserInput(
        String name,                // 이름 (필수, 50자까지)
        String birthDate,           // YYYY-MM-DD (필수)
        String assignedStaffId      // 담당 직원 번호. 비어 있으면 '담당 미지정'
) {
}
