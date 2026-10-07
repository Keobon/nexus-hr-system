package com.nexuslabs.hr.global.request;

import com.nexuslabs.hr.global.error.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * PATCH 본문 검사(API 설계서 1.1: 보낸 필드만 바꾸고 null 은 비운다).
 * 컨트롤러가 본문을 Map 으로 받아야 "안 보냄"과 "null"을 구별할 수 있다.
 */
public final class PatchRequest {

    private PatchRequest() {}

    /** 모르는 필드, 비울 수 없는 필드에 null → VALIDATION_ERROR(error.fields). */
    public static void check(Map<String, Object> patch, Set<String> allowed, Set<String> notNull) {
        Map<String, String> errors = new LinkedHashMap<>();
        patch.forEach((field, value) -> {
            if (!allowed.contains(field)) {
                errors.put(field, "알 수 없는 항목입니다");
            } else if (value == null && notNull.contains(field)) {
                errors.put(field, "비울 수 없습니다");
            }
        });
        if (!errors.isEmpty()) {
            throw BusinessException.invalidFields(errors);
        }
    }
}
