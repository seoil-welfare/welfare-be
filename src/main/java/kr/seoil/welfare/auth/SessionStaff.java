package kr.seoil.welfare.auth;

import kr.seoil.welfare.common.ApiException;

/**
 * 세션(로그인 상태)에 저장하는 값의 이름과, 로그인 확인 도우미.
 *
 * ★ 지민씨(로그인 담당)와의 약속
 *   로그인에 성공하면 세션에 KEY("staffId") 이름으로 직원 번호(Long)를 넣는다.
 *   session.setAttribute(SessionStaff.KEY, staffId);
 * 다른 기능은 컨트롤러에서 @SessionAttribute(SessionStaff.KEY) 로 꺼내 쓴다.
 */
public final class SessionStaff {

    public static final String KEY = "staffId";       // 세션에 저장하는 이름

    private SessionStaff() {                          // 객체로 만들 필요 없는 도우미 클래스
    }

    /** 로그인 안 된 상태(null)면 401을 던지고, 되어 있으면 직원 번호를 돌려준다 */
    public static long require(Long staffId) {
        if (staffId == null) {
            throw ApiException.unauthenticated();
        }
        return staffId;
    }
}
