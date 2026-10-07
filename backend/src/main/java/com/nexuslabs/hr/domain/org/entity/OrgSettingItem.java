package com.nexuslabs.hr.domain.org.entity;

import com.nexuslabs.hr.global.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 직급 · 직책 · 고용형태의 공통 구조(이름 · 정렬 순서 · 활성 여부). 세 테이블은 컬럼이 같다. */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class OrgSettingItem extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private int sortOrder;

    @Column(name = "is_active")
    private boolean active = true;

    protected OrgSettingItem(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public void update(String name, int sortOrder, boolean active) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    /** 쓰인 적 있는 항목은 지우지 않고 선택지에서만 뺀다(BR-ORG-001). */
    public void deactivate() {
        this.active = false;
    }
}
