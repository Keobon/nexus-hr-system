package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;

import java.time.LocalDate;

/**
 * 문장 → 업무 분류 · 휴가 · 연장근무 · 출장 정보 추출. 구현은 {@link AnthropicLlmClient}(Claude API)와
 * {@link FakeLlmClient}(규칙 기반). 어느 쪽이 쓰일지는 {@link AiClientConfig}가 설정값으로 고른다.
 * 결과는 사용자 입력과 같은 수준으로 믿지 않는다 — 값은 각 초안 서비스가 다시 검사한다. 실패하면 BusinessException(AI_UNAVAILABLE).
 */
public interface LlmClient {

    LeaveExtraction extractLeave(LeaveExtractionPrompt prompt);

    /** 자유 입력 문장이 어느 업무인지(휴가 · 연장근무 · 출장 · 모름). */
    AiWorkType classify(LocalDate today, String message);

    OvertimeExtraction extractOvertime(OvertimeExtractionPrompt prompt);

    TripExtraction extractTrip(TripExtractionPrompt prompt);

    default boolean available() {
        return true;
    }

    /** 꺼져 있을 때(ai.enabled=false, 키 없음). 앱은 뜨고 AI API 만 AI_UNAVAILABLE. */
    LlmClient DISABLED = new LlmClient() {
        @Override
        public LeaveExtraction extractLeave(LeaveExtractionPrompt prompt) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }

        @Override
        public AiWorkType classify(LocalDate today, String message) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }

        @Override
        public OvertimeExtraction extractOvertime(OvertimeExtractionPrompt prompt) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }

        @Override
        public TripExtraction extractTrip(TripExtractionPrompt prompt) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }

        @Override
        public boolean available() {
            return false;
        }
    };
}
