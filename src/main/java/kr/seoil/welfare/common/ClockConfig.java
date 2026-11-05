package kr.seoil.welfare.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * '오늘'을 알려 주는 시계 (한국 시간).
 * 서비스가 LocalDate.now() 대신 이 시계를 받아 쓰면, 테스트에서 날짜를 고정해 둘 수 있다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
