package com.nexuslabs.hr.domain.company.entity;

import com.nexuslabs.hr.global.audit.AuditAction;
import com.nexuslabs.hr.global.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/** 감사 로그(BR-AUDIT-001). append-only. 기록은 AuditLogger 가 한다. */
@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "audit_action")
    private AuditAction action;

    private String targetType;
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> afterValue;

    public AuditLog(Long actorId, AuditAction action, String targetType, Long targetId, Map<String, Object> beforeValue,
                    Map<String, Object> afterValue) {
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }
}
