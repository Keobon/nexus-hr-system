package com.nexuslabs.hr.domain.approval.dto;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;

import java.util.List;
import java.util.Map;

/** 승인함 한 건 — 현재 단계는 stepOrder/totalSteps(예: 2/3), 앞 단계 처리 내역은 previousSteps. */
public record InboxItem(long stepId, ApprovalWorkType workType, long targetId, long applicantId, String applicantName,
                        String applicantOrgUnitName, String title, Map<String, Object> details, Integer requestedMinutes,
                        int stepOrder, int totalSteps, List<ApprovalStepView> previousSteps) {
}
