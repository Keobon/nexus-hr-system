package com.nexuslabs.hr.domain.approval.service;

/** DB ENUM approval_work_type 과 이름이 같아야 한다. LEAVE_CANCEL 은 승인선이 없다(원래 휴가의 마지막 승인자). */
public enum ApprovalWorkType {
    LEAVE, LEAVE_CANCEL, OVERTIME, BUSINESS_TRIP, TRIP_EXPENSE
}
