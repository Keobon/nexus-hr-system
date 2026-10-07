package com.nexuslabs.hr.domain.approval.dto;

import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.ApproverType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/approval-lines · PUT /api/approval-lines/{id} — 단계는 통째로 교체한다.
 * 조건은 직책(condJobTitleId) 또는 역할(condRoleId) 중 하나. 기본 승인선은 조건 없이 항상 활성.
 */
public record ApprovalLineRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull ApprovalWorkType workType,
        Long condJobTitleId,
        Long condRoleId,
        @NotNull Integer priority,
        @NotNull Boolean isActive,
        @NotNull @Size(min = 1, max = 5, message = "단계는 1개 이상 5개 이하입니다") List<@Valid @NotNull Step> steps) {

    public record Step(@NotNull @Min(1) @Max(5) Integer stepOrder, @NotNull ApproverType approverType,
                       @Min(1) Integer upLevels, Long jobTitleId, Long employeeId) {
    }
}
