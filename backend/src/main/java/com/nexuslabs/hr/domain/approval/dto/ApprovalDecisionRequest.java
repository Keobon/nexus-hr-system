package com.nexuslabs.hr.domain.approval.dto;

import jakarta.validation.constraints.Size;

/** 승인 · 반려 본문. comment 는 반려면 필수. approvedMinutes 는 연장근무 승인만(앞 단계 값 이하, 비우면 그대로). */
public record ApprovalDecisionRequest(@Size(max = 500) String comment, Integer approvedMinutes) {
}
