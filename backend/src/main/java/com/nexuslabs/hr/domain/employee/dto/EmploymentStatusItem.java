package com.nexuslabs.hr.domain.employee.dto;

import com.nexuslabs.hr.domain.employee.entity.EmpStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 재직상태 이력 한 줄 — GET /api/employees/{id}/status-history 와 상태 변경 응답. 입사 이력은 createdBy 가 등록한 사람. */
public record EmploymentStatusItem(long id, EmpStatus status, LocalDate effectiveDate, String reason, Long createdById,
                                   String createdByName, OffsetDateTime createdAt) {
}
