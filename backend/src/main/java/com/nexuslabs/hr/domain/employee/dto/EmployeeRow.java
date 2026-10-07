package com.nexuslabs.hr.domain.employee.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexuslabs.hr.domain.attendance.service.TodayStatus;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 직원 목록 한 줄(GET /api/employees). todayStatus 는 퇴직자면 null 이다.
 * annualSalary(연환산 연봉)는 PAYROLL_READ 가 있을 때만 내려간다 — 권한이 없으면 필드 자체가 빠지고(Java null),
 * 권한이 있는데 급여가 등록되지 않았으면 null 로 내려간다(빈 Optional). BR-PAY-016.
 */
public record EmployeeRow(long id, String employeeNo, String name, long orgUnitId, String orgUnitName,
                          String jobGradeName, String jobTitleName, String employmentTypeName, EmpStatus status,
                          TodayStatus todayStatus, LocalDate hireDate,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Optional<Long> annualSalary) {
}
