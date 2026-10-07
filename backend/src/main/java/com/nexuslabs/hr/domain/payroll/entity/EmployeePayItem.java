package com.nexuslabs.hr.domain.payroll.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.global.entity.CreatedAtEntity;
import jakarta.persistence.Entity;
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

import java.time.LocalDate;

/** 직원별 항목 금액 이력. append-only. 적용을 끝낼 때는 금액 0 행을 쌓는다. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeePayItem extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pay_item_id")
    private PayItem payItem;

    private long amount;
    private LocalDate effectiveFrom;
    private String reason;
    private long createdBy;

    public EmployeePayItem(Employee employee, PayItem payItem, long amount, LocalDate effectiveFrom, String reason,
                           long createdBy) {
        this.employee = employee;
        this.payItem = payItem;
        this.amount = amount;
        this.effectiveFrom = effectiveFrom;
        this.reason = reason;
        this.createdBy = createdBy;
    }
}
