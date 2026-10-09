package com.nexuslabs.hr.domain.employee.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.nexuslabs.hr.domain.attendance.service.TodayStatus;
import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.Gender;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 직원 상세(GET /api/employees/{id}, F-EMP-06). 팀 범위면 위 필드만 내려가고 full 이 통째로 빠진다(Java null).
 * hrMemo 는 EMPLOYEE_MANAGE 가 있을 때만 — 권한이 없으면 필드 자체가 빠지고(Java null), 있는데 비어 있으면 null(빈 Optional).
 * 급여 계좌는 범위와 관계없이 넣지 않는다(F-PAY-03). todayStatus 는 퇴직자면 null 이다.
 */
public record EmployeeDetail(long id, String employeeNo, String name, String orgUnitName, String jobGradeName,
                             String jobTitleName, String employmentTypeName, EmpStatus status, String email,
                             String phone, Long profileFileId, TodayStatus todayStatus, MonthSummary monthSummary,
                             @JsonUnwrapped Full full,
                             @JsonInclude(JsonInclude.Include.NON_NULL) Optional<String> hrMemo) {

    /**
     * 전사 범위에서만 더해지는 항목. 부양가족 수 · 자녀 수는 가족 정보에서 센 값이다(BR-EMP-007, 가족 API 와 같은 계산) —
     * 가족 명단은 EMPLOYEE_MANAGE 만 보는 가족 API 에 있다.
     */
    public record Full(String nameEn, String address, LocalDate birthDate, Gender gender, String emergencyName,
                       String emergencyRelation, String emergencyPhone, LocalDate hireDate, long orgUnitId,
                       Long jobGradeId, Long jobTitleId, long employmentTypeId, boolean payrollEligible,
                       LocalDate contractEndDate, LocalDate probationEndDate,
                       List<MyProfileResponse.FieldValue> fieldValues, int dependentsCount, int childrenCount) {
    }

    /** 이번 달 근태 요약(F-EMP-06) — 지각 · 조퇴 횟수와 결근 · 재택 · 외근 일수. */
    public record MonthSummary(String month, int lateCount, int earlyLeaveCount, int absentDays, int remoteDays,
                               int fieldDays) {
    }
}
