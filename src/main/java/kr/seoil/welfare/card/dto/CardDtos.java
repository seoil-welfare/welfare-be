package kr.seoil.welfare.card.dto;

import java.util.List;

/**
 * 오늘 카드 요청·응답 JSON (프론트 src/types/todayCard.ts, src/api/types.ts와 같은 모양)
 * 값이 없는(null) 칸은 응답에서 빠진다 (application.yml default-property-inclusion: non_null)
 */
public final class CardDtos {

    private CardDtos() {
    }

    /** 카드 항목 1개 */
    public record CardItem(
            String id,
            String time,                // HH:mm
            String activity,
            String word,
            String pictogram,
            String mood,
            String easyText,
            String guardianText,
            String homeTip
    ) {
    }

    /** 보호자가 고른 결과 */
    public record CardPick(String itemId, String how, boolean skipped, String pickedAt) {
    }

    /** 담당자용 카드 (GET /api/diaries/{diaryId}/card) */
    public record TodayCard(
            String diaryId,
            String status,              // generating | ready | failed | outdated | sent
            String nickname,
            String date,
            String program,
            String summary,
            List<CardItem> items,
            boolean needsCall,
            String updatedAt,
            String sentAt,              // sent일 때
            String expiresOn,           // sent일 때
            String shareUrl,            // sent일 때. 같은 링크를 다시 복사할 수 있다
            CardPick pick
    ) {
    }

    /** 보호자용 카드 (GET /api/cards/{token}). 실명·생년월일·직원 이름은 넣지 않는다 */
    public record GuardianCard(
            String nickname,
            String date,
            String program,
            String summary,
            List<CardItem> items,
            boolean needsCall,
            String expiresOn,
            CardPick pick
    ) {
    }

    /** PUT /api/diaries/{diaryId}/card */
    public record CardUpdate(String updatedAt, Boolean needsCall, List<ItemText> items) {
    }

    public record ItemText(String id, String easyText, String guardianText, String homeTip) {
    }

    /** POST /api/cards/{token}/verify */
    public record VerifyRequest(String birthMmdd) {
    }

    /** POST /api/cards/{token}/pick */
    public record PickRequest(String itemId, String how, Boolean skipped) {
    }
}
