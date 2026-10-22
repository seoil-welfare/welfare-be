package kr.seoil.welfare.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 컨트롤러에서 터진 예외를 한 곳에서 받아 같은 모양의 오류 JSON으로 바꾼다.
 * 이렇게 하면 프론트는 어떤 오류든 message를 그대로 화면에 띄우면 된다.
 */
@RestControllerAdvice                                            // 모든 @RestController에 적용
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 우리가 직접 던진 오류 → 정해 둔 상태·코드 그대로 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getMessage(), e.getCode(), e.getDetails()));
    }

    /** JSON 모양이 깨졌거나 타입이 안 맞음 (예: 숫자 칸에 글자) → 400 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("요청 형식이 올바르지 않아요.", "VALIDATION_ERROR", null));
    }

    /** DB UNIQUE 제약에 걸림 (동시에 같은 날짜 일지를 두 번 저장한 경우 등) → 409 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("이미 같은 날짜의 일지가 있어요.", "DIARY_ALREADY_EXISTS", null));
    }

    /** 없는 주소로 요청 → 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("찾을 수 없는 주소예요.", "NOT_FOUND", null));
    }

    /** 그 밖의 모든 오류 → 500. 내부 정보(SQL 등)는 화면에 보내지 않고 서버 로그에만 남긴다 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("처리하지 못한 오류", e);                         // IntelliJ 실행 창에 빨갛게 찍힘
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.", "INTERNAL_ERROR", null));
    }
}
