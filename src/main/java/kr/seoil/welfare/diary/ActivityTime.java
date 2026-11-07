package kr.seoil.welfare.diary;

import kr.seoil.welfare.common.ApiException;

/** 하루 안의 활동 시각. 종료 24:00을 LocalTime으로 바꾸면 00:00이 되므로 분 단위로 다룬다. */
public final class ActivityTime {
    private ActivityTime() {}

    public static Integer parse(String value, boolean end) {
        if (value == null || value.isBlank()) return null;
        String time = value.trim();
        if (!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]") && !(end && time.equals("24:00"))) {
            throw ApiException.badRequest(end ? "종료 시간은 00:00~24:00의 HH:mm 형식으로 입력해 주세요."
                    : "시작 시간은 00:00~23:59의 HH:mm 형식으로 입력해 주세요.");
        }
        return Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3));
    }

    public static String format(Integer minutes) {
        return minutes == null ? null : "%02d:%02d".formatted(minutes / 60, minutes % 60);
    }

    /** PostgreSQL TIME 24:00:00도 그대로 보존한다. JDBC LocalTime 매핑을 거치지 않는다. */
    public static String fromSql(String value) {
        return value == null ? null : value.substring(0, 5);
    }
}
