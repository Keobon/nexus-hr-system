package com.nexuslabs.hr.domain.employee.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexuslabs.hr.domain.employee.entity.FamilyRelation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/employees/{id}/family 본문(F-EMP-09). 비운 선택 값: isTaxDependent · isDisabled = false, isCohabiting = true.
 * PATCH 는 보낸 필드만 바꾸므로 본문을 Map 으로 받아 이 모양으로 합친다 — EmployeeFamilyService.update.
 */
public record FamilyRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull FamilyRelation relation,
        LocalDate birthDate,
        @JsonProperty("isTaxDependent") Boolean taxDependent,
        @JsonProperty("isDisabled") Boolean disabled,
        @JsonProperty("isCohabiting") Boolean cohabiting) {
}
