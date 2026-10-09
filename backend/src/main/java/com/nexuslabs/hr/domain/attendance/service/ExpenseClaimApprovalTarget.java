package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalTarget;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.TargetSummary;
import com.nexuslabs.hr.domain.attendance.entity.BusinessTrip;
import com.nexuslabs.hr.domain.attendance.entity.ExpenseClaim;
import com.nexuslabs.hr.domain.attendance.entity.ExpenseClaimLine;
import com.nexuslabs.hr.domain.attendance.repository.ExpenseClaimRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 출장 경비 청구의 승인 확정 · 반려 · 승인함 요약(역할 분담 v2 2.1 ApprovalTarget). 승인 처리와 같은 트랜잭션에서 불린다.
 * 승인함 details 키는 API 설계서 9.2 표 — businessTripId · destination · startDate · endDate(출장 기간) · totalAmount · lineCount.
 */
@Component
public class ExpenseClaimApprovalTarget implements ApprovalTarget {

    private final ExpenseClaimRepository repository;

    public ExpenseClaimApprovalTarget(ExpenseClaimRepository repository) {
        this.repository = repository;
    }

    @Override
    public ApprovalWorkType type() {
        return ApprovalWorkType.TRIP_EXPENSE;
    }

    /** 승인 → 정산 대기(명세서 연결 전). 다음 급여 정산에 출장비 항목으로 한 번 들어간다(BR-PAY-012). */
    @Override
    public void onFinalApproved(long targetId, ApprovalStepView last) {
        get(targetId).approve();
    }

    @Override
    public void onRejected(long targetId) {
        get(targetId).reject();
    }

    @Override
    public TargetSummary summary(long targetId) {
        return toSummary(get(targetId));
    }

    /** 한 페이지의 신청을 쿼리 한 번으로 요약한다(역할 분담 2.1 "승인 업무 확정"). 없는 ID는 맵에서 빠진다. */
    @Override
    public Map<Long, TargetSummary> summaries(Collection<Long> targetIds) {
        Map<Long, TargetSummary> result = new LinkedHashMap<>();
        repository.findAllWithTripAndLines(targetIds).forEach(claim -> result.put(claim.getId(), toSummary(claim)));
        return result;
    }

    private static TargetSummary toSummary(ExpenseClaim claim) {
        BusinessTrip trip = claim.getBusinessTrip();
        long total = claim.getLines().stream().mapToLong(ExpenseClaimLine::getAmount).sum();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("businessTripId", trip.getId());
        details.put("destination", trip.getDestination());
        details.put("startDate", trip.getStartDate().toString());
        details.put("endDate", trip.getEndDate().toString());
        details.put("totalAmount", total);
        details.put("lineCount", claim.getLines().size());
        String title = "출장 경비 %s %,d원".formatted(trip.getDestination(), total);
        return new TargetSummary(claim.getEmployee().getId(), title, details, null);
    }

    private ExpenseClaim get(long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalStateException("경비 청구가 없다: " + id));
    }
}
