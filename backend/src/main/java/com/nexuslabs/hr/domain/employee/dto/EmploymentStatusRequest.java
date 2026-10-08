package com.nexuslabs.hr.domain.employee.dto;

import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/employees/{id}/status 본문(F-EMP-04). effectiveDate 부터 그 상태다 — 퇴직이면 마지막 근무일 다음 날.
 * 미래 · 입사일 이전 · 마지막 이력의 발효일 이전은 VALIDATION_ERROR(fields.effectiveDate).
 */
public record EmploymentStatusRequest(
        @NotNull EmpStatus status,
        @NotNull LocalDate effectiveDate,
        @NotBlank @Size(max = 255) String reason) {
}
