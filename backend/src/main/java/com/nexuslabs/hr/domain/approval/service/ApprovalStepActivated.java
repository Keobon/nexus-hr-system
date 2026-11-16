package com.nexuslabs.hr.domain.approval.service;

/**
 * 결재 단계가 승인 차례(PENDING)가 됐다 — 결재 알림(B1)이 받는다. {@link ApprovalService}가 같은 트랜잭션에서 발행한다.
 * 발행 지점: 첫 단계 저장(신청) · 승인으로 다음 단계 진행 · 휴가 취소 결재 열기 · 대기 중 단계의 승인자 재지정.
 * 모든 단계가 생략돼 즉시 승인되거나, 승인자가 없거나 재지정이 필요한(퇴직 · 비활성) 단계는 발행하지 않는다.
 * <p>
 * 받는 쪽은 TenantContext 대신 companyId 를 쓴다(커밋 뒤 전송 스레드 등).
 */
public record ApprovalStepActivated(long companyId, long stepId, long approverEmployeeId, ApprovalWorkType workType,
                                    long targetId, long applicantEmployeeId) {
}
