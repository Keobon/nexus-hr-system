package com.nexuslabs.hr.domain.org.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 직급. sortOrder 가 서열이다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobGrade extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private int sortOrder;

    @Column(name = "is_active")
    private boolean active = true;

    public JobGrade(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }
}
