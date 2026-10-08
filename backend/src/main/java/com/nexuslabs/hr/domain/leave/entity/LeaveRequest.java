package com.nexuslabs.hr.domain.leave.entity;

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

/** 휴가 신청. days 는 신청 시점 값을 저장한다(BR-LEAVE-008). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id")
    private LeaveType leaveType;

    private short leaveYear;
    private LocalDate startDate;
    private LocalDate endDate;
    private short days;
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "leave_status")
    private LeaveStatus status = LeaveStatus.PENDING;

    private String cancelReason;

    public LeaveRequest(Employee employee, LeaveType leaveType, short leaveYear, LocalDate startDate, LocalDate endDate,
                        short days, String reason) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.leaveYear = leaveYear;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
        this.reason = reason;
    }

    /** 최종 승인. 근태(ON_VACATION)는 승인 대상이 같은 트랜잭션에서 만든다(F-ATT-05). */
    public void approve() {
        require(LeaveStatus.PENDING);
        this.status = LeaveStatus.APPROVED;
    }

    public void reject() {
        require(LeaveStatus.PENDING);
        this.status = LeaveStatus.REJECTED;
    }

    /** 승인대기 철회 · 퇴직 자동 취소(F-LEAVE-06). */
    public void withdraw(String cancelReason) {
        require(LeaveStatus.PENDING);
        this.status = LeaveStatus.CANCELLED;
        this.cancelReason = cancelReason;
    }

    /** 승인된 휴가의 취소 요청 — 취소 승인 전까지는 사용으로 친다(BR-LEAVE-004). */
    public void requestCancel(String cancelReason) {
        require(LeaveStatus.APPROVED);
        this.status = LeaveStatus.CANCEL_REQUESTED;
        this.cancelReason = cancelReason;
    }

    public void confirmCancel() {
        require(LeaveStatus.CANCEL_REQUESTED);
        this.status = LeaveStatus.CANCELLED;
    }

    /** 취소 요청 반려 → 다시 승인완료. 취소 사유는 이력으로 남겨 둔다. */
    public void rejectCancel() {
        require(LeaveStatus.CANCEL_REQUESTED);
        this.status = LeaveStatus.APPROVED;
    }

    private void require(LeaveStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("휴가 상태가 " + expected + " 가 아니다: " + id + " " + status);
        }
    }
}
