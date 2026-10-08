package com.nexuslabs.hr.domain.attendance.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 연장근무 신청(BR-ATT-004). 승인 단계에서 시간을 줄일 수 있다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OvertimeRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private LocalDate workDate;
    private OffsetDateTime plannedStart;
    private OffsetDateTime plannedEnd;
    private int requestedMinutes;
    private Integer approvedMinutes;
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "request_status")
    private RequestStatus status = RequestStatus.PENDING;

    private String cancelReason;

    public OvertimeRequest(Employee employee, LocalDate workDate, OffsetDateTime plannedStart,
                           OffsetDateTime plannedEnd, int requestedMinutes, String reason) {
        this.employee = employee;
        this.workDate = workDate;
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.requestedMinutes = requestedMinutes;
        this.reason = reason;
    }

    /** 최종 승인 — 마지막 단계가 인정한 시간(즉시 승인이면 신청 시간)으로 확정한다. */
    public void approve(int approvedMinutes) {
        requirePending();
        this.status = RequestStatus.APPROVED;
        this.approvedMinutes = approvedMinutes;
    }

    public void reject() {
        requirePending();
        this.status = RequestStatus.REJECTED;
    }

    /** 신청자 철회 · 퇴직 자동 취소. 퇴직이면 사유를 남긴다. */
    public void cancel(String cancelReason) {
        requirePending();
        this.status = RequestStatus.CANCELLED;
        this.cancelReason = cancelReason;
    }

    private void requirePending() {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("승인대기가 아닌 연장근무 신청: " + id);
        }
    }
}
