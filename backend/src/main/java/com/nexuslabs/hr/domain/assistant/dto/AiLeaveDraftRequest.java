package com.nexuslabs.hr.domain.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/me/ai/leave-drafts 본문. draftId 가 있으면 그 초안을 이 문장으로 고친다(되묻기에 답할 때 등).
 */
public record AiLeaveDraftRequest(
        @NotBlank @Size(max = 500) String message,
        Long draftId) {
}
