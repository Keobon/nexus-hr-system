// 에러 코드 → 화면 메시지(프론트 가이드 5장). 표에 없는 코드는 서버 error.message 를 그대로 쓴다.
import { formatDateTime } from '../utils/format';

const MESSAGES = {
  AUTH_INVALID_CREDENTIALS: '이메일 또는 비밀번호가 맞지 않습니다',
  AUTH_ACCOUNT_LOCKED: (d) =>
    `비밀번호를 5번 틀려 잠겼습니다. ${d?.lockedUntil ? formatDateTime(d.lockedUntil) : '잠시'} 이후 다시 시도하세요`,
  AUTH_ACCOUNT_INACTIVE: '사용할 수 없는 계정입니다. 관리자에게 문의하세요',
  AUTH_TOKEN_EXPIRED: '다시 로그인해 주세요',
  AUTH_WRONG_PASSWORD: '현재 비밀번호가 맞지 않습니다',
  EMAIL_DUPLICATE: '이미 사용 중인 이메일입니다',
  BUSINESS_REG_NO_DUPLICATE: '이미 등록된 사업자등록번호입니다',
  EMPLOYEE_NO_DUPLICATE: '이미 사용 중인 사원번호입니다',
  DUPLICATE_NAME: '같은 이름이 이미 있습니다',
  LAST_SUPER_ADMIN: '마지막 최고 관리자는 변경하거나 비활성화할 수 없습니다',
  ORG_CYCLE: '하위 조직 아래로는 옮길 수 없습니다',
  ORG_ROOT_LOCKED: '최상위 조직은 바꿀 수 없습니다',
  ORG_HAS_MEMBERS: '소속 직원이나 하위 조직이 있어 처리할 수 없습니다',
  INACTIVE_REFERENCE: '사용 중지된 항목은 선택할 수 없습니다',
  ATT_ALREADY_CHECKED_IN: '이미 출근했습니다',
  ATT_NO_CHECK_IN: '출근 기록이 없습니다',
  ATT_ON_LEAVE_OR_TRIP: '오늘은 휴가·출장일이라 출퇴근할 수 없습니다',
  PERIOD_OVERLAP: '같은 기간에 다른 휴가·출장이 있습니다',
  LEAVE_INSUFFICIENT_BALANCE: (d) =>
    d ? `잔여 일수가 부족합니다(남은 ${d.remaining}일, 신청 ${d.requested}일)` : '잔여 일수가 부족합니다',
  LEAVE_ZERO_DAYS: '선택한 기간에 근무일이 없습니다',
  LEAVE_CROSS_YEAR: '휴가 연도가 바뀌는 기간은 나눠서 신청하세요',
  LEAVE_ALREADY_STARTED: '이미 시작된 휴가는 취소할 수 없습니다',
  APPROVER_NOT_FOUND: '승인자가 지정되지 않았습니다. 관리자에게 문의하세요',
  APPROVAL_NOT_MY_TURN: '지금 처리할 차례가 아닙니다',
  APPROVAL_ALREADY_DONE: '이미 처리된 건입니다',
  OVERTIME_DUPLICATE: '이 날짜에 진행 중인 연장근무 신청이 있습니다',
  PAY_MONTH_SETTLED: '이미 정산된 월입니다',
  EXPENSE_RECEIPT_REQUIRED: '영수증이 필요한 경비가 있습니다',
  EVAL_WEIGHT_SUM_INVALID: '항목 배점 합계가 100이어야 합니다',
  EVAL_INCOMPLETE: '모든 질문과 종합의견을 입력하세요',
  FILE_TOO_LARGE: '10MB 이하 파일만 올릴 수 있습니다',
  FILE_TYPE_NOT_ALLOWED: 'jpg, png, pdf만 올릴 수 있습니다',
  INTERNAL_ERROR: '잠시 후 다시 시도해 주세요',
};

/** ApiError → 화면에 보여 줄 문장 */
export function errorMessage(err) {
  if (!err) return '';
  const m = MESSAGES[err.code];
  if (typeof m === 'function') return m(err.details);
  return m ?? err.message ?? MESSAGES.INTERNAL_ERROR;
}

// 성공 응답의 data.warning(프론트 가이드 2.1) — 성공 처리하고 경고 토스트만 띄운다
const WARNINGS = {
  PAY_MONTH_SETTLED: '이미 정산된 월입니다. 차액은 다음 달 소급 조정으로 처리하세요',
  PERIOD_OVERLAP: '같은 기간에 다른 휴가·출장이 있습니다',
  PAY_MONTH_NOT_ENDED: '아직 끝나지 않은 달입니다. 남은 날의 근태가 빠지고, 확정은 월이 끝난 뒤에 할 수 있습니다',
};

export const warningMessage = (code) => (code ? WARNINGS[code] ?? null : null);
