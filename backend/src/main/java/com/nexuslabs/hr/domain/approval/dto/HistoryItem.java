package com.nexuslabs.hr.domain.approval.dto;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepStatus;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;

import java.time.OffsetDateTime;

public record HistoryItem(long stepId, ApprovalWorkType workType, long targetId, long applicantId, String applicantName,
                          String title, ApprovalStepStatus status, Integer approvedMinutes, String comment,
                          OffsetDateTime actedAt) {
}
