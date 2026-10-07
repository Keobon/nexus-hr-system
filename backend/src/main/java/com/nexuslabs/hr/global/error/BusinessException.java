package com.nexuslabs.hr.global.error;

import java.util.Map;

/** 서비스가 업무 규칙 위반을 알릴 때 던진다. HTTP 응답은 GlobalExceptionHandler가 만든다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;
    private final Map<String, String> fields;

    public BusinessException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, Map<String, Object> details) {
        this(code, code.defaultMessage(), details);
    }

    public BusinessException(ErrorCode code, String message, Map<String, Object> details) {
        this(code, message, details, null);
    }

    private BusinessException(ErrorCode code, String message, Map<String, Object> details, Map<String, String> fields) {
        super(message);
        this.code = code;
        this.details = details;
        this.fields = fields;
    }

    /** 항목별 입력 오류(응답 error.fields). @Valid 로 검사할 수 없는 PATCH 병합 결과 등에 쓴다. */
    public static BusinessException invalidFields(Map<String, String> fields) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return new BusinessException(code, code.defaultMessage(), null, fields);
    }

    public ErrorCode code() { return code; }

    public Map<String, Object> details() { return details; }

    public Map<String, String> fields() { return fields; }
}
