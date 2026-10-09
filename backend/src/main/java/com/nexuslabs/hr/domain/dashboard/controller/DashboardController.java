package com.nexuslabs.hr.domain.dashboard.controller;

import com.nexuslabs.hr.domain.dashboard.dto.DashboardHome;
import com.nexuslabs.hr.domain.dashboard.service.DashboardService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** API 설계서 13장 — 홈 화면 한 번 호출(F-DASH-01–05). 로그인한 누구나, 섹션은 권한에 따라 붙거나 빠진다. */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard/home")
    public ApiResponse<DashboardHome> home(@CurrentUser LoginUser user) {
        return ApiResponse.ok(dashboardService.home(user));
    }
}
