package com.nexuslabs.hr.domain.payroll.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
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

/** 직원 기본 급여 이력(BR-PAY-002). append-only. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeSalary extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "salary_type")
    private SalaryType salaryType;

    private Long annualSalary;
    private long monthlyBase;
    private LocalDate effectiveFrom;
    private String reason;
    private long createdBy;

    public EmployeeSalary(Employee employee, SalaryType salaryType, Long annualSalary, long monthlyBase,
                          LocalDate effectiveFrom, String reason, long createdBy) {
        this.employee = employee;
        this.salaryType = salaryType;
        this.annualSalary = annualSalary;
        this.monthlyBase = monthlyBase;
        this.effectiveFrom = effectiveFrom;
        this.reason = reason;
        this.createdBy = createdBy;
    }
}
