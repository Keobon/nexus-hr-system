package com.nexuslabs.hr.domain.payroll.controller;

import com.nexuslabs.hr.domain.payroll.dto.PayItemRequest;
import com.nexuslabs.hr.domain.payroll.dto.PayItemResponse;
import com.nexuslabs.hr.domain.payroll.dto.PayVariableRequest;
import com.nexuslabs.hr.domain.payroll.dto.PayVariableView;
import com.nexuslabs.hr.domain.payroll.dto.TaxBrackets;
import com.nexuslabs.hr.domain.payroll.service.PayItemService;
import com.nexuslabs.hr.domain.payroll.service.PayVariableService;
import com.nexuslabs.hr.domain.payroll.service.TaxBracketService;
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

/** API 설계서 10.1 — 급여 항목 · 계산 변수 · 세율 구간(F-PAY-01·02). */
@RestController
public class PaySettingController {

    private final PayItemService payItemService;
    private final PayVariableService payVariableService;
    private final TaxBracketService taxBracketService;

    public PaySettingController(PayItemService payItemService, PayVariableService payVariableService,
                                TaxBracketService taxBracketService) {
        this.payItemService = payItemService;
        this.payVariableService = payVariableService;
        this.taxBracketService = taxBracketService;
    }

    /** 정렬 순서대로. 정산 화면의 선택지는 activeOnly=true. */
    @GetMapping("/api/pay-items")
    @RequirePermission({PermissionCode.PAYROLL_READ, PermissionCode.PAYROLL_MANAGE})
    public ApiResponse<List<PayItemResponse>> items(@CurrentUser LoginUser user,
                                                    @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(payItemService.list(user.companyId(), activeOnly));
    }

    @PostMapping("/api/pay-items")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<PayItemResponse> createItem(@CurrentUser LoginUser user,
                                                   @Valid @RequestBody PayItemRequest request) {
        return ApiResponse.ok(payItemService.create(user, request));
    }

    /** 보낸 필드만 바꾼다. */
    @PatchMapping("/api/pay-items/{id}")
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<PayItemResponse> updateItem(@CurrentUser LoginUser user, @PathVariable long id,
                                                   @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(payItemService.update(user, id, patch));
    }

    @DeleteMapping("/api/pay-items/{id}")
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<DeleteResult> deleteItem(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(payItemService.delete(user, id));
    }

    @GetMapping("/api/pay-variables")
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<List<PayVariableView>> variables(@CurrentUser LoginUser user) {
        return ApiResponse.ok(payVariableService.list(user.companyId()));
    }

    @PostMapping("/api/pay-variables")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<PayVariableView> createVariable(@CurrentUser LoginUser user,
                                                       @Valid @RequestBody PayVariableRequest request) {
        return ApiResponse.ok(payVariableService.create(user, request));
    }

    @GetMapping("/api/tax-brackets")
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<TaxBrackets> taxBrackets(@CurrentUser LoginUser user) {
        return ApiResponse.ok(taxBracketService.get(user.companyId()));
    }

    /** 구간 전체 교체. */
    @PutMapping("/api/tax-brackets")
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<TaxBrackets> replaceTaxBrackets(@CurrentUser LoginUser user,
                                                       @Valid @RequestBody TaxBrackets request) {
        return ApiResponse.ok(taxBracketService.replace(user, request));
    }
}
