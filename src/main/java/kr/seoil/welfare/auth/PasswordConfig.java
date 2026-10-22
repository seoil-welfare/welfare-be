package kr.seoil.welfare.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시 도구를 서버에 하나 등록한다.
 *
 * BCrypt는 같은 비밀번호라도 매번 다른 해시를 만든다(소금, salt). 그래서
 * 해시끼리 문자열 비교(equals)로는 확인할 수 없고 반드시 encoder.matches(원문, 해시)를 써야 한다.
 *
 * 기본 설정은 $2a$10$... 모양이고, db/dev/V2__dev_seed.sql의 데모 계정 해시도 같은 모양이다.
 * (그래서 가상 데이터의 demo1234 계정이 진짜 로그인으로도 그대로 들어간다)
 *
 * ※ Spring Security 전체(spring-boot-starter-security)를 넣지 않았다. 그것을 넣으면
 *   모든 주소에 로그인 필터가 자동으로 걸려서 이미 만들어 둔 일지·달력 API가 전부 막힌다.
 *   우리는 세션 방식을 직접 쓰고 있으므로(SessionStaff) 해시 계산 라이브러리만 가져왔다.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();          // 기본 강도 10
    }
}
