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
}
