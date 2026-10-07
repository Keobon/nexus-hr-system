package com.nexuslabs.hr.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/** created_at만 있는 테이블(append-only 이력 등)의 부모. */
@Getter
@MappedSuperclass
public abstract class CreatedAtEntity extends TenantEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
