package com.nexuslabs.hr.domain.approval.service;

/**
 * 승인 대상 업무(휴가 · 휴가 취소 · 연장근무 · 출장 · 출장 경비)가 구현하는 경계 인터페이스(역할 분담 v2 2.1).
 * 업무 영역마다 @Component 로 하나씩 만든다 — ApprovalService 가 type() 으로 찾아 부른다.
 * 그래서 승인 영역은 휴가·출장 영역의 테이블을 직접 만지지 않는다.
 *
 * <p>모든 메서드는 승인 처리와 <b>같은 트랜잭션</b>에서 불린다(BR-APPR-005). 예외를 던지면 승인도 롤백된다.
 * 회사는 요청의 TenantContext 로 정해져 있다(JDBC로 직접 읽을 때는 company_id 조건을 넣는다).
 */
public interface ApprovalTarget {

    ApprovalWorkType type();

    /**
     * 마지막 단계가 승인됐을 때(또는 모든 단계가 생략돼 신청 즉시) 업무를 확정한다.
     * 예) 휴가 APPROVED + 근태 생성 / 연장근무 APPROVED + last.approvedMinutes() 확정.
     * last 는 즉시 승인이면 null.
     */
    void onFinalApproved(long targetId, ApprovalStepView last);

    /** 한 단계라도 반려되면 신청을 반려 상태로 바꾼다(휴가 취소 반려면 휴가를 다시 APPROVED). */
    void onRejected(long targetId);

    /** 승인함 · 재지정 화면에 보여 줄 요약. 신청자 ID는 재지정 검사(신청자 본인 불가)에도 쓴다. */
    TargetSummary summary(long targetId);
}
