package com.nexuslabs.hr.domain.account.dto;

import com.nexuslabs.hr.domain.account.service.PasswordRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** "새 비밀번호 확인" 일치는 화면에서 검사한다. */
public record PasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE) String newPassword) {
}
