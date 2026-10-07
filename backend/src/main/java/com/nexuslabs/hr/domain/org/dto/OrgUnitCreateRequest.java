package com.nexuslabs.hr.domain.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** POST /api/org-units. sortOrder 를 비우면 같은 상위 조직 아래 맨 뒤에 놓는다. */
public record OrgUnitCreateRequest(
        @NotNull Long parentId,
        @NotBlank @Size(max = 50) String name,
        @Size(max = 30) String levelName,
        @PositiveOrZero Long monthlyBudget,
        Integer sortOrder) {
}
