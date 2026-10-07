package com.nexuslabs.hr.domain.attendance.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
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

import java.time.LocalDate;

/** 출장 경비 줄. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseClaimLine extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_claim_id")
    private ExpenseClaim expenseClaim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_type_id")
    private ExpenseType expenseType;

    private LocalDate usedDate;
    private long amount;
    private String description;
    private Long receiptFileId;

    public ExpenseClaimLine(ExpenseClaim expenseClaim, ExpenseType expenseType, LocalDate usedDate, long amount,
                            String description, Long receiptFileId) {
        this.expenseClaim = expenseClaim;
        this.expenseType = expenseType;
        this.usedDate = usedDate;
        this.amount = amount;
        this.description = description;
        this.receiptFileId = receiptFileId;
    }
}
