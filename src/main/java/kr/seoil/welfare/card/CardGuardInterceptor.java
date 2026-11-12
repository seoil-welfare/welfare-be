package kr.seoil.welfare.card;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 보호자 주소(/api/cards/**) 앞의 검문소 (명세 7장)
 *  - Cache-Control: no-store — 카드 내용을 브라우저·중간 서버에 남기지 않는다
 *  - IP별 요청 수 제한 — 1분에 30번까지. 넘으면 429 RATE_LIMITED (토큰 무작위 대입 방지)
 *    서버 한 대 기준 메모리 계산이다. 프록시 뒤에서는 X-Forwarded-For의 첫 주소를 쓴다
 */
@Component
public class CardGuardInterceptor implements HandlerInterceptor {

    static final int LIMIT_PER_MINUTE = 30;

    // Clock 빈을 받지 않는다: 직원 화면만 띄우는 컨트롤러 테스트(@WebMvcTest)에도 이 검문소가 함께 올라오기 때문
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long minute, int count) {
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        long minute = System.currentTimeMillis() / 60_000;
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> w.minute() < minute);       // 지난 분의 기록은 버린다
        }
        Window w = windows.merge(clientIp(request), new Window(minute, 1),
                (old, one) -> old.minute() == minute ? new Window(minute, old.count() + 1) : one);
        if (w.count() > LIMIT_PER_MINUTE) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"요청이 너무 많아요. 잠시 뒤에 다시 시도해 주세요.\",\"code\":\"RATE_LIMITED\"}");
            return false;
        }
        return true;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
