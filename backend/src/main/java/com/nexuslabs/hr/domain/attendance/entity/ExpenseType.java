package com.nexuslabs.hr.domain.attendance.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출장 경비 종류. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseType extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private boolean receiptRequired;
    private int sortOrder;

    @Column(name = "is_active")
    private boolean active = true;

    public ExpenseType(String name, boolean receiptRequired, int sortOrder) {
        this.name = name;
        this.receiptRequired = receiptRequired;
        this.sortOrder = sortOrder;
    }

    public void update(String name, boolean receiptRequired, int sortOrder, boolean active) {
        this.name = name;
        this.receiptRequired = receiptRequired;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    /** 청구에 쓰인 종류는 지우지 않고 선택지에서만 뺀다(F-ATT-09). */
    public void deactivate() {
        this.active = false;
    }
}
