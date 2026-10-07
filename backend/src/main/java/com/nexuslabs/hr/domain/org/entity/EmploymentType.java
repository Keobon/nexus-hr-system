package com.nexuslabs.hr.domain.org.entity;

import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 고용형태. */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmploymentType extends OrgSettingItem {

    public EmploymentType(String name, int sortOrder) {
        super(name, sortOrder);
    }
}
