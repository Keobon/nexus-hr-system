package com.nexuslabs.hr.domain.attendance.controller;

import com.nexuslabs.hr.domain.attendance.dto.CheckInRequest;
import com.nexuslabs.hr.domain.attendance.dto.CheckOutRequest;
import com.nexuslabs.hr.domain.attendance.dto.TodayAttendance;
import com.nexuslabs.hr.domain.attendance.service.AttendanceService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API 설계서 7.1 — 본인 출퇴근(F-ATT-01·02). 경로에 직원 ID 가 없고 토큰의 직원으로 고정한다.
 * 네 API 모두 오늘 근태(TodayAttendance)를 돌려준다.
 */
@RestController
@RequestMapping("/api/me/attendance")
public class MyAttendanceController {

    private final AttendanceService attendanceService;

    public MyAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /** 본문을 비우면 사내 근무로 출근한다. 기록 방식(웹 · 모바일)은 User-Agent 로 판단한다. */
    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TodayAttendance> checkIn(@CurrentUser LoginUser user,
                                                @Valid @RequestBody(required = false) CheckInRequest request,
                                                @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                                                String userAgent) {
        return ApiResponse.ok(attendanceService.checkIn(user,
                request == null ? new CheckInRequest(null, null, null, null) : request, userAgent));
    }

    @PostMapping("/check-out")
    public ApiResponse<TodayAttendance> checkOut(@CurrentUser LoginUser user,
                                                 @Valid @RequestBody(required = false) CheckOutRequest request,
                                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                                                 String userAgent) {
        return ApiResponse.ok(attendanceService.checkOut(user,
                request == null ? new CheckOutRequest(null, null) : request, userAgent));
    }

    /** 보낸 필드만 바꾼다. "안 보냄"과 "null(비움)"을 구별해야 해서 본문을 Map 으로 받는다. */
    @PatchMapping("/today")
    public ApiResponse<TodayAttendance> changeWorkType(@CurrentUser LoginUser user,
                                                       @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(attendanceService.changeWorkType(user, patch));
    }

    @GetMapping("/today")
    public ApiResponse<TodayAttendance> today(@CurrentUser LoginUser user) {
        return ApiResponse.ok(attendanceService.today(user));
    }
}
