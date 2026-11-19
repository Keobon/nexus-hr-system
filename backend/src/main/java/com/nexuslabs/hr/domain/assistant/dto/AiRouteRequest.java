package com.nexuslabs.hr.domain.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/me/ai/route 본문 — 칩을 고르지 않고 바로 쓴 문장. */
public record AiRouteRequest(@NotBlank @Size(max = 500) String message) {
}
