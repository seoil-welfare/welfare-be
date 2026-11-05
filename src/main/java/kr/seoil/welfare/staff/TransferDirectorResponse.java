package kr.seoil.welfare.staff;

/**
 * POST /api/staff/{id}/transfer-director 응답 JSON
 * 프론트는 previous.active가 false면 로그인 화면으로 보낸다.
 */
public record TransferDirectorResponse(
        StaffDto director,      // 새 국장 (받은 사람)의 바뀐 정보
        StaffDto previous       // 원래 국장 (요청한 사람)의 바뀐 정보
) {
}
