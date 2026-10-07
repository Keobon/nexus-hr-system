package com.nexuslabs.hr.domain.leave.controller;

import com.nexuslabs.hr.domain.leave.dto.EmployeeLeaveBalances;
import com.nexuslabs.hr.domain.leave.dto.MyLeaveBalances;
import com.nexuslabs.hr.domain.leave.service.LeaveBalanceService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API 설계서 8장 — 잔여 조회(F-LEAVE-05). leaveYear 를 빼면 오늘이 속한 휴가 연도. */
@RestController
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    public LeaveBalanceController(LeaveBalanceService leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @GetMapping("/api/me/leave-balances")
    public ApiResponse<MyLeaveBalances> mine(@CurrentUser LoginUser user,
                                             @RequestParam(required = false) Integer leaveYear) {
        return ApiResponse.ok(leaveBalanceService.mine(user, leaveYear));
    }

    @GetMapping("/api/leave-balances")
    @RequirePermission(PermissionCode.LEAVE_READ)
    public ApiResponse<PageResponse<EmployeeLeaveBalances>> list(@CurrentUser LoginUser user,
                                                                 @RequestParam(required = false) Integer leaveYear,
                                                                 @RequestParam(required = false) Long orgUnitId,
                                                                 @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(leaveBalanceService.list(user, leaveYear, orgUnitId, pageable)));
    }
}
