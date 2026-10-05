package com.nexuslabs.hr.global.error;

import org.springframework.http.HttpStatus;

/**
 * API 설계서 1.6의 에러 코드. 코드를 추가하면 문서에도 추가하고 프론트에 알린다.
 */
public enum ErrorCode {
    // 공통
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인하세요"),
    INVALID_ENUM_VALUE(HttpStatus.BAD_REQUEST, "허용되지 않은 코드값입니다"),
    BUSINESS_RULE_VIOLATION(HttpStatus.BAD_REQUEST, "처리할 수 없는 요청입니다"),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    OUT_OF_SCOPE(HttpStatus.FORBIDDEN, "조회할 수 있는 범위가 아닙니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다"),
    DUPLICATE_NAME(HttpStatus.CONFLICT, "같은 이름이 이미 있습니다"),
    INVALID_STATE(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없습니다"),
    ITEM_IN_USE(HttpStatus.CONFLICT, "사용 중이라 변경할 수 없습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요"),

    // 인증·계정·회사
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않습니다"),
    AUTH_ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "비밀번호를 여러 번 틀려 계정이 잠겼습니다"),
    AUTH_ACCOUNT_INACTIVE(HttpStatus.UNAUTHORIZED, "사용할 수 없는 계정입니다"),
    AUTH_PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "비밀번호를 먼저 변경해 주세요"),
    AUTH_WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 맞지 않습니다"),
    EMAIL_DUPLICATE(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
    BUSINESS_REG_NO_DUPLICATE(HttpStatus.CONFLICT, "이미 등록된 사업자등록번호입니다"),
    LAST_SUPER_ADMIN(HttpStatus.CONFLICT, "마지막 최고 관리자는 변경할 수 없습니다"),
    SYSTEM_ROLE_READONLY(HttpStatus.CONFLICT, "최고 관리자 역할은 수정할 수 없습니다"),

    // 조직·직원
    ORG_CYCLE(HttpStatus.BAD_REQUEST, "하위 조직 아래로는 옮길 수 없습니다"),
    ORG_ROOT_LOCKED(HttpStatus.CONFLICT, "최상위 조직은 바꿀 수 없습니다"),
    ORG_LEAD_NOT_MEMBER(HttpStatus.BAD_REQUEST, "조직장은 그 조직 소속이어야 합니다"),
    ORG_HAS_MEMBERS(HttpStatus.CONFLICT, "소속 직원이나 하위 조직이 있어 처리할 수 없습니다"),
    INACTIVE_REFERENCE(HttpStatus.BAD_REQUEST, "사용 중지된 항목은 선택할 수 없습니다"),
    EMPLOYEE_NO_DUPLICATE(HttpStatus.CONFLICT, "이미 사용 중인 사원번호입니다"),
    EMPLOYEE_NOT_ACTIVE(HttpStatus.CONFLICT, "재직 중인 직원이 아닙니다"),
    EMPLOYEE_RESIGNED(HttpStatus.CONFLICT, "퇴직한 직원입니다"),

    // 근태·연장·출장
    SCHEDULE_PAST_DATE(HttpStatus.BAD_REQUEST, "적용 시작일은 오늘 이후여야 합니다"),
    ATT_ALREADY_CHECKED_IN(HttpStatus.CONFLICT, "이미 출근했습니다"),
    ATT_ALREADY_CHECKED_OUT(HttpStatus.CONFLICT, "이미 퇴근했습니다"),
    ATT_NO_CHECK_IN(HttpStatus.CONFLICT, "출근 기록이 없습니다"),
    ATT_ON_LEAVE_OR_TRIP(HttpStatus.CONFLICT, "휴가·출장일에는 출퇴근할 수 없습니다"),
    PERIOD_OVERLAP(HttpStatus.CONFLICT, "같은 기간에 다른 휴가·출장이 있습니다"),
    OVERTIME_DUPLICATE(HttpStatus.CONFLICT, "이 날짜에 진행 중인 연장근무 신청이 있습니다"),
    EXPENSE_CLAIM_DUPLICATE(HttpStatus.CONFLICT, "이 출장에 진행 중인 경비 청구가 있습니다"),
    EXPENSE_RECEIPT_REQUIRED(HttpStatus.BAD_REQUEST, "영수증이 필요한 경비가 있습니다"),

    // 휴가·승인
    LEAVE_INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "잔여 일수가 부족합니다"),
    LEAVE_ZERO_DAYS(HttpStatus.BAD_REQUEST, "선택한 기간에 근무일이 없습니다"),
    LEAVE_CROSS_YEAR(HttpStatus.BAD_REQUEST, "휴가 연도가 바뀌는 기간은 나눠서 신청하세요"),
    LEAVE_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작된 휴가는 취소할 수 없습니다"),
    APPROVER_NOT_FOUND(HttpStatus.CONFLICT, "승인자가 지정되지 않았습니다. 관리자에게 문의하세요"),
    APPROVAL_NOT_MY_TURN(HttpStatus.FORBIDDEN, "지금 처리할 차례가 아닙니다"),
    APPROVAL_ALREADY_DONE(HttpStatus.CONFLICT, "이미 처리된 건입니다"),
    APPROVAL_DEFAULT_LOCKED(HttpStatus.CONFLICT, "기본 승인선은 삭제하거나 조건을 지정할 수 없습니다"),

    // 급여
    PAY_MONTH_SETTLED(HttpStatus.CONFLICT, "이미 정산된 월입니다"),
    TAX_BRACKET_INVALID(HttpStatus.BAD_REQUEST, "세율 구간이 0원부터 빈틈없이 이어져야 합니다"),

    // 평가
    EVAL_WEIGHT_SUM_INVALID(HttpStatus.BAD_REQUEST, "항목 배점 합계가 100이어야 합니다"),
    EVAL_NO_QUESTION(HttpStatus.BAD_REQUEST, "질문이 없는 항목이 있습니다"),
    EVAL_TEMPLATE_IN_USE(HttpStatus.CONFLICT, "사용된 템플릿은 복사해서 수정하세요"),
    EVAL_NO_TARGET(HttpStatus.CONFLICT, "평가 대상자가 없습니다"),
    EVAL_INCOMPLETE(HttpStatus.BAD_REQUEST, "모든 질문과 종합의견을 입력하세요"),
    EVAL_PERIOD_CLOSED(HttpStatus.CONFLICT, "평가 기간이 종료되었습니다"),

    // 파일
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "10MB 이하 파일만 올릴 수 있습니다"),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "jpg, png, pdf만 올릴 수 있습니다");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() { return status; }

    public String defaultMessage() { return defaultMessage; }
}
