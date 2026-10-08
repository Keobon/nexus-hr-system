package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.SalaryType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 기본 급여 등록 응답(201). 적용 시작일이 이미 정산된 월에 걸치면 warning = "PAY_MONTH_SETTLED" — 등록은 됐고 정산된 명세서는
 * 바뀌지 않는다(차액은 다음 달 수동 입력 항목으로 소급 조정).
 */
public record SalaryRegistered(long id, SalaryType salaryType, Long annualSalary, long monthlyBase,
                               LocalDate effectiveFrom, String reason, String createdByName, OffsetDateTime createdAt,
                               String warning) {
}
