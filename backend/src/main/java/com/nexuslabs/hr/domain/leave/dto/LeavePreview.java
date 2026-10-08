package com.nexuslabs.hr.domain.leave.dto;

import java.util.List;

/**
 * GET /api/me/leave-requests/preview 응답(API 설계서 8장). 저장하지 않는다.
 * balance 는 차감 종류만(차감 안 하는 종류는 null), approvalLine 은 승인자를 못 찾으면 null.
 * errors 는 실제 신청하면 걸릴 오류 코드 — 하나라도 있으면 화면이 신청 버튼을 막는다.
 */
public record LeavePreview(int days, int leaveYear, Balance balance, ApprovalLine approvalLine, List<Step> steps,
                           List<String> errors) {

    public record Balance(String leaveTypeName, int granted, int used, int pending, int remaining, int remainingAfter) {
    }

    public record ApprovalLine(long id, String name) {
    }

    public record Step(int stepOrder, Long approverId, String approverName, boolean skipped) {
    }
}
