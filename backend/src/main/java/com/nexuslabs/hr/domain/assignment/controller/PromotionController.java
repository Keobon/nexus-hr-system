package com.nexuslabs.hr.domain.assignment.controller;

import com.nexuslabs.hr.domain.assignment.dto.PromotionCandidate;
import com.nexuslabs.hr.domain.assignment.service.PromotionCandidateService;
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

/** 승진 심사(10/15 시연 피드백) — 대상자 조회만. 확정은 승진 발령(POST /api/assignments)으로. */
@RestController
public class PromotionController {

    private final PromotionCandidateService candidateService;

    public PromotionController(PromotionCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    /** 지금 직급에 minYears 년(기본 3) 이상 있은 재직 직원 + 최근 3년 확정 평가 평균. 전사 발령 조회 권한만. */
    @GetMapping("/api/promotions/candidates")
    @RequirePermission(PermissionCode.ASSIGNMENT_READ)
    public ApiResponse<PageResponse<PromotionCandidate>> candidates(@CurrentUser LoginUser user,
                                                                    @RequestParam(defaultValue = "3") int minYears,
                                                                    @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(candidateService.candidates(user, minYears, pageable)));
    }
}
