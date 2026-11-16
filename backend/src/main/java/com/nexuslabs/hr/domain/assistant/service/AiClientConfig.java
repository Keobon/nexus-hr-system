package com.nexuslabs.hr.domain.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 어떤 {@link LlmClient}를 쓸지 고른다(application.properties 의 ai.*).
 * <ul>
 *   <li>ai.enabled=false → 꺼짐(초안 API 만 AI_UNAVAILABLE)</li>
 *   <li>ai.client=fake → 규칙 기반 {@link FakeLlmClient}(테스트 · 키 없이 시연)</li>
 *   <li>ai.client=anthropic + ANTHROPIC_API_KEY → {@link AnthropicLlmClient}. 키가 없으면 꺼짐</li>
 * </ul>
 */
@Configuration
public class AiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiClientConfig.class);

    @Bean
    public LlmClient llmClient(@Value("${ai.enabled:true}") boolean enabled,
                               @Value("${ai.client:anthropic}") String client,
                               @Value("${ai.api-key:}") String apiKey,
                               @Value("${ai.model:claude-opus-5-5}") String model,
                               @Value("${ai.timeout-seconds:5}") long timeoutSeconds,
                               ObjectMapper objectMapper) {
        return choose(enabled, client, apiKey, model, Duration.ofSeconds(timeoutSeconds), objectMapper);
    }

    static LlmClient choose(boolean enabled, String client, String apiKey, String model, Duration timeout,
                            ObjectMapper objectMapper) {
        if (!enabled) {
            log.info("AI 비서 꺼짐(ai.enabled=false)");
            return LlmClient.DISABLED;
        }
        if ("fake".equalsIgnoreCase(client)) {
            log.info("AI 비서: 규칙 기반 가짜 클라이언트(ai.client=fake)");
            return new FakeLlmClient();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI 비서 꺼짐 — ANTHROPIC_API_KEY 가 없습니다");
            return LlmClient.DISABLED;
        }
        log.info("AI 비서: Claude API(model={})", model);
        return new AnthropicLlmClient(apiKey, model, timeout, objectMapper);
    }
}
