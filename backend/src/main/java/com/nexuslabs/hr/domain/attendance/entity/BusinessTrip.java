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

/** 출장(BR-ATT-005). 다녀온 뒤 결과 보고를 남긴다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessTrip extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "trip_type")
    private TripType tripType;

    private String destination;
    private String purpose;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long estimatedCost;
    private String reportText;
    private OffsetDateTime reportedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "request_status")
    private RequestStatus status = RequestStatus.PENDING;

    private String cancelReason;

    public BusinessTrip(Employee employee, TripType tripType, String destination, String purpose, LocalDate startDate,
                        LocalDate endDate, Long estimatedCost) {
        this.employee = employee;
        this.tripType = tripType;
        this.destination = destination;
        this.purpose = purpose;
        this.startDate = startDate;
        this.endDate = endDate;
        this.estimatedCost = estimatedCost;
    }

    /** 최종 승인. 근태(ON_BUSINESS_TRIP)는 승인 대상이 같은 트랜잭션에서 만든다. */
    public void approve() {
        requirePending();
        this.status = RequestStatus.APPROVED;
    }

    public void reject() {
        requirePending();
        this.status = RequestStatus.REJECTED;
    }

    /** 신청자 철회 · 퇴직 자동 취소. 승인된 출장의 취소는 없다(F-ATT-07). */
    public void cancel(String cancelReason) {
        requirePending();
        this.status = RequestStatus.CANCELLED;
        this.cancelReason = cancelReason;
    }

    /** 결과 보고 작성·수정 — 승인은 받지 않는다. */
    public void writeReport(String reportText, OffsetDateTime at) {
        this.reportText = reportText;
        this.reportedAt = at;
    }

    private void requirePending() {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("승인대기가 아닌 출장: " + id);
        }
    }
}
