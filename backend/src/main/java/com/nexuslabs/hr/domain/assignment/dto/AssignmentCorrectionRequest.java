package com.nexuslabs.hr.domain.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/assignments/{id}/corrections — 정정 발령(F-ASSIGN-04). 유형은 원본과 같다. */
public record AssignmentCorrectionRequest(
        @NotNull Long toOrgUnitId,
        @NotNull Long toJobGradeId,
        Long toJobTitleId,
        @NotBlank @Size(max = 255) String reason) {
}
