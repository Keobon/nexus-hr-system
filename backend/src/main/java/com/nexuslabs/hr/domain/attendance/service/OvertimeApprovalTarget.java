package com.nexuslabs.hr.domain.attendance.service;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepView;
import com.nexuslabs.hr.domain.approval.service.ApprovalTarget;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.domain.approval.service.TargetSummary;
import com.nexuslabs.hr.domain.attendance.entity.OvertimeRequest;
import com.nexuslabs.hr.domain.attendance.repository.OvertimeRequestRepository;
import com.nexuslabs.hr.global.config.ClockConfig;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 연장근무의 승인 확정 · 반려 · 승인함 요약(역할 분담 v2 2.1 ApprovalTarget). 승인 처리와 같은 트랜잭션에서 불린다.
 * 승인함 details 키는 API 설계서 9.2 표 — workDate · plannedStart · plannedEnd · reason.
 */
@Component
public class OvertimeApprovalTarget implements ApprovalTarget {

    private static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter TITLE_TIME = DateTimeFormatter.ofPattern("HH:mm");
    /** API 설계서 1.1 시각 형식(초 포함) — 응답 본문의 OffsetDateTime 과 같은 모양. */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final OvertimeRequestRepository repository;

    public OvertimeApprovalTarget(OvertimeRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    public ApprovalWorkType type() {
        return ApprovalWorkType.OVERTIME;
    }

    /**
     * 승인된 연장 시간 = 마지막 단계가 인정한 시간(F-ATT-06). 승인 엔진은 비워 보낸 값을 앞 단계 값으로 채워 저장한다.
     * 모든 단계가 생략된 즉시 승인이면(last == null) 신청 시간 그대로(2.3 28번).
     */
    @Override
    public void onFinalApproved(long targetId, ApprovalStepView last) {
        OvertimeRequest overtime = get(targetId);
        Integer minutes = last == null ? null : last.approvedMinutes();
        overtime.approve(minutes != null ? minutes : overtime.getRequestedMinutes());
    }

    @Override
    public void onRejected(long targetId) {
        get(targetId).reject();
    }

    @Override
    public TargetSummary summary(long targetId) {
        OvertimeRequest overtime = get(targetId);
        OffsetDateTime start = seoul(overtime.getPlannedStart());
        OffsetDateTime end = seoul(overtime.getPlannedEnd());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("workDate", overtime.getWorkDate().toString());
        details.put("plannedStart", start.format(ISO));
        details.put("plannedEnd", end.format(ISO));
        details.put("reason", overtime.getReason());
        String title = "연장근무 %s %s–%s".formatted(overtime.getWorkDate().format(TITLE_DATE),
                start.format(TITLE_TIME), end.format(TITLE_TIME));
        return new TargetSummary(overtime.getEmployee().getId(), title, details, overtime.getRequestedMinutes());
    }

    private OvertimeRequest get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("연장근무 신청이 없다: " + id));
    }

    private static OffsetDateTime seoul(OffsetDateTime t) {
        return t.atZoneSameInstant(ClockConfig.ZONE).toOffsetDateTime();
    }
}
