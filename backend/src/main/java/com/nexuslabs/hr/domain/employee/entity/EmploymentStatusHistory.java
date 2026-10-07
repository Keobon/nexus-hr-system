package com.nexuslabs.hr.domain.employee.entity;

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

import java.time.LocalDate;

/** 재직상태 이력. append-only. 입사 때 ACTIVE 1행이 생긴다. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmploymentStatusHistory extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "emp_status")
    private EmpStatus status;

    private LocalDate effectiveDate;
    private String reason;
    private Long createdBy;

    public EmploymentStatusHistory(Employee employee, EmpStatus status, LocalDate effectiveDate, String reason,
                                   Long createdBy) {
        this.employee = employee;
        this.status = status;
        this.effectiveDate = effectiveDate;
        this.reason = reason;
        this.createdBy = createdBy;
    }
}
