package com.nexuslabs.hr.domain.employee.dto;

import java.util.List;

/**
 * GET /api/employees/{id}/family · /api/me/family (F-EMP-09, BR-EMP-007). 두 숫자는 계산값이다 —
 * 부양가족 수 = 공제 대상인 가족 수, 자녀 수 = 관계가 자녀이고 공제 대상인 가족 수. 정산(F-PAY-05)이 같은 식으로 센다.
 */
public record FamilyList(int dependentsCount, int childrenCount, List<FamilyMember> members) {
}
