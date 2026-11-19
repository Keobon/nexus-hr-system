package com.nexuslabs.hr.domain.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/me/ai/overtime-drafts · /api/me/ai/trip-drafts 본문(휴가 초안 본문과 같은 모양).
 * draftId 가 있으면 그 초안을 이 문장으로 고친다(되묻기에 답할 때 등).
 */
public record AiDraftRequest(
        @NotBlank @Size(max = 500) String message,
        Long draftId) {
}
