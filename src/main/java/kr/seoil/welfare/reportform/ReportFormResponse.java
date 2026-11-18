package kr.seoil.welfare.reportform;

import java.time.OffsetDateTime;

/**
 * 출력 양식 한 개 (프론트 src/api/types.ts의 ReportForm과 같은 모양).
 * 파일 내용은 담지 않는다. 내용은 GET /api/report-forms/{id}/file 로 따로 받는다.
 */
public record ReportFormResponse(
        String id,
        String kind,                // daily | monthly
        String name,
        String fileName,
        boolean active,             // false면 숨김
        OffsetDateTime uploadedAt,
        String uploadedBy) {        // 올린 사람 이름
}
