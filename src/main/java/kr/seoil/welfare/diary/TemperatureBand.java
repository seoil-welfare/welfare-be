package kr.seoil.welfare.diary;

import kr.seoil.welfare.common.ApiException;

/**
 * 일지 온도 구간 (daily_log.temperature). 복지관 요청으로 날씨와 함께 고른다.
 * 저장·응답은 코드(COLD 등)로 하고, 화면 글자는 프론트가 정한다. 통계 응답에는 이름도 함께 보낸다.
 * 값을 바꾸면 V10 마이그레이션의 CHECK도 함께 바꿔야 한다.
 */
public enum TemperatureBand {
    COLD("추움", "10℃ 이하"),
    COOL("선선함", "11~19℃"),
    WARM("따뜻함", "20~27℃"),
    HOT("더움", "28℃ 이상");

    private final String label;
    private final String range;

    TemperatureBand(String label, String range) {
        this.label = label;
        this.range = range;
    }

    public String label() { return label; }
    public String range() { return range; }

    /** 요청 값 → 구간. 비어 있으면 null (온도는 고르지 않아도 된다), 모르는 값이면 400 */
    public static TemperatureBand parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("온도는 COLD, COOL, WARM, HOT 중 하나여야 해요.");
        }
    }
}
