package com.nexuslabs.hr.global.permission;

import java.util.EnumSet;
import java.util.Set;

/**
 * 시스템이 정한 권한 코드 18개(기능명세서 부록 A). DB ENUM permission_code 와 이름이 같아야 한다.
 * teamScopeAllowed=true 인 5개만 팀 범위를 고를 수 있다.
 */
public enum PermissionCode {
    COMPANY_MANAGE("회사 정보 · 근무시간 · 휴일 · 초기 설정 · 회사 서류", false),
    AUDIT_READ("감사 로그 조회", false),
    ROLE_MANAGE("역할 관리 · 계정 역할 부여 · 계정 활성화", false),
    APPROVAL_MANAGE("승인선 관리 · 승인자 재지정", false),
    ORG_MANAGE("조직 · 직급 · 직책 · 고용형태 · 직원 추가 항목 정의", false),
    EMPLOYEE_READ("다른 직원 목록·상세 조회", true),
    EMPLOYEE_MANAGE("직원 등록 · 수정 · 재직상태 변경 · 직원 서류 · 가족 정보", false),
    ATTENDANCE_READ("다른 직원 근태 · 연장근무 · 출장 · 출장 경비 조회", true),
    ATTENDANCE_MANAGE("근태 정정 · 출장 경비 종류 관리", false),
    LEAVE_READ("다른 직원 휴가 · 잔여 · 승인 진행 조회", true),
    LEAVE_MANAGE("휴가 종류 · 휴가 부여", false),
    PAYROLL_READ("다른 직원 급여 · 명세서 · 인건비 통계 조회", false),
    PAYROLL_MANAGE("급여 항목 · 계산 변수 · 세율 · 직원 급여 등록 · 정산", false),
    ASSIGNMENT_READ("다른 직원 발령 조회", true),
    ASSIGNMENT_MANAGE("발령 등록 · 정정", false),
    EVAL_READ("다른 직원의 확정된 평가 결과 조회", true),
    EVAL_MANAGE("평가 기간 · 템플릿 · 대상 · 확정 · 진행 현황", false),
    DASHBOARD_COMPANY("전사 집계 위젯", false);

    private final String description;
    private final boolean teamScopeAllowed;

    PermissionCode(String description, boolean teamScopeAllowed) {
        this.description = description;
        this.teamScopeAllowed = teamScopeAllowed;
    }

    public String description() { return description; }

    public Set<PermissionScope> allowedScopes() {
        return teamScopeAllowed ? EnumSet.of(PermissionScope.TEAM, PermissionScope.ALL) : EnumSet.of(PermissionScope.ALL);
    }
}
