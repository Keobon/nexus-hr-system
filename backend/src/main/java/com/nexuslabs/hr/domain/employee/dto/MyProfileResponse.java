package com.nexuslabs.hr.domain.employee.dto;

import com.nexuslabs.hr.domain.employee.entity.EmpStatus;
import com.nexuslabs.hr.domain.employee.entity.FieldType;
import com.nexuslabs.hr.domain.employee.entity.Gender;

import java.time.LocalDate;
import java.util.List;

/**
 * 개인 페이지(GET /api/me/profile, F-EMP-05). 인사 메모와 급여 계좌는 넣지 않는다.
 * leaveBalances 는 이번 휴가 연도의 종류별 잔여다(홈의 me.leaveBalances 와 같은 모양).
 */
public record MyProfileResponse(long id, String employeeNo, String name, String nameEn, String email, String phone,
                                String address, LocalDate birthDate, Gender gender, String emergencyName,
                                String emergencyRelation, String emergencyPhone, LocalDate hireDate, long orgUnitId,
                                String orgUnitName, String jobGradeName, String jobTitleName,
                                String employmentTypeName, EmpStatus status, boolean payrollEligible,
                                LocalDate contractEndDate, LocalDate probationEndDate, Long profileFileId,
                                List<FieldValue> fieldValues, List<LeaveBalance> leaveBalances) {

    public record FieldValue(long fieldDefId, String name, FieldType fieldType, int seq, String value) {
    }

    public record LeaveBalance(String leaveTypeName, int remaining) {
    }
}
