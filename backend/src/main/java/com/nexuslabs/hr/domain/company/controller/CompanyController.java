package com.nexuslabs.hr.domain.company.controller;

import com.nexuslabs.hr.domain.company.dto.CompanyChangeHistoryItem;
import com.nexuslabs.hr.domain.company.dto.CompanyDetailResponse;
import com.nexuslabs.hr.domain.company.dto.CompanyView;
import com.nexuslabs.hr.domain.company.dto.SetupStatusResponse;
import com.nexuslabs.hr.domain.company.service.CompanyService;
import com.nexuslabs.hr.domain.company.service.CompanySetupService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API 설계서 3장 — 회사 기본정보와 변경 이력(F-COMP-02). */
@RestController
@RequestMapping("/api/company")
public class CompanyController {

    private final CompanyService companyService;
    private final CompanySetupService companySetupService;

    public CompanyController(CompanyService companyService, CompanySetupService companySetupService) {
        this.companyService = companyService;
        this.companySetupService = companySetupService;
    }

    /** 로그인만 하면 본다. COMPANY_MANAGE 가 없으면 표시용 필드만 내려간다. */
    @GetMapping
    public ApiResponse<CompanyView> get(@CurrentUser LoginUser user) {
        return ApiResponse.ok(companyService.get(user));
    }

    /** 보낸 필드만 바꾼다. "안 보냄"과 "null(비움)"을 구별해야 해서 본문을 Map 으로 받는다. */
    @PatchMapping
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<CompanyDetailResponse> update(@CurrentUser LoginUser user,
                                                     @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(companyService.update(user, patch));
    }

    /** 초기 설정 마법사의 단계별 상태(F-COMP-03). */
    @GetMapping("/setup")
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<SetupStatusResponse> setupStatus(@CurrentUser LoginUser user) {
        return ApiResponse.ok(companySetupService.status(user));
    }

    /** 초기 설정 완료. 이미 완료했어도 200 이다. */
    @PostMapping("/setup/complete")
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<SetupStatusResponse> completeSetup(@CurrentUser LoginUser user) {
        return ApiResponse.ok(companySetupService.complete(user));
    }

    @GetMapping("/change-history")
    @RequirePermission(PermissionCode.COMPANY_MANAGE)
    public ApiResponse<List<CompanyChangeHistoryItem>> changeHistory(@CurrentUser LoginUser user) {
        return ApiResponse.ok(companyService.changeHistory(user));
    }
}
