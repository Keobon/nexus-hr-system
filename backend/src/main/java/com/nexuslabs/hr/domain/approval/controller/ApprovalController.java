package com.nexuslabs.hr.domain.approval.controller;

import com.nexuslabs.hr.domain.approval.dto.ApprovalDecisionRequest;
import com.nexuslabs.hr.domain.approval.dto.HistoryItem;
import com.nexuslabs.hr.domain.approval.dto.InboxItem;
import com.nexuslabs.hr.domain.approval.dto.ReassignItem;
import com.nexuslabs.hr.domain.approval.dto.ReassignRequest;
import com.nexuslabs.hr.domain.approval.service.ApprovalService;
import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API 설계서 9.2 — 승인함 · 승인 · 반려 · 재지정. 승인과 평가는 권한 코드가 아니라 "그 단계의 승인자"인지로 판단한다. */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/inbox")
    public ApiResponse<PageResponse<InboxItem>> inbox(@CurrentUser LoginUser user,
                                                      @RequestParam(required = false) ApprovalWorkType workType,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(approvalService.inbox(user, workType, pageable)));
    }

    @GetMapping("/history")
    public ApiResponse<PageResponse<HistoryItem>> history(@CurrentUser LoginUser user,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(approvalService.history(user, pageable)));
    }

    @PostMapping("/{stepId}/approve")
    public ApiResponse<ApprovalStepView> approve(@CurrentUser LoginUser user, @PathVariable long stepId,
                                                 @Valid @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ApiResponse.ok(approvalService.approve(user, stepId,
                request == null ? new ApprovalDecisionRequest(null, null) : request));
    }

    @PostMapping("/{stepId}/reject")
    public ApiResponse<ApprovalStepView> reject(@CurrentUser LoginUser user, @PathVariable long stepId,
                                                @Valid @RequestBody ApprovalDecisionRequest request) {
        return ApiResponse.ok(approvalService.reject(user, stepId, request));
    }

    @GetMapping("/reassign-needed")
    @RequirePermission(PermissionCode.APPROVAL_MANAGE)
    public ApiResponse<List<ReassignItem>> reassignNeeded(@CurrentUser LoginUser user) {
        return ApiResponse.ok(approvalService.reassignNeeded(user));
    }

    @PatchMapping("/{stepId}/approver")
    @RequirePermission(PermissionCode.APPROVAL_MANAGE)
    public ApiResponse<ApprovalStepView> reassign(@CurrentUser LoginUser user, @PathVariable long stepId,
                                                  @Valid @RequestBody ReassignRequest request) {
        return ApiResponse.ok(approvalService.reassign(user, stepId, request.approverId()));
    }
}
