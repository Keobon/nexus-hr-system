package com.nexuslabs.hr.domain.turnover.controller;

import com.nexuslabs.hr.domain.turnover.dto.TurnoverStats;
import com.nexuslabs.hr.domain.turnover.service.TurnoverStatsService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/**
 * 퇴사 통계(작업 지시서 2-4) — 경영진 · 인사 담당(DASHBOARD_COMPANY), 아니면 403. 기간은 from · to = YYYY-MM(양 끝 달 포함).
 * 데이터가 없는 기간은 404 가 아니라 0 · 빈 배열.
 */
@RestController
@RequestMapping("/api/stats/turnover")
@RequirePermission(PermissionCode.DASHBOARD_COMPANY)
public class TurnoverStatsController {

    private final TurnoverStatsService service;

    public TurnoverStatsController(TurnoverStatsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<TurnoverStats.Summary> summary(@CurrentUser LoginUser user, @RequestParam YearMonth month) {
        return ApiResponse.ok(service.summary(user, month));
    }

    /** unit = month | quarter | year. */
    @GetMapping("/trend")
    public ApiResponse<List<TurnoverStats.TrendPoint>> trend(@CurrentUser LoginUser user, @RequestParam String unit,
                                                             @RequestParam YearMonth from, @RequestParam YearMonth to) {
        return ApiResponse.ok(service.trend(user, unit, from, to));
    }

    @GetMapping("/by-department")
    public ApiResponse<List<TurnoverStats.ByDepartment>> byDepartment(@CurrentUser LoginUser user,
                                                                      @RequestParam YearMonth from,
                                                                      @RequestParam YearMonth to) {
        return ApiResponse.ok(service.byDepartment(user, from, to));
    }

    @GetMapping("/by-tenure")
    public ApiResponse<List<TurnoverStats.ByTenure>> byTenure(@CurrentUser LoginUser user, @RequestParam YearMonth from,
                                                              @RequestParam YearMonth to) {
        return ApiResponse.ok(service.byTenure(user, from, to));
    }

    @GetMapping("/by-reason")
    public ApiResponse<List<TurnoverStats.ByReason>> byReason(@CurrentUser LoginUser user, @RequestParam YearMonth from,
                                                              @RequestParam YearMonth to) {
        return ApiResponse.ok(service.byReason(user, from, to));
    }

    /** 퇴사일 최근 순 페이지(기본 20, 최대 100). */
    @GetMapping("/resignations")
    public ApiResponse<PageResponse<TurnoverStats.Resignation>> resignations(@CurrentUser LoginUser user,
                                                                            @RequestParam YearMonth from,
                                                                            @RequestParam YearMonth to,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.resignations(user, from, to, page, size));
    }
}
