package com.nexuslabs.hr.global.error;

import com.nexuslabs.hr.global.response.ApiResponse;
import com.nexuslabs.hr.global.response.ErrorBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 예외를 API 설계서 1.3 형식의 실패 응답으로 바꾼다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** DB 제약 이름 → 에러 코드. 서비스 검증을 빠져나온 경우의 마지막 방어선. */
    private static final List<Map.Entry<String, ErrorCode>> CONSTRAINT_CODES = List.of(
            Map.entry("employee_email_key", ErrorCode.EMAIL_DUPLICATE),
            Map.entry("company_business_reg_no_key", ErrorCode.BUSINESS_REG_NO_DUPLICATE),
            Map.entry("employee_company_id_employee_no_key", ErrorCode.EMPLOYEE_NO_DUPLICATE),
            Map.entry("ux_overtime_active", ErrorCode.OVERTIME_DUPLICATE),
            Map.entry("ux_expense_claim_active", ErrorCode.EXPENSE_CLAIM_DUPLICATE),
            Map.entry("attendance_employee_id_work_date_key", ErrorCode.ATT_ALREADY_CHECKED_IN),
            Map.entry("_daterange_excl", ErrorCode.PERIOD_OVERLAP),
            Map.entry("_name_key", ErrorCode.DUPLICATE_NAME),
            Map.entry("ux_org_unit_name", ErrorCode.DUPLICATE_NAME));

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return respond(e.code(), new ErrorBody(e.code().name(), e.getMessage(), e.details(), e.fields()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return respond(code, new ErrorBody(code.name(), code.defaultMessage(), null, fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof InvalidFormatException ife && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {
            return invalidEnum(ife.getTargetType());
        }
        return simple(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        if (e.getRequiredType() != null && e.getRequiredType().isEnum()) {
            return invalidEnum(e.getRequiredType());
        }
        return simple(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(Exception e) {
        return simple(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return simple(ErrorCode.FILE_TOO_LARGE);
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoResource(Exception e) {
        return simple(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        for (Map.Entry<String, ErrorCode> entry : CONSTRAINT_CODES) {
            if (message.contains(entry.getKey())) {
                return simple(entry.getValue());
            }
        }
        // append-only 트리거 위반이나 알 수 없는 제약은 코드 버그다
        log.error("처리되지 않은 DB 제약 위반", e);
        return simple(ErrorCode.INTERNAL_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("예상하지 못한 오류", e);
        return simple(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> invalidEnum(Class<?> enumType) {
        List<String> allowed = Arrays.stream(enumType.getEnumConstants()).map(Object::toString).toList();
        ErrorCode code = ErrorCode.INVALID_ENUM_VALUE;
        return respond(code, new ErrorBody(code.name(), code.defaultMessage(), Map.of("allowed", allowed), null));
    }

    private ResponseEntity<ApiResponse<Void>> simple(ErrorCode code) {
        return respond(code, ErrorBody.of(code.name(), code.defaultMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, ErrorBody body) {
        return ResponseEntity.status(code.status()).body(ApiResponse.fail(body));
    }
}
