package com.nexuslabs.hr.global.permission;

import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 권한 범위 계산(BR-AUTH-001, BR-EMP-003).
 * 팀 범위 = 내가 조직장인 활성 조직과 그 하위 조직 전체에 소속된 직원(퇴직자 포함 — 조회 대상).
 * 모든 쿼리에 company_id 조건을 직접 넣는다(JDBC는 @TenantId 자동 필터가 없다).
 */
@Component
public class ScopeResolver {

    private final JdbcTemplate jdbc;
    private final PermissionReader permissionReader;

    public ScopeResolver(JdbcTemplate jdbc, PermissionReader permissionReader) {
        this.jdbc = jdbc;
        this.permissionReader = permissionReader;
    }

    /** 권한 코드가 없으면 403. 전사면 Scope.company(), 팀이면 팀 직원 집합. */
    public Scope scopeOf(LoginUser user, PermissionCode code) {
        return findScope(user, code).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    /** 권한 코드가 없으면 빈 값(본인 조회와 섞이는 API에서 사용). */
    public Optional<Scope> findScope(LoginUser user, PermissionCode code) {
        PermissionScope scope = permissionReader.permissionsOf(user).get(code);
        if (scope == null) {
            return Optional.empty();
        }
        return Optional.of(scope == PermissionScope.ALL ? Scope.company() : Scope.team(teamEmployeeIds(user)));
    }

    /** 내가 조직장인 활성 조직 ID 목록. 비어 있으면 조직장이 아니다. */
    public List<Long> leadOrgUnitIds(LoginUser user) {
        return jdbc.queryForList("""
                        SELECT id FROM org_unit
                        WHERE company_id = ? AND lead_employee_id = ? AND is_active
                        ORDER BY id
                        """,
                Long.class, user.companyId(), user.employeeId());
    }

    public boolean isOrgLead(LoginUser user) {
        return !leadOrgUnitIds(user).isEmpty();
    }

    /** 메뉴 노출 기준(메뉴 구조 M-1): 전사이거나, 팀이면서 조직장일 때 쓸 수 있다. */
    public boolean usable(LoginUser user, PermissionCode code) {
        Map<PermissionCode, PermissionScope> granted = permissionReader.permissionsOf(user);
        PermissionScope scope = granted.get(code);
        return scope == PermissionScope.ALL || (scope == PermissionScope.TEAM && isOrgLead(user));
    }

    private Set<Long> teamEmployeeIds(LoginUser user) {
        List<Long> ids = jdbc.queryForList("""
                        WITH RECURSIVE team AS (
                            SELECT id FROM org_unit
                            WHERE company_id = ? AND lead_employee_id = ? AND is_active
                            UNION
                            SELECT o.id FROM org_unit o JOIN team t ON o.parent_id = t.id
                            WHERE o.company_id = ?
                        )
                        SELECT e.id FROM employee e
                        WHERE e.company_id = ? AND e.org_unit_id IN (SELECT id FROM team)
                        """,
                Long.class, user.companyId(), user.employeeId(), user.companyId(), user.companyId());
        return new HashSet<>(ids);
    }
}
