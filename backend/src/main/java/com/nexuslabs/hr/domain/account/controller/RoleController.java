package com.nexuslabs.hr.domain.account.controller;

import com.nexuslabs.hr.domain.account.dto.PermissionInfo;
import com.nexuslabs.hr.domain.account.dto.RoleDetail;
import com.nexuslabs.hr.domain.account.dto.RoleListItem;
import com.nexuslabs.hr.domain.account.dto.RoleRequest;
import com.nexuslabs.hr.domain.account.service.RoleService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.DeleteResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API 설계서 4장 — 권한 코드 · 역할. */
@RestController
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/api/permissions")
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public ApiResponse<List<PermissionInfo>> permissions() {
        return ApiResponse.ok(roleService.permissions());
    }

    /** 직원 등록 화면의 역할 선택에도 쓴다. */
    @GetMapping("/api/roles")
    @RequirePermission({PermissionCode.ROLE_MANAGE, PermissionCode.EMPLOYEE_MANAGE})
    public ApiResponse<List<RoleListItem>> list(@CurrentUser LoginUser user) {
        return ApiResponse.ok(roleService.list(user));
    }

    @GetMapping("/api/roles/{id}")
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public ApiResponse<RoleDetail> get(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(roleService.get(user, id));
    }

    @PostMapping("/api/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public ApiResponse<RoleDetail> create(@CurrentUser LoginUser user, @Valid @RequestBody RoleRequest request) {
        return ApiResponse.ok(roleService.create(user, request));
    }

    @PutMapping("/api/roles/{id}")
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public ApiResponse<RoleDetail> update(@CurrentUser LoginUser user, @PathVariable long id,
                                          @Valid @RequestBody RoleRequest request) {
        return ApiResponse.ok(roleService.update(user, id, request));
    }

    @DeleteMapping("/api/roles/{id}")
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public ApiResponse<DeleteResult> delete(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(roleService.delete(user, id));
    }
}
