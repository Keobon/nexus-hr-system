package com.nexuslabs.hr.domain.assistant.entity;

import com.nexuslabs.hr.domain.leave.entity.HalfDay;
import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * AI 휴가 결재 초안(2026-11-16). AI 가 직원 문장에서 뽑은 값을 담아 두기만 한다 — 실제 휴가 신청(leave_request)은
 * 직원이 확인했을 때 기존 휴가 신청 로직이 만든다. 종류 · 날짜가 비어 있으면 아직 되묻는 중이다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiLeaveDraft extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long employeeId;
    private String userMessage;
    private Long leaveTypeId;
    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "half_day")
    private HalfDay halfDay;

    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "ai_draft_status")
    private AiDraftStatus status = AiDraftStatus.DRAFT;

    private Long leaveRequestId;
    private OffsetDateTime expiresAt;

    public AiLeaveDraft(long employeeId) {
        this.employeeId = employeeId;
    }

    /** 새로 뽑은 값으로 채운다. 고칠 때마다 만료 시각을 다시 30분 뒤로 민다. */
    public void fill(String userMessage, Long leaveTypeId, LocalDate startDate, LocalDate endDate, HalfDay halfDay,
                     String reason, OffsetDateTime expiresAt) {
        requireDraft();
        this.userMessage = userMessage;
        this.leaveTypeId = leaveTypeId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.halfDay = halfDay;
        this.reason = reason;
        this.expiresAt = expiresAt;
    }

    public boolean expiredAt(OffsetDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public boolean complete() {
        return leaveTypeId != null && startDate != null && endDate != null;
    }

    public void expire() {
        requireDraft();
        this.status = AiDraftStatus.EXPIRED;
    }

    public void confirm(long leaveRequestId) {
        requireDraft();
        this.status = AiDraftStatus.CONFIRMED;
        this.leaveRequestId = leaveRequestId;
    }

    public void discard() {
        requireDraft();
        this.status = AiDraftStatus.DISCARDED;
    }

    /** 만들기 · 고치기 · 확인 · 폐기는 DRAFT 일 때만. 만료된 초안은 AI_DRAFT_EXPIRED. */
    public void requireDraft() {
        if (status == AiDraftStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.AI_DRAFT_EXPIRED);
        }
        if (status != AiDraftStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "이미 처리된 초안입니다");
        }
    }
}
