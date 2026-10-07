package com.nexuslabs.hr.domain.leave.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 휴가 종류. 근속 가산 4개 값은 모두 있거나 모두 없다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveType extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private short annualDays;
    private boolean deductsBalance;

    @Column(name = "is_paid")
    private boolean paid;

    private boolean prorateFirstYear;
    private Short seniorityStartYears;
    private Short seniorityIntervalYears;
    private Short seniorityAddDays;
    private Short seniorityMaxDays;
    private int sortOrder;

    @Column(name = "is_active")
    private boolean active = true;

    public LeaveType(String name, short annualDays, boolean deductsBalance, boolean paid, boolean prorateFirstYear,
                     Short seniorityStartYears, Short seniorityIntervalYears, Short seniorityAddDays,
                     Short seniorityMaxDays, int sortOrder) {
        this.name = name;
        this.annualDays = annualDays;
        this.deductsBalance = deductsBalance;
        this.paid = paid;
        this.prorateFirstYear = prorateFirstYear;
        this.seniorityStartYears = seniorityStartYears;
        this.seniorityIntervalYears = seniorityIntervalYears;
        this.seniorityAddDays = seniorityAddDays;
        this.seniorityMaxDays = seniorityMaxDays;
        this.sortOrder = sortOrder;
    }
}
