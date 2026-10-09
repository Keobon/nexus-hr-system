package com.nexuslabs.hr.domain.account.dto;

public record RoleListItem(long id, String name, String description, boolean isSystem, long accountCount) {
}
