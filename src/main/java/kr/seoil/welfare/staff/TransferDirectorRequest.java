package kr.seoil.welfare.staff;

/**
 * POST /api/staff/{id}/transfer-director 요청 JSON
 * 본문이 없거나 previousRole이 비면 "teamLead"로 처리한다 (AuthService.transferDirector).
 */
public record TransferDirectorRequest(
        String previousRole     // 이양 뒤 원래 국장 처리: teamLead(팀장으로 남기) | deactivate(계정 사용 중지)
) {
}
