package com.nexuslabs.hr.domain.employee.controller;

import com.nexuslabs.hr.domain.employee.dto.EmployeeDocumentGroup;
import com.nexuslabs.hr.domain.employee.dto.EmployeeDocumentRequest;
import com.nexuslabs.hr.domain.employee.service.EmployeeDocumentService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API 설계서 6장 — 직원 서류(F-EMP-08). 삭제는 없다. */
@RestController
public class EmployeeDocumentController {

    private final EmployeeDocumentService documentService;

    public EmployeeDocumentController(EmployeeDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/api/employees/{id}/documents")
    @RequirePermission(PermissionCode.EMPLOYEE_MANAGE)
    public ApiResponse<List<EmployeeDocumentGroup>> list(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(documentService.list(user.companyId(), id));
    }

    /** 새 버전 등록 — 같은 종류의 현재본은 이전 버전이 된다. */
    @PostMapping("/api/employees/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.EMPLOYEE_MANAGE)
    public ApiResponse<EmployeeDocumentGroup> create(@CurrentUser LoginUser user, @PathVariable long id,
                                                     @Valid @RequestBody EmployeeDocumentRequest request) {
        return ApiResponse.ok(documentService.create(user, id, request));
    }

    /** 본인은 조회만. */
    @GetMapping("/api/me/documents")
    public ApiResponse<List<EmployeeDocumentGroup>> mine(@CurrentUser LoginUser user) {
        return ApiResponse.ok(documentService.list(user.companyId(), user.employeeId()));
    }
}
