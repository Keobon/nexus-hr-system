package com.nexuslabs.hr.domain.attendance.controller;

import com.nexuslabs.hr.domain.attendance.dto.OvertimeCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.OvertimeResponse;
import com.nexuslabs.hr.domain.attendance.dto.OvertimeRow;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.service.OvertimeService;
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

/** API 설계서 7.2 — 연장근무(F-ATT-06). */
@RestController
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    @PostMapping("/api/me/overtime-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OvertimeResponse> create(@CurrentUser LoginUser user,
                                                @Valid @RequestBody OvertimeCreateRequest request) {
        return ApiResponse.ok(overtimeService.create(user, request));
    }

    /** 내 신청 목록. month 를 비우면 전체, 최근 근무일부터. */
    @GetMapping("/api/me/overtime-requests")
    public ApiResponse<PageResponse<OvertimeRow>> mine(@CurrentUser LoginUser user,
                                                       @RequestParam(required = false) YearMonth month,
                                                       @RequestParam(required = false) RequestStatus status,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(overtimeService.mine(user, month, status, pageable)));
    }

    /** 다른 직원의 신청 목록. 팀 범위면 내 팀 직원 것만. orgUnitId 는 하위 조직 포함. */
    @GetMapping("/api/overtime-requests")
    @RequirePermission(PermissionCode.ATTENDANCE_READ)
    public ApiResponse<PageResponse<OvertimeRow>> list(@CurrentUser LoginUser user,
                                                       @RequestParam(required = false) YearMonth month,
                                                       @RequestParam(required = false) Long orgUnitId,
                                                       @RequestParam(required = false) RequestStatus status,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(overtimeService.list(user, month, orgUnitId, status, pageable)));
    }

    /** 신청자 · 승인자 · ATTENDANCE_READ(범위 안). 권한은 서비스에서 확인한다. */
    @GetMapping("/api/overtime-requests/{id}")
    public ApiResponse<OvertimeResponse> get(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(overtimeService.get(user, id));
    }

    @PostMapping("/api/me/overtime-requests/{id}/withdraw")
    public ApiResponse<OvertimeResponse> withdraw(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(overtimeService.withdraw(user, id));
    }
}
