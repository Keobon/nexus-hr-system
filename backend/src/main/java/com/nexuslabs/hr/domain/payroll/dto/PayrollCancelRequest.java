package com.nexuslabs.hr.domain.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/payroll-runs/{id}/cancel 본문 — 확정 취소 사유(필수). */
public record PayrollCancelRequest(@NotBlank @Size(max = 255) String reason) {
}
