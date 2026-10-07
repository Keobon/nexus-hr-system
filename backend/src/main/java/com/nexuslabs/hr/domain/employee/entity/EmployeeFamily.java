package com.nexuslabs.hr.domain.employee.entity;

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

/** 가족 정보. 부양가족 수·자녀 수는 이 테이블에서 센다(BR-EMP-007). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeFamily extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "family_relation")
    private FamilyRelation relation;

    private LocalDate birthDate;

    @Column(name = "is_tax_dependent")
    private boolean taxDependent;

    @Column(name = "is_disabled")
    private boolean disabled;

    @Column(name = "is_cohabiting")
    private boolean cohabiting;

    public EmployeeFamily(Employee employee, String name, FamilyRelation relation, LocalDate birthDate,
                          boolean taxDependent, boolean disabled, boolean cohabiting) {
        this.employee = employee;
        this.name = name;
        this.relation = relation;
        this.birthDate = birthDate;
        this.taxDependent = taxDependent;
        this.disabled = disabled;
        this.cohabiting = cohabiting;
    }
}
