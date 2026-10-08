package com.nexuslabs.hr.domain.attendance.dto;

import jakarta.validation.constraints.NotBlank;

/** PUT /api/me/business-trips/{id}/report 본문 — 출장 결과 보고(글). */
public record BusinessTripReportRequest(@NotBlank String reportText) {
}
