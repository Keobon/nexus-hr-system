package com.nexuslabs.hr.domain.assignment.dto;

import com.nexuslabs.hr.domain.assignment.entity.AssignmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/assignments — 발령 등록(F-ASSIGN-01). 발효일 필드는 없다(서버가 오늘로, BR-ASSIGN-004).
 * 변경 후 값 셋을 모두 보낸다(화면은 현재 값으로 미리 채운다). 직책은 비울 수 있다.
 */
public record AssignmentRequest(
        @NotNull Long employeeId,
        @NotNull AssignmentType assignmentType,
        @NotNull Long toOrgUnitId,
        @NotNull Long toJobGradeId,
        Long toJobTitleId,
        @NotBlank @Size(max = 255) String reason) {
}
