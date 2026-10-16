package kr.seoil.welfare.diary.dto;

/**
 * 오늘의 소식 1건 (프론트 NewsEntry)
 * { "event": { id, diaryId, kind, actorId, occurredAt }, "diary": { id, userId, date } }
 */
public record NewsEntryDto(Event event, DiaryRef diary) {

    /** 등록·수정 이력 (log_history 한 줄) */
    public record Event(String id, String diaryId, String kind, String actorId, String occurredAt) {
    }

    /** 이력이 가리키는 일지 요약 */
    public record DiaryRef(String id, String userId, String date) {
    }
}
