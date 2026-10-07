package com.nexuslabs.hr.domain.account.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 임시 비밀번호(무작위 10자, F-AUTH-04). 비밀번호 규칙(영문·숫자 포함)을 항상 만족하고,
 * 화면에서 옮겨 적기 쉽게 헷갈리는 글자(0 O o 1 l I)는 뺀다.
 */
@Component
public class TemporaryPasswordGenerator {

    static final int LENGTH = 10;
    private static final String LETTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String ALL = LETTERS + DIGITS;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] chars = new char[LENGTH];
        chars[0] = pick(LETTERS);
        chars[1] = pick(DIGITS);
        for (int i = 2; i < LENGTH; i++) {
            chars[i] = pick(ALL);
        }
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char t = chars[i];
            chars[i] = chars[j];
            chars[j] = t;
        }
        return new String(chars);
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }
}
