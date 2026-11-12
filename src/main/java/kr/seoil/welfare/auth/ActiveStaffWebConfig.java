package kr.seoil.welfare.auth;

import kr.seoil.welfare.card.CardGuardInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 검문소(ActiveStaffInterceptor)를 어느 주소에 세울지 정한다.
 *  - /api/** 전부에 적용
 *  - /api/auth/** 는 제외: 로그인은 로그인 전에 부르고, me·비밀번호 변경은 AuthService가 이미 사용 중지를 검사한다
 *  - /api/health 는 제외: 서버 상태 확인용
 *  - /api/cards/** 는 제외: 보호자가 로그인 없이 공유 링크로 여는 오늘 카드. 대신 CardGuardInterceptor를 세운다
 */
@Configuration
public class ActiveStaffWebConfig implements WebMvcConfigurer {

    private final ActiveStaffInterceptor activeStaffInterceptor;
    private final CardGuardInterceptor cardGuardInterceptor;

    public ActiveStaffWebConfig(ActiveStaffInterceptor activeStaffInterceptor, CardGuardInterceptor cardGuardInterceptor) {
        this.activeStaffInterceptor = activeStaffInterceptor;
        this.cardGuardInterceptor = cardGuardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 검문소를 /api로 시작하는 모든 주소에 세우고, 아래 두 곳만 뺀다
        //  - /api/auth/** : 로그인은 로그인 전에 부르는 주소.
        //                   내 정보·비밀번호 변경은 AuthService가 이미 정지 여부를 확인함
        //  - /api/health  : 서버가 살아 있는지 보는 주소라 누구나 불러야 함
        registry.addInterceptor(activeStaffInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/health", "/api/cards/**");
        // 보호자 주소: 캐시 금지 + IP별 요청 수 제한
        registry.addInterceptor(cardGuardInterceptor).addPathPatterns("/api/cards/**");
    }//
}