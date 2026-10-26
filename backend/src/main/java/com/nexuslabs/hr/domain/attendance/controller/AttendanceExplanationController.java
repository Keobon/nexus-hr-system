package com.nexuslabs.hr.domain.attendance.controller;

import com.nexuslabs.hr.domain.attendance.dto.AttendanceExplanationRejectRequest;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceExplanationRequest;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceExplanationResponse;
import com.nexuslabs.hr.domain.attendance.service.AttendanceExplanationService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
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

/** API 설계서 7.1 — 근태 소명(F-ATT-10). 대기 목록은 정정 대상(GET /api/attendances/corrections)에 같이 나온다. */
@RestController
public class AttendanceExplanationController {

    private final AttendanceExplanationService explanationService;

    public AttendanceExplanationController(AttendanceExplanationService explanationService) {
        this.explanationService = explanationService;
    }

    @PostMapping("/api/me/attendance-explanations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttendanceExplanationResponse> create(@CurrentUser LoginUser user,
                                                             @Valid @RequestBody AttendanceExplanationRequest request) {
        return ApiResponse.ok(explanationService.create(user, request));
    }

    /** 내 소명. month 를 비우면 이번 달, 최근 날짜부터. */
    @GetMapping("/api/me/attendance-explanations")
    public ApiResponse<List<AttendanceExplanationResponse>> mine(@CurrentUser LoginUser user,
                                                                 @RequestParam(required = false) YearMonth month) {
        return ApiResponse.ok(explanationService.mine(user, month));
    }

    @PostMapping("/api/me/attendance-explanations/{id}/withdraw")
    public ApiResponse<AttendanceExplanationResponse> withdraw(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(explanationService.withdraw(user, id));
    }

    @PostMapping("/api/attendance-explanations/{id}/approve")
    @RequirePermission(PermissionCode.ATTENDANCE_MANAGE)
    public ApiResponse<AttendanceExplanationResponse> approve(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(explanationService.approve(user, id));
    }

    @PostMapping("/api/attendance-explanations/{id}/reject")
    @RequirePermission(PermissionCode.ATTENDANCE_MANAGE)
    public ApiResponse<AttendanceExplanationResponse> reject(@CurrentUser LoginUser user, @PathVariable long id,
                                                             @Valid @RequestBody AttendanceExplanationRejectRequest request) {
        return ApiResponse.ok(explanationService.reject(user, id, request.reason()));
    }
}
