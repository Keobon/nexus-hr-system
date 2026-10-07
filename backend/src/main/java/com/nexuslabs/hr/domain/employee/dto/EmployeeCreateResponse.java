package com.nexuslabs.hr.domain.employee.dto;

import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.leave.service.LeaveGrantType;

import java.util.List;

/**
 * 직원 등록 응답. temporaryPassword 는 이 응답에서 한 번만 내려간다 — 다시 조회하는 API 는 없다(F-AUTH-04).
 * leaveGrants 는 등록과 함께 만들어진 입사 휴가 부여 내역이다(F-LEAVE-02).
 */
public record EmployeeCreateResponse(long id, String employeeNo, String name, EmpStatus status,
                                     String temporaryPassword, List<LeaveGrant> leaveGrants) {

    public record LeaveGrant(String leaveTypeName, int leaveYear, LeaveGrantType grantType, int days) {
    }
}
