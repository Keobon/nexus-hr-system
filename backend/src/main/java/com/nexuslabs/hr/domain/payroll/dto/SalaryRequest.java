package com.nexuslabs.hr.domain.payroll.dto;

import com.nexuslabs.hr.domain.payroll.entity.SalaryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/employees/{id}/salaries 본문(F-PAY-03). 연봉제는 annualSalary 만, 월급제는 monthlyBase 만 쓴다(다른 쪽은 무시).
 * 연봉제의 월 기본급은 서버가 연봉 ÷ 적용 시작일에 유효한 연봉 분할 개월 수(내림)로 정한다.
 */
public record SalaryRequest(
        @NotNull SalaryType salaryType,
        Long annualSalary,
        Long monthlyBase,
        @NotNull LocalDate effectiveFrom,
        @NotBlank @Size(max = 255) String reason) {
}
