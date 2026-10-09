package com.nexuslabs.hr.domain.evaluation.controller;

import com.nexuslabs.hr.domain.evaluation.dto.EvalAnswersRequest;
import com.nexuslabs.hr.domain.evaluation.dto.EvalProgress;
import com.nexuslabs.hr.domain.evaluation.dto.EvalReopenRequest;
import com.nexuslabs.hr.domain.evaluation.dto.EvalResultItem;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTodoItem;
import com.nexuslabs.hr.domain.evaluation.dto.EvaluationDetail;
import com.nexuslabs.hr.domain.evaluation.dto.MyEvaluation;
import com.nexuslabs.hr.domain.evaluation.service.EvalResultService;
import com.nexuslabs.hr.domain.evaluation.service.EvaluationService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API 설계서 12.2 — 평가 입력 · 제출(F-EVAL-04), 확정 · 재오픈(F-EVAL-06), 결과 · 진행 현황(F-EVAL-05).
 * 입력은 그 평가의 평가자만(서비스가 데이터로 판단), 확정 · 재오픈 · 진행 현황은 EVAL_MANAGE.
 */
@RestController
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final EvalResultService resultService;

    public EvaluationController(EvaluationService evaluationService, EvalResultService resultService) {
        this.evaluationService = evaluationService;
        this.resultService = resultService;
    }

    @GetMapping("/api/me/evaluations/todo")
    public ApiResponse<List<EvalTodoItem>> todo(@CurrentUser LoginUser user) {
        return ApiResponse.ok(evaluationService.todo(user));
    }

    /** 평가자 · EVAL_MANAGE. */
    @GetMapping("/api/evaluations/{id}")
    public ApiResponse<EvaluationDetail> detail(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(evaluationService.detail(user, id));
    }

    @PutMapping("/api/evaluations/{id}/answers")
    public ApiResponse<EvaluationDetail> saveAnswers(@CurrentUser LoginUser user, @PathVariable long id,
                                                     @Valid @RequestBody EvalAnswersRequest request) {
        return ApiResponse.ok(evaluationService.saveAnswers(user, id, request));
    }

    @PostMapping("/api/evaluations/{id}/submit")
    public ApiResponse<EvaluationDetail> submit(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(evaluationService.submit(user, id));
    }

    @PostMapping("/api/evaluations/{id}/confirm")
    @RequirePermission(PermissionCode.EVAL_MANAGE)
    public ApiResponse<EvaluationDetail> confirm(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(evaluationService.confirm(user, id));
    }

    @PostMapping("/api/evaluations/{id}/reopen")
    @RequirePermission(PermissionCode.EVAL_MANAGE)
    public ApiResponse<EvaluationDetail> reopen(@CurrentUser LoginUser user, @PathVariable long id,
                                                @Valid @RequestBody EvalReopenRequest request) {
        return ApiResponse.ok(evaluationService.reopen(user, id, request.reason()));
    }

    /** 확정된 것만. */
    @GetMapping("/api/me/evaluations")
    public ApiResponse<List<MyEvaluation>> mine(@CurrentUser LoginUser user) {
        return ApiResponse.ok(resultService.mine(user));
    }

    @GetMapping("/api/evaluations/results")
    @RequirePermission(PermissionCode.EVAL_READ)
    public ApiResponse<PageResponse<EvalResultItem>> results(@CurrentUser LoginUser user,
                                                             @RequestParam(required = false) Long cycleId,
                                                             @RequestParam(required = false) Long orgUnitId,
                                                             @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(resultService.results(user, cycleId, orgUnitId, pageable)));
    }

    @GetMapping("/api/eval-cycles/{id}/progress")
    @RequirePermission(PermissionCode.EVAL_MANAGE)
    public ApiResponse<EvalProgress> progress(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(resultService.progress(user, id));
    }
}
