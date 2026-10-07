package com.nexuslabs.hr.domain.org.entity;

import com.nexuslabs.hr.domain.employee.entity.Employee;
import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
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

/** 조직. 깊이 제한 없는 트리이고 최상위만 parent 가 없다(BR-ORG-002). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrgUnit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrgUnit parent;

    private String name;
    private String levelName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_employee_id")
    private Employee leadEmployee;

    private Long monthlyBudget;
    private int sortOrder;

    @Column(name = "is_active")
    private boolean active = true;

    public OrgUnit(OrgUnit parent, String name, String levelName, int sortOrder) {
        this.parent = parent;
        this.name = name;
        this.levelName = levelName;
        this.sortOrder = sortOrder;
    }
}
