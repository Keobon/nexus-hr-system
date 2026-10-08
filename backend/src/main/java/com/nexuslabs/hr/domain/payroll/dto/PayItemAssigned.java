package com.nexuslabs.hr.domain.payroll.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 직원별 항목 등록 응답(201). warning 은 기본 급여 등록과 같다. */
public record PayItemAssigned(long id, long payItemId, long amount, LocalDate effectiveFrom, String reason,
                              String createdByName, OffsetDateTime createdAt, String warning) {
}
