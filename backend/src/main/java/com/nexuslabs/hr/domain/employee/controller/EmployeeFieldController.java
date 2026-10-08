package com.nexuslabs.hr.domain.employee.controller;

import com.nexuslabs.hr.domain.employee.dto.EmployeeFieldRequest;
import com.nexuslabs.hr.domain.employee.dto.EmployeeFieldResponse;
import com.nexuslabs.hr.domain.employee.dto.FieldValuesRequest;
import com.nexuslabs.hr.domain.employee.dto.MyProfileResponse;
import com.nexuslabs.hr.domain.employee.service.EmployeeFieldService;
import com.nexuslabs.hr.domain.employee.service.EmployeeFieldValueService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API 설계서 6장 — 직원 추가 항목 정의(F-EMP-07)와 값 교체(F-EMP-03). */
@RestController
public class EmployeeFieldController {

    private final EmployeeFieldService fieldService;
    private final EmployeeFieldValueService fieldValueService;

    public EmployeeFieldController(EmployeeFieldService fieldService, EmployeeFieldValueService fieldValueService) {
        this.fieldService = fieldService;
        this.fieldValueService = fieldValueService;
    }

    /** 로그인만 하면 볼 수 있다. 입력 폼은 activeOnly=true. */
    @GetMapping("/api/employee-fields")
    public ApiResponse<List<EmployeeFieldResponse>> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(fieldService.list(activeOnly));
    }

    @PostMapping("/api/employee-fields")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<EmployeeFieldResponse> create(@CurrentUser LoginUser user,
                                                     @Valid @RequestBody EmployeeFieldRequest request) {
        return ApiResponse.ok(fieldService.create(user, request));
    }

    /** 보낸 필드만 바꾼다. */
    @PatchMapping("/api/employee-fields/{id}")
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<EmployeeFieldResponse> update(@CurrentUser LoginUser user, @PathVariable long id,
                                                     @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(fieldService.update(user, id, patch));
    }

    @DeleteMapping("/api/employee-fields/{id}")
    @RequirePermission(PermissionCode.ORG_MANAGE)
    public ApiResponse<DeleteResult> delete(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(fieldService.delete(user, id));
    }

    /** 그 직원의 활성 항목 값 전체 교체. 응답 = 교체 후 값(개인 페이지 fieldValues 와 같은 모양). */
    @PutMapping("/api/employees/{id}/field-values")
    @RequirePermission(PermissionCode.EMPLOYEE_MANAGE)
    public ApiResponse<List<MyProfileResponse.FieldValue>> replace(@CurrentUser LoginUser user, @PathVariable long id,
                                                                   @Valid @RequestBody FieldValuesRequest request) {
        return ApiResponse.ok(fieldValueService.replace(user, id, request.values()));
    }

    /** 본인 수정 가능 항목만 교체. */
    @PutMapping("/api/me/field-values")
    public ApiResponse<List<MyProfileResponse.FieldValue>> replaceMine(@CurrentUser LoginUser user,
                                                                       @Valid @RequestBody FieldValuesRequest request) {
        return ApiResponse.ok(fieldValueService.replaceMine(user, request.values()));
    }
}
