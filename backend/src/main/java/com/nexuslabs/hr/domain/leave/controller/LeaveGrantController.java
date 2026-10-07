package com.nexuslabs.hr.domain.leave.controller;

import com.nexuslabs.hr.domain.leave.dto.AnnualGrantRequest;
import com.nexuslabs.hr.domain.leave.dto.AnnualGrantResult;
import com.nexuslabs.hr.domain.leave.dto.GrantAdjustmentRequest;
import com.nexuslabs.hr.domain.leave.dto.LeaveGrantItem;
import com.nexuslabs.hr.domain.leave.service.LeaveGrantService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API 설계서 8장 — 휴가 부여(F-LEAVE-02). 입사 부여는 API 없이 직원 등록에서 LeaveGrantService.grantOnHire 를 부른다. */
@RestController
@RequestMapping("/api/leave-grants")
@RequirePermission(PermissionCode.LEAVE_MANAGE)
public class LeaveGrantController {

    private final LeaveGrantService leaveGrantService;

    public LeaveGrantController(LeaveGrantService leaveGrantService) {
        this.leaveGrantService = leaveGrantService;
    }

    @PostMapping("/annual")
    public ApiResponse<AnnualGrantResult> annual(@CurrentUser LoginUser user,
                                                 @Valid @RequestBody AnnualGrantRequest request) {
        return ApiResponse.ok(leaveGrantService.grantAnnual(user, request.leaveYear()));
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LeaveGrantItem> adjust(@CurrentUser LoginUser user,
                                              @Valid @RequestBody GrantAdjustmentRequest request) {
        return ApiResponse.ok(leaveGrantService.adjust(user, request));
    }

    @GetMapping
    public ApiResponse<List<LeaveGrantItem>> list(@CurrentUser LoginUser user, @RequestParam long employeeId,
                                                  @RequestParam(required = false) Integer leaveYear) {
        return ApiResponse.ok(leaveGrantService.list(user, employeeId, leaveYear));
    }
}
