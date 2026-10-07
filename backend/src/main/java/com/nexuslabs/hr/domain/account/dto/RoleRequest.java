package com.nexuslabs.hr.domain.account.dto;

import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.PermissionScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** POST /api/roles · PUT /api/roles/{id} — 권한 목록은 통째로 교체한다. */
public record RoleRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 255) String description,
        @NotNull List<@Valid @NotNull Grant> permissions) {

    public record Grant(@NotNull PermissionCode code, @NotNull PermissionScope scope) {
    }
}
