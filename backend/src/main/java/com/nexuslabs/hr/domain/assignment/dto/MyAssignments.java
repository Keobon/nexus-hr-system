package com.nexuslabs.hr.domain.assignment.dto;

import java.util.List;

/** GET /api/me/assignments — 내 현재 값 + 이력(새 발령부터). */
public record MyAssignments(CurrentAssignment current, List<AssignmentItem> history) {
}
