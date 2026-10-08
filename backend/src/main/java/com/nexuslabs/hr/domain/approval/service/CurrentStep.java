package com.nexuslabs.hr.domain.approval.service;

/**
 * 목록 행의 현재 승인 진행(API 설계서 8장 "휴가 신청 목록 행"의 currentStep).
 * stepOrder/totalSteps 는 승인함과 같은 기준이다(그 회차의 단계 번호 / 그 회차의 단계 수, 생략 단계 포함).
 */
public record CurrentStep(int stepOrder, int totalSteps, String approverName, ApprovalStepStatus status) {
}
