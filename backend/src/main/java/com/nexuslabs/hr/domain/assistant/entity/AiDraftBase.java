package com.nexuslabs.hr.domain.assistant.entity;

import com.nexuslabs.hr.global.entity.BaseTimeEntity;
import com.nexuslabs.hr.global.error.BusinessException;
import com.nexuslabs.hr.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 연장근무 · 출장 AI 초안의 공통 부분(2026-11-19) — 직원 · 마지막 문장 · 상태 · 만료와 상태 바꾸기.
 * 규칙은 휴가 초안({@link AiLeaveDraft})과 같다: 만들기 · 고치기 · 확인 · 폐기는 DRAFT 일 때만, 만료면 AI_DRAFT_EXPIRED.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AiDraftBase extends BaseTimeEntity {

    private Long employeeId;
    private String userMessage;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "ai_draft_status")
    private AiDraftStatus status = AiDraftStatus.DRAFT;

    private OffsetDateTime expiresAt;

    protected AiDraftBase(long employeeId) {
        this.employeeId = employeeId;
    }

    public abstract Long getId();

    /** 값이 다 모여 신청할 수 있는지(되묻기가 끝났는지). */
    public abstract boolean complete();

    /** 새 문장 · 만료 시각을 남긴다(고칠 때마다 30분 뒤로). 값 채우기는 하위 클래스가. */
    protected void touch(String userMessage, OffsetDateTime expiresAt) {
        requireDraft();
        this.userMessage = userMessage;
        this.expiresAt = expiresAt;
    }

    public boolean expiredAt(OffsetDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public void expire() {
        requireDraft();
        this.status = AiDraftStatus.EXPIRED;
    }

    public void discard() {
        requireDraft();
        this.status = AiDraftStatus.DISCARDED;
    }

    /** 확인 — 하위 클래스가 만든 신청 ID 를 함께 남긴다. */
    protected void markConfirmed() {
        requireDraft();
        this.status = AiDraftStatus.CONFIRMED;
    }

    public void requireDraft() {
        if (status == AiDraftStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.AI_DRAFT_EXPIRED);
        }
        if (status != AiDraftStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "이미 처리된 초안입니다");
        }
    }
}
