package com.nexuslabs.hr.domain.leave.service;

/** DB ENUM leave_grant_type 과 이름이 같아야 한다. 정기·입사는 직원·종류·연도마다 합쳐서 1건(BR-LEAVE-007). */
public enum LeaveGrantType {
    REGULAR, HIRE, ADJUSTMENT
}
