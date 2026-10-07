package com.nexuslabs.hr.domain.approval.service;

import java.util.Map;

/**
 * 승인 대상 요약(F-APPR-02).
 *
 * @param applicantId      신청자 직원 ID
 * @param title            한 줄 요약. 예) "연차 10/1–10/2 (2일)"
 * @param details          업무별 상세(휴가: 종류·기간·일수·사유·잔여 / 연장근무: 근무일·시간·사유 / 출장: 출장지·기간·목적 / 경비: 줄·합계·영수증)
 * @param requestedMinutes 연장근무만 — 신청 시간(분). 첫 단계가 인정할 수 있는 최대값. 그 밖의 업무는 null
 */
public record TargetSummary(long applicantId, String title, Map<String, Object> details, Integer requestedMinutes) {
}
