package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.domain.assistant.dto.AiRouteResponse;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 자유 입력 문장의 업무 분류(2026-11-19) — 챗봇 첫 화면에서 칩을 고르지 않고 바로 말로 쓴 경우.
 * 저장하지 않는다. LLM 호출이라 초안 API 와 같은 호출 제한(직원당 분당 합산)을 쓴다.
 */
@Service
public class AiRouteService {

    private final LlmClient llm;
    private final AiRateLimiter rateLimiter;
    private final Clock clock;

    public AiRouteService(LlmClient llm, AiRateLimiter rateLimiter, Clock clock) {
        this.llm = llm;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public AiRouteResponse route(LoginUser user, String message) {
        if (!llm.available()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        rateLimiter.acquire(user.employeeId());
        AiWorkType type = llm.classify(LocalDate.now(clock), message.strip());
        return new AiRouteResponse(type == null ? AiWorkType.UNKNOWN : type);
    }
}
