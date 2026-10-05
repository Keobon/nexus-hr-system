package com.nexuslabs.hr.global.support;

import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** @TenantId 자동 필터 확인용(테스트 전용). 실제 OrgUnit 엔티티는 B-02에서 만든다. */
@Getter
@Entity
@Table(name = "org_unit")
public class TestOrgUnit extends BaseTimeEntity {

    @Id
    private Long id;

    private String name;
}
