package com.nexuslabs.hr.domain.attendance.controller;

import com.nexuslabs.hr.domain.attendance.dto.EmployeeMonthlySummary;
import com.nexuslabs.hr.domain.attendance.dto.MonthlyAttendance;
import com.nexuslabs.hr.domain.attendance.service.AttendanceQueryService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/** API 설계서 7.1 — 근태 조회(F-ATT-03). month 는 "2026-10" 형식이고 빼면 이번 달이다. */
@RestController
public class AttendanceController {

    private final AttendanceQueryService queryService;

    public AttendanceController(AttendanceQueryService queryService) {
        this.queryService = queryService;
    }

    /** 내 근태 — 그 달의 모든 날짜 행과 월 합계. */
    @GetMapping("/api/me/attendances")
    public ApiResponse<MonthlyAttendance> mine(@CurrentUser LoginUser user,
                                               @RequestParam(required = false) YearMonth month) {
        return ApiResponse.ok(queryService.mine(user, month));
    }

    /** 직원별 월 합계. 팀 범위면 내가 조직장인 조직과 하위 조직의 직원만 나온다. sort: employeeNo(기본) · name */
    @GetMapping("/api/attendances")
    @RequirePermission(PermissionCode.ATTENDANCE_READ)
    public ApiResponse<PageResponse<EmployeeMonthlySummary>> list(@CurrentUser LoginUser user,
                                                                  @RequestParam(required = false) YearMonth month,
                                                                  @RequestParam(required = false) Long orgUnitId,
                                                                  @RequestParam(required = false) Long employeeId,
                                                                  @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(queryService.list(user, month, orgUnitId, employeeId, pageable)));
    }

    /** 한 직원의 근태 — 내 근태와 같은 형식. */
    @GetMapping("/api/attendances/employees/{id}")
    @RequirePermission(PermissionCode.ATTENDANCE_READ)
    public ApiResponse<MonthlyAttendance> ofEmployee(@CurrentUser LoginUser user, @PathVariable long id,
                                                     @RequestParam(required = false) YearMonth month) {
        return ApiResponse.ok(queryService.ofEmployee(user, id, month));
    }
}
