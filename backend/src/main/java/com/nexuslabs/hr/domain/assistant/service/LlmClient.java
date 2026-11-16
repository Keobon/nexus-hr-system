package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;

/**
 * 휴가 신청 문장 → 휴가 정보 추출. 구현은 {@link AnthropicLlmClient}(Claude API)와 {@link FakeLlmClient}(규칙 기반).
 * 어느 쪽이 쓰일지는 {@link AiClientConfig}가 설정값으로 고른다. 결과는 사용자 입력과 같은 수준으로 믿지 않는다 —
 * 종류 · 날짜 · 반차는 {@link AiLeaveDraftService}가 다시 검사한다.
 */
public interface LlmClient {

    /** 실패하면 BusinessException(AI_UNAVAILABLE). */
    LeaveExtraction extractLeave(LeaveExtractionPrompt prompt);

    default boolean available() {
        return true;
    }

    /** 꺼져 있을 때(ai.enabled=false, 키 없음). 앱은 뜨고 초안 API 만 AI_UNAVAILABLE. */
    LlmClient DISABLED = new LlmClient() {
        @Override
        public LeaveExtraction extractLeave(LeaveExtractionPrompt prompt) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }

        @Override
        public boolean available() {
            return false;
        }
    };
}
