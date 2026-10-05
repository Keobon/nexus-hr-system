package com.nexuslabs.hr.global.error;

import java.util.Map;

/** 서비스가 업무 규칙 위반을 알릴 때 던진다. HTTP 응답은 GlobalExceptionHandler가 만든다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

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
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() { return code; }

    public Map<String, Object> details() { return details; }
}
