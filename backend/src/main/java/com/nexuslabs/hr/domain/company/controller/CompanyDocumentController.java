package com.nexuslabs.hr.domain.company.controller;

import com.nexuslabs.hr.domain.company.dto.CompanyDocumentGroup;
import com.nexuslabs.hr.domain.company.dto.CompanyDocumentRequest;
import com.nexuslabs.hr.domain.company.service.CompanyDocumentService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API 설계서 3장 — 회사 서류(F-COMP-07). 삭제는 없다. */
@RestController
public class CompanyDocumentController {

    private final CompanyDocumentService documentService;

    public CompanyDocumentController(CompanyDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/api/company/documents")
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<List<CompanyDocumentGroup>> list(@CurrentUser LoginUser user) {
        return ApiResponse.ok(documentService.list(user.companyId()));
    }

    /** 새 버전 등록 — 같은 종류의 현재본은 이전 버전이 된다. */
    @PostMapping("/api/company/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<CompanyDocumentGroup> create(@CurrentUser LoginUser user,
                                                    @Valid @RequestBody CompanyDocumentRequest request) {
        return ApiResponse.ok(documentService.create(user, request));
    }
}
