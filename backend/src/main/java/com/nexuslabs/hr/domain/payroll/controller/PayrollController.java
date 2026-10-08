package com.nexuslabs.hr.domain.payroll.controller;

import com.nexuslabs.hr.domain.payroll.dto.LaborCost;
import com.nexuslabs.hr.domain.payroll.dto.MyPayslipRow;
import com.nexuslabs.hr.domain.payroll.dto.PayrollPreview;
import com.nexuslabs.hr.domain.payroll.dto.PayrollRunRequest;
import com.nexuslabs.hr.domain.payroll.dto.PayrollRunRow;
import com.nexuslabs.hr.domain.payroll.dto.PayrollRunView;
import com.nexuslabs.hr.domain.payroll.dto.PayslipRow;
import com.nexuslabs.hr.domain.payroll.dto.PayslipView;
import com.nexuslabs.hr.domain.payroll.service.PayrollService;
import com.nexuslabs.hr.domain.payroll.service.PayslipService;
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

import java.time.YearMonth;
import java.util.List;

/** API 설계서 10.3 — 월 급여 정산 · 급여명세서 · 조직별 인건비(F-PAY-05·06·07). */
@RestController
public class PayrollController {

    private final PayrollService payrollService;
    private final PayslipService payslipService;

    public PayrollController(PayrollService payrollService, PayslipService payslipService) {
        this.payrollService = payrollService;
        this.payslipService = payslipService;
    }

    /** 저장하지 않는 계산. 월이 끝나기 전이면 warning = PAY_MONTH_NOT_ENDED. */
    @PostMapping("/api/payroll-runs/preview")
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<PayrollPreview> preview(@CurrentUser LoginUser user,
                                               @Valid @RequestBody PayrollRunRequest request) {
        return ApiResponse.ok(payrollService.preview(user, request));
    }

    /** 확정 — 미리보기와 같은 입력. 귀속 월이 끝난 뒤에만. */
    @PostMapping("/api/payroll-runs")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<PayrollRunView> confirm(@CurrentUser LoginUser user,
                                               @Valid @RequestBody PayrollRunRequest request) {
        return ApiResponse.ok(payrollService.confirm(user, request));
    }

    @GetMapping("/api/payroll-runs")
    @RequirePermission(PermissionCode.PAYROLL_READ)
    public ApiResponse<List<PayrollRunRow>> runs(@CurrentUser LoginUser user) {
        return ApiResponse.ok(payrollService.list(user.companyId()));
    }

    @GetMapping("/api/payroll-runs/{id}")
    @RequirePermission(PermissionCode.PAYROLL_READ)
    public ApiResponse<PayrollRunView> run(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(payrollService.view(user.companyId(), id));
    }

    @PostMapping("/api/payroll-runs/{id}/paid")
    @RequirePermission(PermissionCode.PAYROLL_MANAGE)
    public ApiResponse<PayrollRunView> markPaid(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(payrollService.markPaid(user, id));
    }

    @GetMapping("/api/me/payslips")
    public ApiResponse<List<MyPayslipRow>> myPayslips(@CurrentUser LoginUser user) {
        return ApiResponse.ok(payslipService.mine(user));
    }

    /** 본인 명세서 — 회사부담 필드가 없다. */
    @GetMapping("/api/me/payslips/{id}")
    public ApiResponse<PayslipView> myPayslip(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(payslipService.myPayslip(user, id));
    }

    /** orgUnitId 는 정산 당시 소속 기준, 하위 조직 포함. */
    @GetMapping("/api/payslips")
    @RequirePermission(PermissionCode.PAYROLL_READ)
    public ApiResponse<PageResponse<PayslipRow>> payslips(@CurrentUser LoginUser user,
                                                          @RequestParam(required = false) YearMonth payMonth,
                                                          @RequestParam(required = false) Long orgUnitId,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(payslipService.list(user.companyId(), payMonth, orgUnitId, pageable)));
    }

    @GetMapping("/api/payslips/{id}")
    @RequirePermission(PermissionCode.PAYROLL_READ)
    public ApiResponse<PayslipView> payslip(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(payslipService.payslip(user.companyId(), id));
    }

    @GetMapping("/api/statistics/labor-cost")
    @RequirePermission(PermissionCode.PAYROLL_READ)
    public ApiResponse<LaborCost> laborCost(@CurrentUser LoginUser user, @RequestParam YearMonth payMonth) {
        return ApiResponse.ok(payslipService.laborCost(user.companyId(), payMonth));
    }
}
