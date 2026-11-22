package com.nexuslabs.hr.domain.assistant.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalPlan;
import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.assistant.dto.DraftApprovalStep;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 초안 미리보기의 승인선 — 승인 엔진의 미리보기(ApprovalService.preview, 저장 안 함)를 그대로 쓴다.
 * 승인자를 못 찾으면(APPROVER_NOT_FOUND) 신청할 때와 같이 오류로 모으고 빈 승인선을 돌려준다.
 */
@Component
public class DraftApprovalPreview {

    private final ApprovalService approvalService;

    public DraftApprovalPreview(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    public List<DraftApprovalStep> steps(long companyId, ApprovalWorkType workType, long applicantId,
                                         List<String> errors) {
        try {
            ApprovalPlan plan = approvalService.preview(companyId, workType, applicantId);
            return plan.steps().stream()
                    .map(s -> new DraftApprovalStep(s.stepOrder(), s.approverId(), s.approverName(), s.skipped(),
                            s.stepLabel()))
                    .toList();
        } catch (BusinessException e) {
            if (e.code() != ErrorCode.APPROVER_NOT_FOUND) {
                throw e;
            }
            errors.add(e.code().name());
            return List.of();
        }
    }
}
