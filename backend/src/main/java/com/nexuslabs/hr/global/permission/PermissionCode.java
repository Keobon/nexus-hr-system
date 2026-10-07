package com.nexuslabs.hr.global.permission;

import java.util.EnumSet;
import java.util.Set;

/**
 * 시스템이 정한 권한 코드 18개(기능명세서 부록 A). DB ENUM permission_code 와 이름이 같아야 한다.
 * 설명·관련 기능은 부록 A의 "할 수 있는 것"·"기능" 열과 같게 둔다. teamScopeAllowed=true 인 5개만 팀 범위를 고를 수 있다.
 */
public enum PermissionCode {
    COMPANY_MANAGE("회사 정보와 변경 이력 · 근무시간 · 휴일 · 초기 설정 · 회사 서류", "F-COMP-02–05 · 07", false),
    AUDIT_READ("감사 로그 조회", "F-COMP-06", false),
    ROLE_MANAGE("역할 관리 · 계정 역할 부여 · 계정 활성화", "F-AUTH-05·06", false),
    APPROVAL_MANAGE("승인선 관리 · 승인자 재지정", "F-APPR-01·02", false),
    ORG_MANAGE("조직 · 직급 · 직책 · 고용형태 · 직원 추가 항목 정의", "F-ORG-01·03·04, F-EMP-07", false),
    EMPLOYEE_READ("다른 직원 목록·상세 조회", "F-EMP-02·06", true),
    EMPLOYEE_MANAGE("직원 등록 · 수정 · 재직상태 변경 · 직원 서류 · 가족 정보 · 인사 메모 조회", "F-EMP-01·03·04·08·09", false),
    ATTENDANCE_READ("다른 직원 근태 · 연장근무 · 출장 · 출장 경비 조회", "F-ATT-03 · 06 · 07 · 08", true),
    ATTENDANCE_MANAGE("근태 정정 · 출장 경비 종류 관리", "F-ATT-04 · 09", false),
    LEAVE_READ("다른 직원 휴가 · 잔여 · 승인 진행 조회", "F-LEAVE-04·05, F-APPR-04", true),
    LEAVE_MANAGE("휴가 종류 · 휴가 부여", "F-LEAVE-01·02", false),
    PAYROLL_READ("다른 직원 급여 · 명세서 · 인건비 통계 조회", "F-PAY-04·06·07", false),
    PAYROLL_MANAGE("급여 항목 · 계산 변수 · 세율 · 직원 급여 등록 · 정산", "F-PAY-01·02·03·05", false),
    ASSIGNMENT_READ("다른 직원 발령 조회", "F-ASSIGN-02·03", true),
    ASSIGNMENT_MANAGE("발령 등록 · 정정", "F-ASSIGN-01·04", false),
    EVAL_READ("다른 직원의 확정된 평가 결과 조회", "F-EVAL-05", true),
    EVAL_MANAGE("평가 기간 · 템플릿 · 대상 · 확정 · 진행 현황", "F-EVAL-01·02·03·06", false),
    DASHBOARD_COMPANY("전사 집계 위젯", "F-DASH-02–04", false);

    private final String description;
    private final String relatedFeatures;
    private final boolean teamScopeAllowed;

    PermissionCode(String description, String relatedFeatures, boolean teamScopeAllowed) {
        this.description = description;
        this.relatedFeatures = relatedFeatures;
        this.teamScopeAllowed = teamScopeAllowed;
    }

    public String description() { return description; }

    /** 관련 기능 ID(예: "F-LEAVE-01·02") — 역할 편집 화면에 표시. */
    public String relatedFeatures() { return relatedFeatures; }

    public Set<PermissionScope> allowedScopes() {
        return teamScopeAllowed ? EnumSet.of(PermissionScope.TEAM, PermissionScope.ALL) : EnumSet.of(PermissionScope.ALL);
    }
}
