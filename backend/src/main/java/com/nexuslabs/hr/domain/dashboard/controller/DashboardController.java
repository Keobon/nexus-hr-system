package com.nexuslabs.hr.domain.dashboard.controller;

import com.nexuslabs.hr.domain.dashboard.dto.CompanyLaborCost;
import com.nexuslabs.hr.domain.dashboard.dto.CompanyPerformance;
import com.nexuslabs.hr.domain.dashboard.dto.DashboardHome;
import com.nexuslabs.hr.domain.dashboard.service.CompanyInsightService;
import com.nexuslabs.hr.domain.dashboard.service.DashboardService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * API 설계서 13장 — 홈 화면 한 번 호출(F-DASH-01–07). 로그인한 누구나, 섹션은 권한에 따라 붙거나 빠진다.
 * 성과 현황 · 직원별 인건비는 다른 기간 · 정산 월을 고를 때 따로 부른다(DASHBOARD_COMPANY + 전사 EVAL_READ / PAYROLL_READ).
 */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;
    private final CompanyInsightService companyInsightService;

    public DashboardController(DashboardService dashboardService, CompanyInsightService companyInsightService) {
        this.dashboardService = dashboardService;
        this.companyInsightService = companyInsightService;
    }

    @GetMapping("/api/dashboard/home")
    public ApiResponse<DashboardHome> home(@CurrentUser LoginUser user) {
        return ApiResponse.ok(dashboardService.home(user));
    }

    /** F-DASH-06 — evalCycleId 가 없으면 기본 기간. 권한이 모자라면 403, 없는 기간이면 404. */
    @GetMapping("/api/dashboard/company/performance")
    @RequirePermission(PermissionCode.DASHBOARD_COMPANY)
    public ApiResponse<CompanyPerformance> performance(@CurrentUser LoginUser user,
                                                       @RequestParam(required = false) Long evalCycleId) {
        return ApiResponse.ok(companyInsightService.performance(user, evalCycleId));
    }

    /** F-DASH-07 — payMonth 가 없으면 가장 최근 정산 월. 직원은 인건비 많은 순 페이지(기본 20, 최대 100). */
    @GetMapping("/api/dashboard/company/labor-cost")
    @RequirePermission(PermissionCode.DASHBOARD_COMPANY)
    public ApiResponse<CompanyLaborCost> laborCost(@CurrentUser LoginUser user,
                                                   @RequestParam(required = false) YearMonth payMonth,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(companyInsightService.laborCost(user, payMonth, page, size));
    }
}
