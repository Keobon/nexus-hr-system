package com.nexuslabs.hr.domain.org.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/org-units/{id}/move — 새 상위 조직. */
public record OrgUnitMoveRequest(@NotNull Long parentId) {
}
