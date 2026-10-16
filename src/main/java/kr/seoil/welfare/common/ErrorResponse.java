package kr.seoil.welfare.common;

import java.util.Map;

/**
 * 오류 응답 JSON 모양: { "message": "...", "code": "...", "details": { ... } }
 * record = 값만 담는 간단한 클래스 (getter·생성자를 자바가 자동으로 만들어 줌)
 */
public record ErrorResponse(String message, String code, Map<String, Object> details) {
}
