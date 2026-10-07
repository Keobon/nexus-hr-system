package com.nexuslabs.hr.domain.company.dto;

import com.nexuslabs.hr.domain.account.service.PasswordRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/companies (API 설계서 2장). 나머지 회사 정보는 이후 PATCH /company 로 입력한다. */
public record CompanyRegisterRequest(@NotNull @Valid CompanyInfo company, @NotNull @Valid AdminInfo admin) {

    public record CompanyInfo(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "^\\d{3}-?\\d{2}-?\\d{5}$", message = "사업자등록번호는 숫자 10자리입니다")
            String businessRegNo,
            @NotBlank @Size(max = 50) String ceoName,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Size(max = 20) String phone,
            @NotBlank @Email @Size(max = 100) String email) {
    }

    public record AdminInfo(
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE) String password) {
    }
}
