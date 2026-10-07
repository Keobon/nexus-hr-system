package com.nexuslabs.hr.domain.leave.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.domain.leave.service.LeaveGrantType;
import com.nexuslabs.hr.global.entity.CreatedAtEntity;
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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 휴가 부여 내역(BR-LEAVE-006·007). append-only. 자동 부여는 createdBy 가 없다. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveGrant extends CreatedAtEntity {

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "leave_grant_type")
    private LeaveGrantType grantType;

    private short days;
    private String reason;
    private Long createdBy;

    public LeaveGrant(Employee employee, LeaveType leaveType, short leaveYear, LeaveGrantType grantType, short days,
                      String reason, Long createdBy) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.leaveYear = leaveYear;
        this.grantType = grantType;
        this.days = days;
        this.reason = reason;
        this.createdBy = createdBy;
    }
}
