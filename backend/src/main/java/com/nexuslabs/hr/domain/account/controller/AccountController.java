package com.nexuslabs.hr.domain.account.controller;

import com.nexuslabs.hr.domain.account.dto.AccountRow;
import com.nexuslabs.hr.domain.account.dto.AccountUpdateRequest;
import com.nexuslabs.hr.domain.account.dto.TemporaryPasswordResponse;
import com.nexuslabs.hr.domain.account.service.AccountService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API 설계서 4장 — 계정 관리. 경로의 ID는 직원 ID다. */
@RestController
@RequestMapping("/api/accounts")
@RequirePermission(PermissionCode.ROLE_MANAGE)
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** sort: employeeNo(기본) · name · lastLoginAt */
    @GetMapping
    public ApiResponse<PageResponse<AccountRow>> list(@CurrentUser LoginUser user,
                                                      @RequestParam(required = false) Long roleId,
                                                      @RequestParam(required = false) Boolean active,
                                                      @RequestParam(required = false) Boolean locked,
                                                      @RequestParam(required = false) String keyword,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(accountService.list(user, roleId, active, locked, keyword, pageable)));
    }

    @PatchMapping("/{employeeId}")
    public ApiResponse<AccountRow> update(@CurrentUser LoginUser user, @PathVariable long employeeId,
                                          @RequestBody AccountUpdateRequest request) {
        return ApiResponse.ok(accountService.update(user, employeeId, request));
    }

    @PostMapping("/{employeeId}/unlock")
    public ApiResponse<AccountRow> unlock(@CurrentUser LoginUser user, @PathVariable long employeeId) {
        return ApiResponse.ok(accountService.unlock(user, employeeId));
    }

    @PostMapping("/{employeeId}/reset-password")
    public ApiResponse<TemporaryPasswordResponse> resetPassword(@CurrentUser LoginUser user,
                                                                @PathVariable long employeeId) {
        return ApiResponse.ok(accountService.resetPassword(user, employeeId));
    }
}
