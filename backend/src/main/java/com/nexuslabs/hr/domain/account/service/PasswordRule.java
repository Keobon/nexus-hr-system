package com.nexuslabs.hr.domain.account.service;

/** 비밀번호 규칙(F-COMP-01, F-AUTH-03): 8자 이상, 영문과 숫자 포함. BCrypt 한계(72바이트) 때문에 64자까지. */
public final class PasswordRule {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    public static final String MESSAGE = "8자 이상, 영문과 숫자를 포함해야 합니다";

    private PasswordRule() {
    }
}
