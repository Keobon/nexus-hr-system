package com.nexuslabs.hr.domain.approval.dto;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepStatus;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;

public record ReassignItem(long stepId, ApprovalWorkType workType, long targetId, long applicantId, String applicantName,
                           String title, int stepOrder, ApprovalStepStatus status, Long approverId, String approverName) {
}
