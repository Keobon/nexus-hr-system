package com.nexuslabs.hr.domain.employee.dto;

import com.nexuslabs.hr.domain.employee.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * PATCH /api/employees/{id} — 관리자가 고칠 수 있는 항목(F-EMP-03). 소속 조직 · 직급 · 직책은 발령으로만(BR-EMP-001),
 * 사원번호는 바꿀 수 없다(BR-EMP-006). 추가 항목 값은 PUT /employees/{id}/field-values 로 따로 바꾼다.
 */
public record EmployeeUpdateRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 255) String address,
        @NotNull Long employmentTypeId,
        @NotNull Boolean payrollEligible,
        @Size(max = 100) String nameEn,
        LocalDate birthDate,
        Gender gender,
        @Size(max = 50) String emergencyName,
        @Size(max = 20) String emergencyRelation,
        @Size(max = 20) String emergencyPhone,
        LocalDate contractEndDate,
        LocalDate probationEndDate,
        Long profileFileId,
        String hrMemo) {
}
