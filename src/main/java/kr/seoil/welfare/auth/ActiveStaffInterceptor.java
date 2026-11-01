package kr.seoil.welfare.auth;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.seoil.welfare.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * [설명 포인트] 모든 API 앞의 '검문소'
 *  로그인한 직원이 그사이 사용 중지되었는지를 요청마다 DB에서 다시 확인한다 (명세서 8.3 — 이미 로그인된 세션도 끊는다).
 *
 * 왜 필요한가?
 *   일지·통계·이용자 API는 SessionStaff.require로 "세션에 직원 번호가 있나"만 본다.
 *   그래서 로그인해 둔 상태에서 계정이 정지되면, 세션이 살아 있는 동안 일지를 계속 쓸 수 있었다.
 *   Controller마다 검사를 넣는 대신 여기 한 곳에서 막는다. 나중에 API가 늘어나도 자동으로 적용된다.
 *
 * 동작
 *   - 로그인 안 한 요청(세션 없음, staffId 없음) → 그냥 통과. 401은 각 Controller의 SessionStaff.require가 낸다
 *   - 계정이 사용 중지됨 → 세션을 끊고 403 ACCOUNT_DISABLED (로그인 때와 같은 오류)
 *   - 계정이 아예 없음   → 세션을 끊고 401
 *   어떤 주소에 적용할지는 ActiveStaffWebConfig에서 정한다 (/api/auth/**, /api/health는 제외).
 */
@Component
public class ActiveStaffInterceptor implements HandlerInterceptor {

    private final AuthRepository authRepository;

    public ActiveStaffInterceptor(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    /** Controller가 실행되기 전에 불린다. true면 통과, 예외를 던지면 거기서 멈춘다 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);                 // false: 세션이 없으면 새로 만들지 않음
        if (session == null) {
            return true;                                                 // 로그인 안 한 요청
        }
        if (!(session.getAttribute(SessionStaff.KEY) instanceof Long staffId)) {
            return true;                                                 // 세션은 있지만 로그인 정보가 없음
        }
        Optional<AuthRepository.Account> account = authRepository.findById(staffId);   // SELECT ... WHERE staff_id = ?
        if (account.isEmpty()) {                                         // 계정이 사라졌으면 로그인 상태가 아닌 것
            session.invalidate();
            throw ApiException.unauthenticated();                        // 401
        }
        if (!account.get().active()) {                                   // 로그인 뒤에 사용 중지됨
            session.invalidate();                                        // 세션(쿠키)을 끊어서 다음 요청도 못 하게
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "사용이 중지된 계정이에요. 관리자에게 문의해 주세요.");     // AuthService와 같은 문구
        }
        return true;                                                     // 사용 중인 계정 → Controller로
    }
}