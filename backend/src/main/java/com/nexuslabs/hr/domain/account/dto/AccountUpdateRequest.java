package com.nexuslabs.hr.domain.account.dto;

/** PATCH /api/accounts/{employeeId} — 보낸 값만 바꾼다. 둘 다 비울 수 없다(null 이면 VALIDATION_ERROR — AccountService.update). */
public record AccountUpdateRequest(Long roleId, Boolean active) {
}
