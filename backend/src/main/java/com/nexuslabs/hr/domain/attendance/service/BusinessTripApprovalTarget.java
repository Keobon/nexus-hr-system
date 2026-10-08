package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalTarget;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.TargetSummary;
import com.nexuslabs.hr.domain.attendance.entity.BusinessTrip;
import com.nexuslabs.hr.domain.attendance.repository.BusinessTripRepository;
import com.nexuslabs.hr.global.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 출장의 승인 확정 · 반려 · 승인함 요약(역할 분담 v2 2.1 ApprovalTarget). 승인 처리와 같은 트랜잭션에서 불린다.
 * 승인함 details 키는 API 설계서 9.2 표 — tripType · destination · purpose · startDate · endDate.
 */
@Component
public class BusinessTripApprovalTarget implements ApprovalTarget {

    private static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("M/d");

    private final BusinessTripRepository repository;
    private final AttendanceService attendanceService;

    public BusinessTripApprovalTarget(BusinessTripRepository repository, AttendanceService attendanceService) {
        this.repository = repository;
        this.attendanceService = attendanceService;
    }

    @Override
    public ApprovalWorkType type() {
        return ApprovalWorkType.BUSINESS_TRIP;
    }

    /** 출장 확정 + 기간의 근무일마다 출장 근태(F-ATT-07). 근태는 JDBC 라 출장 상태를 먼저 DB에 반영한다. */
    @Override
    public void onFinalApproved(long targetId, ApprovalStepView last) {
        get(targetId).approve();
        repository.flush();
        attendanceService.createTripDays(TenantContext.require(), targetId);
    }

    @Override
    public void onRejected(long targetId) {
        get(targetId).reject();
    }

    @Override
    public TargetSummary summary(long targetId) {
        BusinessTrip trip = get(targetId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tripType", trip.getTripType().name());
        details.put("destination", trip.getDestination());
        details.put("purpose", trip.getPurpose());
        details.put("startDate", trip.getStartDate().toString());
        details.put("endDate", trip.getEndDate().toString());
        String title = "출장 %s %s–%s".formatted(trip.getDestination(), trip.getStartDate().format(TITLE_DATE),
                trip.getEndDate().format(TITLE_DATE));
        return new TargetSummary(trip.getEmployee().getId(), title, details, null);
    }

    private BusinessTrip get(long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalStateException("출장이 없다: " + id));
    }
}
