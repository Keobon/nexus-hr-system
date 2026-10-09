package com.nexuslabs.hr.domain.leave.controller;

import com.nexuslabs.hr.domain.leave.dto.LeaveCancelRequest;
import com.nexuslabs.hr.domain.leave.dto.LeavePreview;
import com.nexuslabs.hr.domain.leave.dto.LeaveRequestCreate;
import com.nexuslabs.hr.domain.leave.dto.LeaveRequestResponse;
import com.nexuslabs.hr.domain.leave.dto.LeaveRequestRow;
import com.nexuslabs.hr.domain.leave.entity.LeaveStatus;
import com.nexuslabs.hr.domain.leave.service.LeaveRequestService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** API 설계서 8장 — 휴가 신청 · 조회 · 철회 · 취소 요청(F-LEAVE-03·04·06). */
@RestController
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    /** 신청 전 미리보기 — 저장하지 않는다. errors 에 코드가 있으면 신청 버튼을 막는다. */
    @GetMapping("/api/me/leave-requests/preview")
    public ApiResponse<LeavePreview> preview(@CurrentUser LoginUser user, @RequestParam long leaveTypeId,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.ok(leaveRequestService.preview(user, leaveTypeId, startDate, endDate));
    }

    @PostMapping("/api/me/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LeaveRequestResponse> create(@CurrentUser LoginUser user,
                                                    @Valid @RequestBody LeaveRequestCreate request) {
        return ApiResponse.ok(leaveRequestService.create(user, request));
    }

    /** 내 휴가 목록. 최근 시작일부터. */
    @GetMapping("/api/me/leave-requests")
    public ApiResponse<PageResponse<LeaveRequestRow>> mine(@CurrentUser LoginUser user,
                                                           @RequestParam(required = false) Integer leaveYear,
                                                           @RequestParam(required = false) LeaveStatus status,
                                                           @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(leaveRequestService.mine(user, leaveYear, status, pageable)));
    }

    /** 다른 직원의 휴가 목록. 팀 범위면 내 팀 직원 것만. from · to 는 기간이 걸치는 휴가, orgUnitId 는 하위 조직 포함. */
    @GetMapping("/api/leave-requests")
    @RequirePermission(PermissionCode.LEAVE_READ)
    public ApiResponse<PageResponse<LeaveRequestRow>> list(@CurrentUser LoginUser user,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                           @RequestParam(required = false) Long orgUnitId,
                                                           @RequestParam(required = false) LeaveStatus status,
                                                           @RequestParam(required = false) Long leaveTypeId,
                                                           @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(
                leaveRequestService.list(user, from, to, orgUnitId, status, leaveTypeId, pageable)));
    }

    /** 신청자 · 승인자 · LEAVE_READ(범위 안). 권한은 서비스에서 확인한다. */
    @GetMapping("/api/leave-requests/{id}")
    public ApiResponse<LeaveRequestResponse> get(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(leaveRequestService.get(user, id));
    }

    @PostMapping("/api/me/leave-requests/{id}/withdraw")
    public ApiResponse<LeaveRequestResponse> withdraw(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(leaveRequestService.withdraw(user, id));
    }

    @PostMapping("/api/me/leave-requests/{id}/cancel-request")
    public ApiResponse<LeaveRequestResponse> cancelRequest(@CurrentUser LoginUser user, @PathVariable long id,
                                                           @Valid @RequestBody(required = false) LeaveCancelRequest request) {
        // 사유는 선택 — 본문 없이 보내도 된다
        return ApiResponse.ok(leaveRequestService.requestCancel(user, id, request == null ? null : request.reason()));
    }
}
