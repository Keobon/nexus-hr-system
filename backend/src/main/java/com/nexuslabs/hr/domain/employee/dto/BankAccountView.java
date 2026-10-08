package com.nexuslabs.hr.domain.employee.dto;

/**
 * 급여 계좌(F-PAY-03, BR-PAY-014). accountNoMasked = "***-****-" + 뒤 4자리(명세서 스냅샷과 같은 표기).
 * accountNo(전체 번호)는 ?reveal=true 일 때만 채우고 아니면 null. 등록 전이면 응답 data 자체가 null.
 */
public record BankAccountView(String bankName, String accountNoMasked, String accountNo, String holder) {
}
