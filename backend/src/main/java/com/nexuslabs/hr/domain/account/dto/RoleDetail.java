package com.nexuslabs.hr.domain.account.dto;

import java.util.List;

public record RoleDetail(long id, String name, String description, boolean isSystem, long accountCount,
                         List<MeResponse.PermissionGrant> permissions) {
}
