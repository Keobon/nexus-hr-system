package com.nexuslabs.hr.domain.account.dto;

import java.time.OffsetDateTime;

/**
 * GET /api/accounts 한 행. locked 는 지금 잠겨 있는지(lockedUntil 이 미래). resigned 는 퇴직한 직원(계정을 다시 켤 수 없다),
 * manageable 은 요청자가 이 계정의 역할 · 활성 · 비밀번호를 다룰 수 있는지(이 계정의 역할이 내 권한 안 — RoleGrantGuard, 2026-11-15).
 */
public record AccountRow(long employeeId, String employeeNo, String name, String email, String orgUnitName,
                         long roleId, String roleName, boolean active, boolean locked, OffsetDateTime lockedUntil,
                         OffsetDateTime lastLoginAt, boolean resigned, boolean manageable) {

    public AccountRow withManageable(boolean value) {
        return new AccountRow(employeeId, employeeNo, name, email, orgUnitName, roleId, roleName, active, locked,
                lockedUntil, lastLoginAt, resigned, value);
    }
}
