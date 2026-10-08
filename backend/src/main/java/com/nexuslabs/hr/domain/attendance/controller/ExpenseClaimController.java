package com.nexuslabs.hr.domain.attendance.controller;

import com.nexuslabs.hr.domain.attendance.dto.ExpenseClaimCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.ExpenseClaimResponse;
import com.nexuslabs.hr.domain.attendance.dto.ExpenseClaimRow;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.service.ExpenseClaimService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API 설계서 7.3 — 출장 경비 청구(F-ATT-08). */
@RestController
public class ExpenseClaimController {

    private final ExpenseClaimService expenseClaimService;

    public ExpenseClaimController(ExpenseClaimService expenseClaimService) {
        this.expenseClaimService = expenseClaimService;
    }

    @PostMapping("/api/me/expense-claims")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseClaimResponse> create(@CurrentUser LoginUser user,
                                                    @Valid @RequestBody ExpenseClaimCreateRequest request) {
        return ApiResponse.ok(expenseClaimService.create(user, request));
    }

    /** 내 청구 목록. 최근 청구부터. */
    @GetMapping("/api/me/expense-claims")
    public ApiResponse<PageResponse<ExpenseClaimRow>> mine(@CurrentUser LoginUser user,
                                                           @RequestParam(required = false) RequestStatus status,
                                                           @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(expenseClaimService.mine(user, status, pageable)));
    }

    /** 다른 직원의 청구 목록. PAYROLL_READ 면 전사, 아니면 ATTENDANCE_READ 범위. settled=false 면 정산 대기 목록. */
    @GetMapping("/api/expense-claims")
    @RequirePermission({PermissionCode.ATTENDANCE_READ, PermissionCode.PAYROLL_READ})
    public ApiResponse<PageResponse<ExpenseClaimRow>> list(@CurrentUser LoginUser user,
                                                           @RequestParam(required = false) RequestStatus status,
                                                           @RequestParam(required = false) Boolean settled,
                                                           @RequestParam(required = false) Long orgUnitId,
                                                           @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(expenseClaimService.list(user, status, settled, orgUnitId, pageable)));
    }

    /** 신청자 · 승인자 · ATTENDANCE_READ(범위 안) · PAYROLL_READ. 권한은 서비스에서 확인한다. */
    @GetMapping("/api/expense-claims/{id}")
    public ApiResponse<ExpenseClaimResponse> get(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(expenseClaimService.get(user, id));
    }

    @PostMapping("/api/me/expense-claims/{id}/withdraw")
    public ApiResponse<ExpenseClaimResponse> withdraw(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(expenseClaimService.withdraw(user, id));
    }
}
