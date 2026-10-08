package com.nexuslabs.hr.domain.attendance.controller;

import com.nexuslabs.hr.domain.attendance.dto.AttendanceCorrectionResponse;
import com.nexuslabs.hr.domain.attendance.dto.AttendanceCreateRequest;
import com.nexuslabs.hr.domain.attendance.dto.CorrectionItem;
import com.nexuslabs.hr.domain.attendance.service.AttendanceCorrectionService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API 설계서 7.1 — 근태 정정(F-ATT-04). 정정은 전사 범위 권한이다. */
@RestController
@RequestMapping("/api/attendances")
@RequirePermission(PermissionCode.ATTENDANCE_MANAGE)
public class AttendanceCorrectionController {

    private final AttendanceCorrectionService correctionService;

    public AttendanceCorrectionController(AttendanceCorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    /** 정정 대상 — 퇴근미기록과 휴가 · 출장 충돌. 건수는 /me 의 todos.attendanceCorrections 와 같다. */
    @GetMapping("/corrections")
    public ApiResponse<List<CorrectionItem>> corrections(@CurrentUser LoginUser user) {
        return ApiResponse.ok(correctionService.list(user));
    }

    /** 보낸 필드만 바꾼다. "안 보냄"과 "null(비움)"을 구별해야 해서 본문을 Map 으로 받는다. */
    @PatchMapping("/{id}")
    public ApiResponse<AttendanceCorrectionResponse> correct(@CurrentUser LoginUser user, @PathVariable long id,
                                                             @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(correctionService.correct(user, id, patch));
    }

    /** 기록이 없는 날에 근태를 만든다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttendanceCorrectionResponse> create(@CurrentUser LoginUser user,
                                                            @Valid @RequestBody AttendanceCreateRequest request) {
        return ApiResponse.ok(correctionService.create(user, request));
    }
}
