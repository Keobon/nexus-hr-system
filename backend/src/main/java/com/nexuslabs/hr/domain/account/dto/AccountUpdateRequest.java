package com.nexuslabs.hr.domain.account.dto;

/** PATCH /api/accounts/{employeeId} — 보낸 값만 바꾼다(null 이면 그대로). */
public record AccountUpdateRequest(Long roleId, Boolean active) {
}
