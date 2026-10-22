package kr.seoil.welfare.client;

/**
 * 프론트 ServiceUser 타입과 같은 모양의 JSON. (DB 테이블 이름은 client)
 */
public record ServiceUserDto(
        String id,                  // 이용자 번호 (client_id)
        String name,                // 이름 (개발 DB에는 가상 이름만)
        String birthDate,           // YYYY-MM-DD
        String assignedStaffId,     // 담당 직원 번호 (없으면 빈 문자열)
        String status               // active | closed
) {
}
