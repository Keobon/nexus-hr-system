package com.nexuslabs.hr.domain.employee.entity;

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

/** 직원 추가 항목 값. 여러 건 항목은 seq 로 구분한다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeFieldValue extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_def_id")
    private EmployeeFieldDef fieldDef;

    private short seq;
    private String value;

    public EmployeeFieldValue(Employee employee, EmployeeFieldDef fieldDef, short seq, String value) {
        this.employee = employee;
        this.fieldDef = fieldDef;
        this.seq = seq;
        this.value = value;
    }
}
