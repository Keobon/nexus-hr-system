package com.nexuslabs.hr.domain.account.dto;

import java.time.OffsetDateTime;

/** GET /api/accounts 한 행. locked 는 지금 잠겨 있는지(lockedUntil 이 미래). */
public record AccountRow(long employeeId, String employeeNo, String name, String email, String orgUnitName,
                         long roleId, String roleName, boolean active, boolean locked, OffsetDateTime lockedUntil,
                         OffsetDateTime lastLoginAt) {
}
