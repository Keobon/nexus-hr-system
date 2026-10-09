package com.nexuslabs.hr.domain.evaluation.controller;

import com.nexuslabs.hr.domain.evaluation.dto.EvalTemplateCopyRequest;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTemplateDetail;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTemplateItem;
import com.nexuslabs.hr.domain.evaluation.dto.EvalTemplateRequest;
import com.nexuslabs.hr.domain.evaluation.service.EvalTemplateService;
import com.nexuslabs.hr.global.auth.CurrentUser;
import com.nexuslabs.hr.global.auth.LoginUser;
import com.nexuslabs.hr.global.permission.PermissionCode;
import com.nexuslabs.hr.global.permission.RequirePermission;
import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.DeleteResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API 설계서 12.1 — 평가 템플릿(F-EVAL-02). 모두 EVAL_MANAGE. */
@RestController
@RequestMapping("/api/eval-templates")
@RequirePermission(PermissionCode.EVAL_MANAGE)
public class EvalTemplateController {

    private final EvalTemplateService templateService;

    public EvalTemplateController(EvalTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse<List<EvalTemplateItem>> list(@CurrentUser LoginUser user,
                                                    @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(templateService.list(user, activeOnly));
    }

    @GetMapping("/{id}")
    public ApiResponse<EvalTemplateDetail> detail(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(templateService.detail(user, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EvalTemplateDetail> create(@CurrentUser LoginUser user,
                                                  @Valid @RequestBody EvalTemplateRequest request) {
        return ApiResponse.ok(templateService.create(user, request));
    }

    /** 통째로 교체. 시작된 평가 기간에 쓰였으면 EVAL_TEMPLATE_IN_USE. */
    @PutMapping("/{id}")
    public ApiResponse<EvalTemplateDetail> update(@CurrentUser LoginUser user, @PathVariable long id,
                                                  @Valid @RequestBody EvalTemplateRequest request) {
        return ApiResponse.ok(templateService.update(user, id, request));
    }

    @PostMapping("/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EvalTemplateDetail> copy(@CurrentUser LoginUser user, @PathVariable long id,
                                                @Valid @RequestBody EvalTemplateCopyRequest request) {
        return ApiResponse.ok(templateService.copy(user, id, request.name()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResult> delete(@CurrentUser LoginUser user, @PathVariable long id) {
        return ApiResponse.ok(templateService.delete(user, id));
    }
}
