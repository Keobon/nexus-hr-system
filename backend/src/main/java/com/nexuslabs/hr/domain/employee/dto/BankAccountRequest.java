package com.nexuslabs.hr.domain.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/employees/{id}/bank-account · /api/me/bank-account 본문(F-PAY-03). 직원마다 1개이고 바꾸면 덮어쓴다.
 * bankName 은 화면의 고정 목록에서 고른 이름, accountNo 는 숫자와 하이픈.
 */
public record BankAccountRequest(
        @NotBlank @Size(max = 30) String bankName,
        @NotBlank @Size(max = 40) String accountNo,
        @NotBlank @Size(max = 50) String holder) {
}
