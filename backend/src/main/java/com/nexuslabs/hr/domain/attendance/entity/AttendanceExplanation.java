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

/**
 * 근태 소명(F-ATT-10). 직원이 사유와 실제 시각(선택)을 내고 ATTENDANCE_MANAGE 가 승인하면 그 값으로 근태를 정정한다.
 * 근태 행과는 직원 + 날짜로만 잇는다 — 결근인 날은 근태 행이 없다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceExplanation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private LocalDate workDate;
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "work_type")
    private WorkType reqWorkType;

    private OffsetDateTime reqCheckInAt;
    private OffsetDateTime reqCheckOutAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "request_status")
    private RequestStatus status = RequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Employee reviewedBy;

    private OffsetDateTime reviewedAt;
    private String rejectReason;

    public AttendanceExplanation(Employee employee, LocalDate workDate, String reason, WorkType reqWorkType,
                                 OffsetDateTime reqCheckInAt, OffsetDateTime reqCheckOutAt) {
        this.employee = employee;
        this.workDate = workDate;
        this.reason = reason;
        this.reqWorkType = reqWorkType;
        this.reqCheckInAt = reqCheckInAt;
        this.reqCheckOutAt = reqCheckOutAt;
    }

    /** 요청 값이 하나라도 있으면 승인할 때 정정한다. 없으면 사유만 낸 소명이다. */
    public boolean hasRequestedValues() {
        return reqWorkType != null || reqCheckInAt != null || reqCheckOutAt != null;
    }

    public void approve(Employee reviewer, OffsetDateTime at) {
        requirePending();
        this.status = RequestStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = at;
    }

    public void reject(Employee reviewer, OffsetDateTime at, String rejectReason) {
        requirePending();
        this.status = RequestStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = at;
        this.rejectReason = rejectReason;
    }

    public void cancel() {
        requirePending();
        this.status = RequestStatus.CANCELLED;
    }

    private void requirePending() {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("대기 중이 아닌 근태 소명: " + id);
        }
    }
}
