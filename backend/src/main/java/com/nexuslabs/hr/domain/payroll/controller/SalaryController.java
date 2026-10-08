package com.nexuslabs.hr.domain.payroll.controller;

import com.nexuslabs.hr.domain.payroll.dto.EmployeePayItemRequest;
import com.nexuslabs.hr.domain.payroll.dto.EmployeeSalaries;
import com.nexuslabs.hr.domain.payroll.dto.PayItemAssigned;
import com.nexuslabs.hr.domain.payroll.dto.SalaryRegistered;
import com.nexuslabs.hr.domain.payroll.dto.SalaryRequest;
import com.nexuslabs.hr.domain.payroll.dto.SalaryRow;
import com.nexuslabs.hr.domain.payroll.service.SalaryService;
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

/** API 설계서 10.2 — 직원 급여(F-PAY-03·04). 수정 · 삭제 API 는 없다(append-only, BR-PAY-002). */
@RestController
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    /** 급여 대상인 재직 · 휴직 직원, 사원번호 순. keyword 는 이름 · 사원번호, orgUnitId 는 하위 조직 포함. */
    @GetMapping("/api/salaries")
    @RequirePermission(PermissionCode.PAYROLL_READ)
    public ApiResponse<PageResponse<SalaryRow>> list(@CurrentUser LoginUser user,
                                                     @RequestParam(required = false) Long orgUnitId,
                                                     @RequestParam(required = false) String keyword,
                                                     @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(salaryService.list(user.companyId(), orgUnitId, keyword, pageable)));
    }

    @GetMapping("/api/employees/{id}/salaries")
    @RequirePermission(PermissionCode.PAYROLL_READ)
    public ApiResponse<EmployeeSalaries> ofEmployee(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(salaryService.of(user.companyId(), id));
    }

    @GetMapping("/api/me/salaries")
    public ApiResponse<EmployeeSalaries> mine(@CurrentUser LoginUser user) {
        return ApiResponse.ok(salaryService.mine(user));
    }

    @PostMapping("/api/employees/{id}/salaries")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<SalaryRegistered> register(@CurrentUser LoginUser user, @PathVariable long id,
                                                  @Valid @RequestBody SalaryRequest request) {
        return ApiResponse.ok(salaryService.register(user, id, request));
    }

    @PostMapping("/api/employees/{id}/pay-items")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<PayItemAssigned> assignItem(@CurrentUser LoginUser user, @PathVariable long id,
                                                   @Valid @RequestBody EmployeePayItemRequest request) {
        return ApiResponse.ok(salaryService.assignItem(user, id, request));
    }
}
