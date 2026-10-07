package com.nexuslabs.hr.domain.approval.entity;

import com.nexuslabs.hr.domain.approval.service.ApprovalStepStatus;
import com.nexuslabs.hr.domain.approval.service.ApprovalWorkType;
import com.nexuslabs.hr.global.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/** 신청 건별 승인 단계. 신청할 때 승인자를 계산해 저장한다(BR-APPR-006). targetId 는 다형 참조라 FK 가 없다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalStep extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "approval_work_type")
    private ApprovalWorkType workType;

    private long targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_line_id")
    private ApprovalLine approvalLine;

    private short round;
    private short stepOrder;
    private Long approverId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "approval_step_status")
    private ApprovalStepStatus status;

    private Integer approvedMinutes;
    private String comment;
    private OffsetDateTime actedAt;
    private boolean needsReassign = false;

    public ApprovalStep(ApprovalWorkType workType, long targetId, ApprovalLine approvalLine, short round,
                        short stepOrder, Long approverId, ApprovalStepStatus status) {
        this.workType = workType;
        this.targetId = targetId;
        this.approvalLine = approvalLine;
        this.round = round;
        this.stepOrder = stepOrder;
        this.approverId = approverId;
        this.status = status;
    }
}
