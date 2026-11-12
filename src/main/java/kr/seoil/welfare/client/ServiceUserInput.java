package kr.seoil.welfare.client;

/**
 * POST /api/service-users, PUT /api/service-users/{id} 요청 JSON (프론트 ServiceUserInput과 같은 모양)
 */
public record ServiceUserInput(
        String name,                // 이름 (필수, 50자까지)
        String birthDate,           // YYYY-MM-DD (필수)
        String assignedStaffId,     // 담당 직원 번호. 비어 있으면 '담당 미지정'
        String cardNickname,        // 오늘 카드 별칭 (20자까지). 보내지 않으면(null) 그대로, 빈 문자열이면 지움
        Boolean cardConsent         // 보호자 카드 수신 동의. 보내지 않으면(null) 그대로
) {
    /** 카드 칸 없이 만들기 */
    public ServiceUserInput(String name, String birthDate, String assignedStaffId) {
        this(name, birthDate, assignedStaffId, null, null);
    }
}
