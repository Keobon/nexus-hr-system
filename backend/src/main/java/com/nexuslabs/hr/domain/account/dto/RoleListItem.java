package com.nexuslabs.hr.domain.account.dto;

/** grantable — 요청자가 이 역할을 계정 · 직원 등록에서 줄 수 있는지(2026-11-15). */
public record RoleListItem(long id, String name, String description, boolean isSystem, long accountCount,
                           boolean grantable) {
}
