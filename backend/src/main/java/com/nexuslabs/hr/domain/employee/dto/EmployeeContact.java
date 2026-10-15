package com.nexuslabs.hr.domain.employee.dto;

/**
 * 전 직원 연락처 한 줄(GET /api/employees/contacts). 화면은 "속성"으로 고른 칸(전화번호 · 이메일 · 주소)만 이름 · 직급 옆에 보여 준다.
 */
public record EmployeeContact(long id, String employeeNo, String name, String orgUnitName, String jobGradeName,
                              String jobTitleName, String email, String phone, String address) {
}
