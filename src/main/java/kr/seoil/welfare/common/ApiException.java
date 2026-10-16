package kr.seoil.welfare.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * 화면에 보여 줄 오류를 던질 때 쓰는 예외.
 * 던지면 GlobalExceptionHandler가 { message, code, details } 모양의 JSON으로 바꿔 응답한다.
 * code 목록은 기능명세서 4.2 (VALIDATION_ERROR, NOT_FOUND, DIARY_ALREADY_EXISTS ...)
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;              // HTTP 상태 (400, 404, 409 ...)
    private final String code;                    // 프론트가 구분하는 오류 코드
    private final Map<String, Object> details;    // 추가 정보 (없으면 null)

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);        // details 없이 만드는 생성자
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);                           // message는 화면에 그대로 보이는 한국어 문장
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, Object> getDetails() { return details; }

    // ---- 자주 쓰는 오류를 짧게 만드는 도우미 ----

    /** 400 — 입력값이 잘못됨 */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /** 404 — 찾는 대상이 없음 (예: "일지") */
    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "찾을 수 없는 " + what + "예요.");
    }

    /** 401 — 로그인 안 됨 */
    public static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요해요.");
    }
}
