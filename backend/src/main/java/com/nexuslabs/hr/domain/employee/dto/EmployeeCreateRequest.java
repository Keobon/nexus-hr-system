package com.nexuslabs.hr.domain.employee.dto;

import com.nexuslabs.hr.domain.employee.entity.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * POST /api/employees — 직원 등록(F-EMP-01).
 * employeeNo 를 비우면 자동 채번, roleId 를 비우면 기본 역할 "직원", payrollEligible 을 비우면 급여 대상이다.
 * 입사일이 미래인지는 서버 시간대(Asia/Seoul)의 오늘과 비교해야 해서 서비스에서 확인한다.
 */
public record EmployeeCreateRequest(
        @Size(max = 30) String employeeNo,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 255) String address,
        @NotNull LocalDate hireDate,
        @NotNull Long orgUnitId,
        @NotNull Long jobGradeId,
        Long jobTitleId,
        @NotNull Long employmentTypeId,
        Long roleId,
        Boolean payrollEligible,
        @Size(max = 100) String nameEn,
        LocalDate birthDate,
        Gender gender,
        @Size(max = 50) String emergencyName,
        @Size(max = 20) String emergencyRelation,
        @Size(max = 20) String emergencyPhone,
        LocalDate contractEndDate,
        LocalDate probationEndDate,
        Long profileFileId,
        String hrMemo,
        @Valid List<FieldValueInput> fieldValues) {
}
