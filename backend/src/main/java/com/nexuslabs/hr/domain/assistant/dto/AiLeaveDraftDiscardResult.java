package com.nexuslabs.hr.domain.assistant.dto;

import com.nexuslabs.hr.domain.assistant.entity.AiDraftStatus;

/** POST /api/me/ai/leave-drafts/{id}/discard 응답. */
public record AiLeaveDraftDiscardResult(long draftId, AiDraftStatus status) {
}
