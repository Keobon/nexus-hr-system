package com.nexuslabs.hr.domain.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/eval-templates/{id}/copy — 복사본 이름. */
public record EvalTemplateCopyRequest(@NotBlank @Size(max = 50) String name) {
}
