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

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 휴가 신청의 승인 확정 · 반려 · 승인함 요약(역할 분담 v2 2.1 ApprovalTarget). 승인 처리와 같은 트랜잭션에서 불린다.
 * 승인함 details 키는 API 설계서 9.2 표 — leaveTypeName · startDate · endDate · days · reason · remaining.
 */
@Component
public class LeaveApprovalTarget implements ApprovalTarget {

    private static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("M/d");

    private final LeaveRequestRepository repository;
    private final AttendanceService attendanceService;
    private final LeaveCalculator calculator;

    public LeaveApprovalTarget(LeaveRequestRepository repository, AttendanceService attendanceService,
                               LeaveCalculator calculator) {
        this.repository = repository;
        this.attendanceService = attendanceService;
        this.calculator = calculator;
    }

    @Override
    public ApprovalWorkType type() {
        return ApprovalWorkType.LEAVE;
    }

    /** 휴가 확정 + 기간의 근무일마다 휴가 근태(F-ATT-05). 근태는 JDBC 라 휴가 상태를 먼저 DB에 반영한다. */
    @Override
    public void onFinalApproved(long targetId, ApprovalStepView last) {
        get(targetId).approve();
        repository.flush();
        attendanceService.createLeaveDays(TenantContext.require(), targetId);
    }

    @Override
    public void onRejected(long targetId) {
        get(targetId).reject();
    }

    @Override
    public TargetSummary summary(long targetId) {
        LeaveRequest leave = get(targetId);
        return new TargetSummary(leave.getEmployee().getId(), title("휴가", leave), details(leave), null);
    }

    /**
     * remaining = 그 종류·연도의 지금 잔여(승인대기인 이 건까지 뺀 값 = 승인하면 남는 일수). 차감 안 하는 종류는 null.
     * 취소 요청(LEAVE_CANCEL)도 같은 키를 쓴다.
     */
    Map<String, Object> details(LeaveRequest leave) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("leaveTypeName", leave.getLeaveType().getName());
        details.put("startDate", leave.getStartDate().toString());
        details.put("endDate", leave.getEndDate().toString());
        details.put("days", (int) leave.getDays());
        details.put("reason", leave.getReason());
        details.put("remaining", leave.getLeaveType().isDeductsBalance()
                ? calculator.balance(TenantContext.require(), leave.getEmployee().getId(), leave.getLeaveType().getId(),
                        leave.getLeaveYear()).remaining()
                : null);
        return details;
    }

    static String title(String prefix, LeaveRequest leave) {
        return "%s %s %s–%s".formatted(prefix, leave.getLeaveType().getName(), leave.getStartDate().format(TITLE_DATE),
                leave.getEndDate().format(TITLE_DATE));
    }

    LeaveRequest get(long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalStateException("휴가가 없다: " + id));
    }
}
