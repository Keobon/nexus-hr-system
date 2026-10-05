package com.nexuslabs.hr.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/** created_at·updated_at이 모두 있는 테이블의 부모. */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends CreatedAtEntity {

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
