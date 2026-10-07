package com.nexuslabs.hr.domain.account.dto;

import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionScope;

import java.util.List;

/** GET /api/me — 메뉴·화면 부트스트랩(API 설계서 2장). 값이 없는 필드는 null 로 내려간다. */
public record MeResponse(
        EmployeeSummary employee,
        CompanySummary company,
        RoleSummary role,
        List<PermissionGrant> permissions,
        boolean isOrgLead,
        List<Long> leadOrgUnitIds,
        boolean mustChangePassword,
        Todos todos) {

    public record EmployeeSummary(long id, String employeeNo, String name, long orgUnitId, String orgUnitName,
                                  String jobGradeName, String jobTitleName, Long profileFileId,
                                  boolean payrollEligible) {
    }

    public record CompanySummary(long id, String name, Long logoFileId, boolean setupCompleted) {
    }

    public record RoleSummary(long id, String name) {
    }

    public record PermissionGrant(PermissionCode code, PermissionScope scope) {
    }

    /** null 이면 그 배지를 볼 권한이 없다는 뜻. */
    public record Todos(long approvalsPending, long evaluationsToSubmit, Long reassignNeeded,
                        Long attendanceCorrections) {
    }
}
