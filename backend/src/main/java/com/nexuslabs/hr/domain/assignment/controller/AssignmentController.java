package com.nexuslabs.hr.domain.assignment.controller;

import com.nexuslabs.hr.domain.assignment.dto.AssignmentCorrectionRequest;
import com.nexuslabs.hr.domain.assignment.dto.AssignmentItem;
import com.nexuslabs.hr.domain.assignment.dto.AssignmentRequest;
import com.nexuslabs.hr.domain.assignment.dto.CurrentAssignment;
import com.nexuslabs.hr.domain.assignment.dto.MyAssignments;
import com.nexuslabs.hr.domain.assignment.entity.AssignmentType;
import com.nexuslabs.hr.domain.assignment.service.AssignmentQueryService;
import com.nexuslabs.hr.domain.assignment.service.AssignmentService;
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

import java.time.LocalDate;

/** API 설계서 11장 — 인사발령 등록 · 정정(F-ASSIGN-01 · 04)과 조회(F-ASSIGN-02 · 03). */
@RestController
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final AssignmentQueryService queryService;

    public AssignmentController(AssignmentService assignmentService, AssignmentQueryService queryService) {
        this.assignmentService = assignmentService;
        this.queryService = queryService;
    }

    /** 발효일은 오늘. 응답은 방금 추가된 이력 한 줄. */
    @PostMapping("/api/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<AssignmentItem> register(@CurrentUser LoginUser user,
                                                @Valid @RequestBody AssignmentRequest request) {
        return ApiResponse.ok(assignmentService.register(user, request));
    }

    @PostMapping("/api/assignments/{id}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCode.ASSIGNMENT_MANAGE)
    public ApiResponse<AssignmentItem> correct(@CurrentUser LoginUser user, @PathVariable long id,
                                               @Valid @RequestBody AssignmentCorrectionRequest request) {
        return ApiResponse.ok(assignmentService.correct(user, id, request));
    }

    /** 새 발령부터. 팀 범위면 내 팀 직원만. */
    @GetMapping("/api/assignments")
    @RequirePermission(PermissionCode.ASSIGNMENT_READ)
    public ApiResponse<PageResponse<AssignmentItem>> list(@CurrentUser LoginUser user,
                                                          @RequestParam(required = false) Long employeeId,
                                                          @RequestParam(required = false) LocalDate from,
                                                          @RequestParam(required = false) LocalDate to,
                                                          @RequestParam(required = false) AssignmentType type,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(queryService.list(user, employeeId, from, to, type, pageable)));
    }

    /** 직원별 현재 조직 · 직급 · 직책 · 최근 발령일. orgUnitId 는 하위 조직 포함. */
    @GetMapping("/api/assignments/current")
    @RequirePermission(PermissionCode.ASSIGNMENT_READ)
    public ApiResponse<PageResponse<CurrentAssignment>> current(@CurrentUser LoginUser user,
                                                                @RequestParam(required = false) Long orgUnitId,
                                                                @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(queryService.current(user, orgUnitId, pageable)));
    }

    @GetMapping("/api/me/assignments")
    public ApiResponse<MyAssignments> mine(@CurrentUser LoginUser user) {
        return ApiResponse.ok(queryService.mine(user));
    }
}
