package com.nexuslabs.hr.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** 실패 응답의 error 부분. details·fields는 있을 때만 내려간다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(String code, String message, Map<String, Object> details, Map<String, String> fields) {

    public static ErrorBody of(String code, String message) {
        return new ErrorBody(code, message, null, null);
    }
}
