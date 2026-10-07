package com.nexuslabs.hr.domain.approval.service;

import java.util.List;

/**
 * 승인자 계산 결과 — 저장하기 전 단계 목록. 신청 미리보기(휴가 신청 화면)와 실제 신청이 같은 계산을 쓴다.
 * approvalLineId 는 휴가 취소처럼 승인선이 없으면 null.
 */
public record ApprovalPlan(Long approvalLineId, String approvalLineName, List<PlannedStep> steps) {

    /** 생략되지 않은 단계가 하나도 없으면 신청 즉시 승인된다(BR-APPR-004). */
    public boolean immediatelyApproved() {
        return steps.stream().allMatch(PlannedStep::skipped);
    }

    /**
     * @param approverId 계산된 승인자. 생략된 단계도 계산된 사람을 남긴다(최상위라 못 찾은 경우만 null)
     * @param skipReason SELF(신청자 본인) · SAME_AS_PREVIOUS(앞 단계와 같은 사람) · TOP_OF_ORG(최상위 조직장이라 위가 없음)
     */
    public record PlannedStep(int stepOrder, Long approverId, String approverName, SkipReason skipReason) {

        public boolean skipped() {
            return skipReason != null;
        }
    }

    public enum SkipReason { SELF, SAME_AS_PREVIOUS, TOP_OF_ORG }
}
