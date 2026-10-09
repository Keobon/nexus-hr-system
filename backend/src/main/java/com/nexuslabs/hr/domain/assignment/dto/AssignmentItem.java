package com.nexuslabs.hr.domain.assignment.dto;

import com.nexuslabs.hr.domain.assignment.entity.AssignmentType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 발령 이력 한 줄(F-ASSIGN-03). 정정 건은 correctionOfId 와 원본 요약(correctionOf), 정정된 건은 correctedById
 * (그 건을 정정한 행 — 화면은 취소선). 변경 전 값은 등록 때 직원의 값이라 처음 직급이 없던 사람은 null 일 수 있다.
 */
public record AssignmentItem(long id, long employeeId, String employeeNo, String employeeName,
                             AssignmentType assignmentType, LocalDate effectiveDate,
                             Long fromOrgUnitId, String fromOrgUnitName, Long fromJobGradeId, String fromJobGradeName,
                             Long fromJobTitleId, String fromJobTitleName,
                             long toOrgUnitId, String toOrgUnitName, long toJobGradeId, String toJobGradeName,
                             Long toJobTitleId, String toJobTitleName,
                             String reason, Long correctionOfId, Original correctionOf, Long correctedById,
                             long createdById, String createdByName, OffsetDateTime createdAt) {

    /** 정정 대상 원본 요약. */
    public record Original(long id, AssignmentType assignmentType, LocalDate effectiveDate, String toOrgUnitName,
                           String toJobGradeName, String toJobTitleName, String reason) {
    }
}
