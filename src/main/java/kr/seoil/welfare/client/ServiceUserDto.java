package kr.seoil.welfare.client;

/**
 * 프론트 ServiceUser 타입과 같은 모양의 JSON. (DB 테이블 이름은 client)
 */
public record ServiceUserDto(
        String id,                  // 이용자 번호 (client_id)
        String name,                // 이름 (개발 DB에는 가상 이름만)
        String birthDate,           // YYYY-MM-DD
        String assignedStaffId,     // 담당 직원 번호 (없으면 빈 문자열)
        String status,              // active | closed
        String cardNickname,        // 오늘 카드에 보이는 별칭 (없으면 빠짐 → 카드에는 '이용자')
        boolean cardConsent,        // 보호자 카드 수신 동의 (없으면 공유 링크를 만들 수 없음)
        String cardConsentAt        // 동의를 등록한 시각 (동의 안 했으면 빠짐)
) {
    /** 카드 칸 없이 만들기 (기존 테스트용) */
    public ServiceUserDto(String id, String name, String birthDate, String assignedStaffId, String status) {
        this(id, name, birthDate, assignedStaffId, status, null, false, null);
    }
}
