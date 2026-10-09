package com.nexuslabs.hr.domain.evaluation.controller;

import com.nexuslabs.hr.domain.evaluation.dto.EvalCycleItem;
import com.nexuslabs.hr.domain.evaluation.dto.EvalCycleRequest;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTargetPreview;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTargetRules;
import com.nexuslabs.hr.domain.evaluation.service.EvalCycleService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API 설계서 12.1 — 평가 기간 · 대상 규칙 · 시작 · 마감(F-EVAL-01 · 03). 모두 EVAL_MANAGE. */
@RestController
@RequestMapping("/api/eval-cycles")
@RequirePermission(PermissionCode.EVAL_MANAGE)
public class EvalCycleController {

    private final EvalCycleService cycleService;

    public EvalCycleController(EvalCycleService cycleService) {
        this.cycleService = cycleService;
    }

    @GetMapping
    public ApiResponse<List<EvalCycleItem>> list(@CurrentUser LoginUser user) {
        return ApiResponse.ok(cycleService.list(user));
    }

    /** 다른 기간과 겹치면 만들고 warning = PERIOD_OVERLAP. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EvalCycleItem> create(@CurrentUser LoginUser user, @Valid @RequestBody EvalCycleRequest request) {
        return ApiResponse.ok(cycleService.create(user, request));
    }

    /** 보낸 필드만. 예정이면 전부, 진행 중이면 종료일 연장만. */
    @PatchMapping("/{id}")
    public ApiResponse<EvalCycleItem> update(@CurrentUser LoginUser user, @PathVariable long id,
                                             @RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(cycleService.update(user, id, patch));
    }

    @GetMapping("/{id}/targets")
    public ApiResponse<EvalTargetRules> rules(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(cycleService.rules(user, id));
    }

    /** 통째로 교체(예정 상태만). */
    @PutMapping("/{id}/targets")
    public ApiResponse<EvalTargetRules> replaceRules(@CurrentUser LoginUser user, @PathVariable long id,
                                                     @Valid @RequestBody EvalTargetRules request) {
        return ApiResponse.ok(cycleService.replaceRules(user, id, request));
    }

    @GetMapping("/{id}/targets/preview")
    public ApiResponse<EvalTargetPreview> preview(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(cycleService.preview(user, id));
    }

    /** 실행 POST — 200(API 1.4). */
    @PostMapping("/{id}/start")
    public ApiResponse<EvalCycleItem> start(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(cycleService.start(user, id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<EvalCycleItem> close(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(cycleService.close(user, id));
    }
}
