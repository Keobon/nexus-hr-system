package com.nexuslabs.hr.domain.leave.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalTarget;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.TargetSummary;
import com.nexuslabs.hr.domain.attendance.service.AttendanceService;
import com.nexuslabs.hr.domain.leave.entity.LeaveRequest;
import com.nexuslabs.hr.domain.leave.repository.LeaveRequestRepository;
import com.nexuslabs.hr.global.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 휴가 취소 요청의 승인 · 반려(F-LEAVE-06, BR-LEAVE-004). target_id 는 원래 휴가 신청 id 다.
 * 승인 → 취소완료 + 휴가 근태 삭제(F-ATT-05), 반려 → 다시 승인완료. 승인함 details 는 휴가와 같은 키 + cancelReason.
 */
@Component
public class LeaveCancelApprovalTarget implements ApprovalTarget {

    private final LeaveApprovalTarget leaveTarget;
    private final LeaveRequestRepository repository;
    private final AttendanceService attendanceService;

    public LeaveCancelApprovalTarget(LeaveApprovalTarget leaveTarget, LeaveRequestRepository repository,
                                     AttendanceService attendanceService) {
        this.leaveTarget = leaveTarget;
        this.repository = repository;
        this.attendanceService = attendanceService;
    }

    @Override
    public ApprovalWorkType type() {
        return ApprovalWorkType.LEAVE_CANCEL;
    }

    @Override
    public void onFinalApproved(long targetId, ApprovalStepView last) {
        leaveTarget.get(targetId).confirmCancel();
        repository.flush();
        attendanceService.deleteLeaveDays(TenantContext.require(), targetId);
    }

    @Override
    public void onRejected(long targetId) {
        leaveTarget.get(targetId).rejectCancel();
    }

    @Override
    public TargetSummary summary(long targetId) {
        LeaveRequest leave = leaveTarget.get(targetId);
        Map<String, Object> details = leaveTarget.details(leave);
        details.put("cancelReason", leave.getCancelReason());
        return new TargetSummary(leave.getEmployee().getId(), LeaveApprovalTarget.title("휴가 취소", leave), details,
                null);
    }
}
