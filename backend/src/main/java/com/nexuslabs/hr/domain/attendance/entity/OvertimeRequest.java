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
}
