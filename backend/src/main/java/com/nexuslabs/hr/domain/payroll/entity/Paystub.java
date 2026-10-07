package com.nexuslabs.hr.domain.payroll.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.global.entity.CreatedAtEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.util.ArrayList;
import java.util.List;

/** 급여명세서(BR-PAY-008). 정산 당시 값을 그대로 저장하는 스냅샷이다. append-only. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Paystub extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private String employeeNoSnap;
    private String employeeNameSnap;
    private long orgUnitIdSnap;
    private String orgNameSnap;
    private String bankAccountMaskedSnap;
    private long basePay;
    private long ordinaryHourlyWage;
    private short dependentsCount;
    private short childrenCount;
    private short workedDays;
    private short monthDays;
    private long grossPay;
    private long taxablePay;
    private long incomeTax;
    private long localIncomeTax;
    private long totalDeduction;
    private long netPay;
    private long companyBurdenTotal;

    @OneToMany(mappedBy = "paystub", cascade = CascadeType.PERSIST)
    @OrderBy("sortOrder")
    private List<PaystubLine> lines = new ArrayList<>();

    public Paystub(PayrollRun payrollRun, Employee employee, String employeeNoSnap, String employeeNameSnap,
                   long orgUnitIdSnap, String orgNameSnap, String bankAccountMaskedSnap, long basePay,
                   long ordinaryHourlyWage, short dependentsCount, short childrenCount, short workedDays,
                   short monthDays, long grossPay, long taxablePay, long incomeTax, long localIncomeTax,
                   long totalDeduction, long netPay, long companyBurdenTotal) {
        this.payrollRun = payrollRun;
        this.employee = employee;
        this.employeeNoSnap = employeeNoSnap;
        this.employeeNameSnap = employeeNameSnap;
        this.orgUnitIdSnap = orgUnitIdSnap;
        this.orgNameSnap = orgNameSnap;
        this.bankAccountMaskedSnap = bankAccountMaskedSnap;
        this.basePay = basePay;
        this.ordinaryHourlyWage = ordinaryHourlyWage;
        this.dependentsCount = dependentsCount;
        this.childrenCount = childrenCount;
        this.workedDays = workedDays;
        this.monthDays = monthDays;
        this.grossPay = grossPay;
        this.taxablePay = taxablePay;
        this.incomeTax = incomeTax;
        this.localIncomeTax = localIncomeTax;
        this.totalDeduction = totalDeduction;
        this.netPay = netPay;
        this.companyBurdenTotal = companyBurdenTotal;
    }

    public void addLine(PaystubLine line) {
        lines.add(line);
    }
}
