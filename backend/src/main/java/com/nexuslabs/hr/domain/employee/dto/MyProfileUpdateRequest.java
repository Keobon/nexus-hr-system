package com.nexuslabs.hr.domain.employee.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH /api/me/profile — 본인이 고칠 수 있는 항목만(F-EMP-03). 모두 비울 수 있다(null).
 * 급여 계좌와 추가 항목 값은 따로 있다(/me/bank-account · /me/field-values).
 */
public record MyProfileUpdateRequest(
        @Size(max = 20) String phone,
        @Size(max = 255) String address,
        @Size(max = 100) String nameEn,
        @Size(max = 50) String emergencyName,
        @Size(max = 20) String emergencyRelation,
        @Size(max = 20) String emergencyPhone,
        Long profileFileId) {
}
