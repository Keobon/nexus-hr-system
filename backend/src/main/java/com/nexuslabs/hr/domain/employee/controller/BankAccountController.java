package com.nexuslabs.hr.domain.employee.controller;

import com.nexuslabs.hr.domain.employee.dto.BankAccountRequest;
import com.nexuslabs.hr.domain.employee.dto.BankAccountView;
import com.nexuslabs.hr.domain.employee.service.BankAccountService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API 설계서 6장 — 급여 계좌(F-PAY-03). 기본은 뒤 4자리만, ?reveal=true 면 전체 번호. */
@RestController
public class BankAccountController {

    private final BankAccountService bankAccountService;

    public BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    /** reveal=true 는 감사 로그(VIEW). */
    @GetMapping("/api/employees/{id}/bank-account")
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<BankAccountView> of(@CurrentUser LoginUser user, @PathVariable long id,
                                           @RequestParam(defaultValue = "false") boolean reveal) {
        return ApiResponse.ok(bankAccountService.of(user, id, reveal));
    }

    @PutMapping("/api/employees/{id}/bank-account")
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<BankAccountView> update(@CurrentUser LoginUser user, @PathVariable long id,
                                               @Valid @RequestBody BankAccountRequest request) {
        return ApiResponse.ok(bankAccountService.update(user, id, request));
    }

    @GetMapping("/api/me/bank-account")
    public ApiResponse<BankAccountView> mine(@CurrentUser LoginUser user,
                                             @RequestParam(defaultValue = "false") boolean reveal) {
        return ApiResponse.ok(bankAccountService.mine(user, reveal));
    }

    @PutMapping("/api/me/bank-account")
    public ApiResponse<BankAccountView> updateMine(@CurrentUser LoginUser user,
                                                   @Valid @RequestBody BankAccountRequest request) {
        return ApiResponse.ok(bankAccountService.updateMine(user, request));
    }
}
