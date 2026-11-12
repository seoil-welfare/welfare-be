package kr.seoil.welfare.card;

import java.time.Duration;
import java.util.concurrent.Executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.seoil.welfare.card.ai.CardGenerator;
import kr.seoil.welfare.card.ai.OpenAiCardGenerator;
import kr.seoil.welfare.card.ai.RuleCardGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 오늘 카드 설정
 *  - AI: OPENAI_API_KEY가 있으면 OpenAI, 없으면 정해진 문장(RuleCardGenerator)
 *  - 비동기: 카드 생성은 일지 저장 응답과 따로 돈다 (@Async, 전용 스레드 2개)
 */
@Configuration
@EnableAsync
public class CardConfig {

    private static final Logger log = LoggerFactory.getLogger(CardConfig.class);

    @Bean
    public CardGenerator cardGenerator(@Value("${card.ai.api-key:}") String apiKey,
                                       @Value("${card.ai.model:gpt-5.4-mini}") String model,
                                       @Value("${card.ai.reasoning-effort:low}") String reasoningEffort,
                                       @Value("${card.ai.timeout:30s}") Duration timeout,
                                       ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("오늘 카드: OPENAI_API_KEY가 없어 정해진 문장으로 만듭니다 (RuleCardGenerator)");
            return new RuleCardGenerator();
        }
        log.info("오늘 카드: OpenAI {} 사용", model);
        return new OpenAiCardGenerator(apiKey.trim(), model, reasoningEffort, timeout, objectMapper);
    }

    @Bean(name = "cardExecutor")
    public Executor cardExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("card-");
        executor.initialize();
        return executor;
    }
}
