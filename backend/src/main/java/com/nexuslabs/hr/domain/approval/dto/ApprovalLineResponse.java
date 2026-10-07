package com.nexuslabs.hr.domain.approval.dto;

import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.ApproverType;

import java.util.List;

public record ApprovalLineResponse(long id, String name, ApprovalWorkType workType, Long condJobTitleId,
                                   String condJobTitleName, Long condRoleId, String condRoleName, int priority,
                                   boolean isDefault, boolean isActive, List<Step> steps) {

    public record Step(int stepOrder, ApproverType approverType, Integer upLevels, Long jobTitleId,
                       String jobTitleName, Long employeeId, String employeeName) {
    }
}
