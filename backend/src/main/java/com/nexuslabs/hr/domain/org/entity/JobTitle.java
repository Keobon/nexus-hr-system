package com.nexuslabs.hr.domain.org.entity;

import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 직책. */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobTitle extends OrgSettingItem {

    public JobTitle(String name, int sortOrder) {
        super(name, sortOrder);
    }
}
