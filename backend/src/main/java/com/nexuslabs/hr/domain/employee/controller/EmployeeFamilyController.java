package com.nexuslabs.hr.domain.employee.controller;

import com.nexuslabs.hr.domain.employee.dto.FamilyList;
import com.nexuslabs.hr.domain.employee.dto.FamilyMember;
import com.nexuslabs.hr.domain.employee.dto.FamilyRequest;
import com.nexuslabs.hr.domain.employee.service.EmployeeFamilyService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** API 설계서 6장 — 가족 정보(F-EMP-09). */
@RestController
public class EmployeeFamilyController {

    private final EmployeeFamilyService familyService;

    public EmployeeFamilyController(EmployeeFamilyService familyService) {
        this.familyService = familyService;
    }

    @GetMapping("/api/employees/{id}/family")
    @RequirePermission(PermissionCode.EMPLOYEE_MANAGE)
    public ApiResponse<FamilyList> list(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(familyService.list(user.companyId(), id));
    }

    @PostMapping("/api/employees/{id}/family")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.EMPLOYEE_MANAGE)
    public ApiResponse<FamilyMember> create(@CurrentUser LoginUser user, @PathVariable long id,
                                            @Valid @RequestBody FamilyRequest request) {
        return ApiResponse.ok(familyService.create(user, id, request));
    }

    /** 보낸 필드만 바꾼다. */
    @PatchMapping("/api/employees/{id}/family/{familyId}")
    @RequirePermission(PermissionCode.EMPLOYEE_MANAGE)
    public ApiResponse<FamilyMember> update(@CurrentUser LoginUser user, @PathVariable long id,
                                            @PathVariable long familyId, @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(familyService.update(user, id, familyId, patch));
    }

    @DeleteMapping("/api/employees/{id}/family/{familyId}")
    @RequirePermission(PermissionCode.EMPLOYEE_MANAGE)
    public ApiResponse<DeleteResult> delete(@CurrentUser LoginUser user, @PathVariable long id,
                                            @PathVariable long familyId) {
        return ApiResponse.ok(familyService.delete(user, id, familyId));
    }

    /** 본인은 조회만. */
    @GetMapping("/api/me/family")
    public ApiResponse<FamilyList> mine(@CurrentUser LoginUser user) {
        return ApiResponse.ok(familyService.list(user.companyId(), user.employeeId()));
    }
}
