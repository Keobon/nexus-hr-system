package com.nexuslabs.hr.domain.assignment.dto;

import java.time.LocalDate;

/** 현재 발령(F-ASSIGN-02) — 지금 조직 · 직급 · 직책과 최근 발령일. 발령이 한 번도 없으면 lastAssignedDate 는 null. */
public record CurrentAssignment(long employeeId, String employeeNo, String name, long orgUnitId, String orgUnitName,
                                Long jobGradeId, String jobGradeName, Long jobTitleId, String jobTitleName,
                                LocalDate lastAssignedDate) {
}
