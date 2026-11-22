package com.nexuslabs.hr.domain.assistant.dto;

/**
 * 초안 미리보기의 승인선 한 단계 — 휴가 미리보기 steps 와 같은 모양(stepOrder · approverId · approverName · skipped · stepLabel).
 * 아직 신청 전이라 상태 대신 생략 여부만 있다.
 */
public record DraftApprovalStep(int stepOrder, Long approverId, String approverName, boolean skipped,
                                String stepLabel) {
}
