package com.nexuslabs.hr.domain.notification.entity;

import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 알림(2026-11-16). 1차는 결재 알림(APPROVAL_REQUESTED)만 — 결재 단계가 승인 차례가 되면 승인자에게 한 건.
 * 저장은 결재 트랜잭션 안에서 NotificationService 가 JDBC 로 한다(회사는 이벤트의 companyId). 읽음 처리만 이 엔티티로.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends CreatedAtEntity {

    public static final String APPROVAL_REQUESTED = "APPROVAL_REQUESTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long recipientId;
    private String type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "approval_work_type")
    private ApprovalWorkType workType;

    private Long targetId;
    private Long approvalStepId;
    private String title;
    private String body;
    private String linkPath;
    private OffsetDateTime readAt;

    /** 읽음 — 이미 읽었으면 처음 읽은 시각을 그대로 둔다(멱등). */
    public void markRead(OffsetDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
