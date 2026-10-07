package com.nexuslabs.hr.domain.approval.service;

/** DB ENUM approver_type. 필요한 값: ORG_LEAD(없음) · ORG_LEAD_UP(upLevels) · JOB_TITLE(jobTitleId) · EMPLOYEE(employeeId). */
public enum ApproverType {
    ORG_LEAD, ORG_LEAD_UP, JOB_TITLE, EMPLOYEE
}
