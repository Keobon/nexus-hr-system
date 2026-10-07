package com.nexuslabs.hr.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

/** COMPANY를 뺀 모든 엔티티의 부모. company_id는 Hibernate가 조회 조건·저장 값에 자동으로 넣는다. */
@Getter
@MappedSuperclass
public abstract class TenantEntity {

    @TenantId
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;
}
