package kr.seoil.welfare.diary;

/**
 * 일지를 작성 완료로 저장했을 때 (처음 완료 또는 완료 일지 수정)
 * 바뀐 내용 없이 다시 저장한 경우는 보내지 않는다 (DiaryService 규칙 5).
 * 오늘 카드(TodayCardService)가 받아서 카드를 만들거나 outdated로 바꾼다.
 */
public record DiaryCompletedEvent(long logId) {
}
