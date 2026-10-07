package com.nexuslabs.hr.domain.org.entity;

import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 직급. sortOrder 가 서열이다. */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobGrade extends OrgSettingItem {

    public JobGrade(String name, int sortOrder) {
        super(name, sortOrder);
    }
}
