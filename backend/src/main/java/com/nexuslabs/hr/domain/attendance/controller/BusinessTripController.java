package com.nexuslabs.hr.domain.attendance.controller;

import com.nexuslabs.hr.domain.attendance.dto.BusinessTripCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.BusinessTripReportRequest;
import com.nexuslabs.hr.domain.attendance.dto.BusinessTripResponse;
import com.nexuslabs.hr.domain.attendance.dto.BusinessTripRow;
import com.nexuslabs.hr.domain.attendance.entity.RequestStatus;
import com.nexuslabs.hr.domain.attendance.service.BusinessTripService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** API 설계서 7.3 — 출장(F-ATT-07). */
@RestController
public class BusinessTripController {

    private final BusinessTripService businessTripService;

    public BusinessTripController(BusinessTripService businessTripService) {
        this.businessTripService = businessTripService;
    }

    @PostMapping("/api/me/business-trips")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BusinessTripResponse> create(@CurrentUser LoginUser user,
                                                    @Valid @RequestBody BusinessTripCreateRequest request) {
        return ApiResponse.ok(businessTripService.create(user, request));
    }

    /** 내 출장 목록. 최근 시작일부터. */
    @GetMapping("/api/me/business-trips")
    public ApiResponse<PageResponse<BusinessTripRow>> mine(@CurrentUser LoginUser user,
                                                           @RequestParam(required = false) RequestStatus status,
                                                           @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(businessTripService.mine(user, status, pageable)));
    }

    /** 다른 직원의 출장 목록. 팀 범위면 내 팀 직원 것만. from · to 는 기간이 걸치는 출장, orgUnitId 는 하위 조직 포함. */
    @GetMapping("/api/business-trips")
    @RequirePermission(PermissionCode.ATTENDANCE_READ)
    public ApiResponse<PageResponse<BusinessTripRow>> list(@CurrentUser LoginUser user,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                           @RequestParam(required = false) Long orgUnitId,
                                                           @RequestParam(required = false) RequestStatus status,
                                                           @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(businessTripService.list(user, from, to, orgUnitId, status, pageable)));
    }

    /** 신청자 · 승인자 · ATTENDANCE_READ(범위 안). 권한은 서비스에서 확인한다. */
    @GetMapping("/api/business-trips/{id}")
    public ApiResponse<BusinessTripResponse> get(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(businessTripService.get(user, id));
    }

    @PostMapping("/api/me/business-trips/{id}/withdraw")
    public ApiResponse<BusinessTripResponse> withdraw(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(businessTripService.withdraw(user, id));
    }

    @PutMapping("/api/me/business-trips/{id}/report")
    public ApiResponse<BusinessTripResponse> report(@CurrentUser LoginUser user, @PathVariable long id,
                                                    @Valid @RequestBody BusinessTripReportRequest request) {
        return ApiResponse.ok(businessTripService.report(user, id, request.reportText()));
    }
}
