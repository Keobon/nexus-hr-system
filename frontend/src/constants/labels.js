// 코드값 → 한글 라벨 · 배지 색(프론트 가이드 부록 A).
// 화면마다 if (status === 'PENDING') 를 쓰지 않고 여기서 찾는다. 색은 AntD Tag color.
// 권한 코드 18개의 설명은 GET /permissions 응답을 쓴다(여기에 두지 않음).

const L = (label, color = 'default') => ({ label, color });

export const LABELS = {
  leaveStatus: {
    PENDING: L('승인대기', 'gold'),
    APPROVED: L('승인완료', 'green'),
    REJECTED: L('반려', 'red'),
    CANCEL_REQUESTED: L('취소요청', 'orange'),
    CANCELLED: L('취소완료'),
  },
  // 연장근무 · 출장 · 출장 경비
  requestStatus: {
    PENDING: L('승인대기', 'gold'),
    APPROVED: L('승인완료', 'green'),
    REJECTED: L('반려', 'red'),
    CANCELLED: L('철회'),
  },
  approvalStepStatus: {
    WAITING: L('대기'),
    PENDING: L('승인 중', 'blue'),
    APPROVED: L('승인', 'green'),
    REJECTED: L('반려', 'red'),
    SKIPPED: L('생략'),
    CANCELLED: L('취소'),
  },
  attendanceStatus: {
    CHECKED_IN: L('출근', 'blue'),
    CHECKED_OUT: L('퇴근', 'green'),
    MISSING_CHECKOUT: L('퇴근미기록', 'orange'),
    ON_VACATION: L('휴가', 'purple'),
    ON_BUSINESS_TRIP: L('출장', 'cyan'),
    ON_LEAVE: L('휴직'),
    NOT_RECORDED: L('미기록'),
    ABSENT: L('결근', 'red'),
  },
  todayStatus: {
    BEFORE_WORK: L('출근 전'),
    OFFICE: L('사내 근무', 'blue'),
    REMOTE: L('재택', 'geekblue'),
    FIELD: L('외근', 'cyan'),
    BUSINESS_TRIP: L('출장', 'cyan'),
    ON_VACATION: L('휴가', 'purple'),
    ON_LEAVE: L('휴직'),
    OFF_WORK: L('퇴근', 'green'),
  },
  workType: {
    OFFICE: L('사내'),
    REMOTE: L('재택'),
    FIELD: L('외근'),
    BUSINESS_TRIP: L('출장'),
  },
  recordSource: {
    WEB: L('웹'),
    MOBILE: L('모바일'),
    ADMIN: L('관리자 정정', 'orange'),
    SYSTEM: L('자동'),
  },
  employmentStatus: {
    ACTIVE: L('재직중', 'green'),
    ON_LEAVE: L('휴직', 'gold'),
    RESIGNED: L('퇴직'),
  },
  permissionScope: {
    TEAM: L('팀', 'blue'),
    ALL: L('전사', 'purple'),
  },
  approvalWorkType: {
    LEAVE: L('휴가', 'purple'),
    LEAVE_CANCEL: L('휴가 취소', 'orange'),
    OVERTIME: L('연장근무', 'blue'),
    BUSINESS_TRIP: L('출장', 'cyan'),
    TRIP_EXPENSE: L('출장 경비', 'green'),
  },
  approverType: {
    ORG_LEAD: L('소속 조직장'),
    ORG_LEAD_UP: L('N단계 위 조직장'),
    JOB_TITLE: L('특정 직책'),
    EMPLOYEE: L('특정 직원'),
  },
  leaveGrantType: {
    REGULAR: L('정기'),
    HIRE: L('입사'),
    ADJUSTMENT: L('조정'),
  },
  payItemCategory: {
    EARNING: L('지급', 'blue'),
    DEDUCTION: L('공제', 'red'),
  },
  payItemCalcType: {
    FIXED: L('고정액'),
    BASE_RATE: L('기본급 대비 %'),
    MANUAL: L('매월 수동 입력'),
    TAXABLE_RATE: L('과세액 대비 요율'),
    ATTENDANCE: L('근태 연동'),
    TRIP_EXPENSE: L('출장 경비'),
  },
  payItemTarget: {
    ALL: L('전원'),
    SELECTED: L('지정 직원'),
  },
  attendanceBasis: {
    OVERTIME: L('연장'),
    NIGHT: L('야간'),
    HOLIDAY: L('휴일'),
    HOLIDAY_OVERTIME: L('휴일연장'),
    ABSENCE: L('결근'),
  },
  payVariable: {
    DEPENDENT_DEDUCTION: L('부양가족 1인당 공제액'),
    MULTI_CHILD_DEDUCTION: L('다자녀 추가 공제액'),
    LOCAL_TAX_RATE: L('지방소득세율'),
    ANNUAL_SPLIT_MONTHS: L('연봉 분할 개월 수'),
    MONTHLY_STANDARD_HOURS: L('월 소정근로시간'),
  },
  salaryType: {
    MONTHLY: L('월급제'),
    ANNUAL: L('연봉제'),
  },
  payrollStatus: {
    CONFIRMED: L('확정', 'blue'),
    PAID: L('지급완료', 'green'),
  },
  assignmentType: {
    TRANSFER: L('조직 이동', 'blue'),
    PROMOTION: L('승진', 'green'),
    TITLE_CHANGE: L('직책 변경', 'purple'),
  },
  evaluationStatus: {
    NOT_STARTED: L('평가전'),
    IN_PROGRESS: L('작성중', 'blue'),
    SUBMITTED: L('제출완료', 'cyan'),
    CONFIRMED: L('확정', 'green'),
    REOPENED: L('재오픈', 'orange'),
  },
  evalCycleStatus: {
    SCHEDULED: L('예정'),
    IN_PROGRESS: L('진행', 'blue'),
    CLOSED: L('종료'),
  },
  holidayType: {
    PUBLIC: L('법정 공휴일', 'red'),
    COMPANY: L('회사 지정', 'blue'),
    SUBSTITUTE: L('대체 공휴일', 'orange'),
  },
  tripType: {
    DOMESTIC: L('국내'),
    OVERSEAS: L('해외', 'purple'),
  },
  gender: {
    MALE: L('남'),
    FEMALE: L('여'),
  },
  familyRelation: {
    SPOUSE: L('배우자'),
    CHILD: L('자녀'),
    PARENT: L('부모'),
    SIBLING: L('형제자매'),
    OTHER: L('기타'),
  },
  companyDocumentType: {
    BUSINESS_REGISTRATION: L('사업자등록증'),
    CORP_REGISTRATION: L('법인등기부등본'),
    BANK_ACCOUNT_COPY: L('통장 사본'),
    OTHER: L('기타'),
  },
  employeeDocumentType: {
    LABOR_CONTRACT: L('근로계약서'),
    BANKBOOK_COPY: L('통장 사본'),
    RESUME: L('이력서'),
    FAMILY_CERT: L('가족관계증명서'),
    HEALTH_CHECK: L('건강검진 결과'),
    OTHER: L('기타'),
  },
  fieldType: {
    TEXT: L('텍스트'),
    LONG_TEXT: L('긴 텍스트'),
    NUMBER: L('숫자'),
    DATE: L('날짜'),
    SELECT: L('선택'),
  },
  auditAction: {
    CREATE: L('생성', 'green'),
    UPDATE: L('수정', 'blue'),
    DELETE: L('삭제', 'red'),
    EXECUTE: L('실행', 'purple'),
    VIEW: L('조회'),
  },
};

/** 라벨만. 모르는 코드는 코드를 그대로 보여 주지 않고 '-' */
export function labelOf(group, code) {
  if (code === null || code === undefined) return '-';
  return LABELS[group]?.[code]?.label ?? '-';
}

/** Select 의 options — [{ value, label }] */
export function optionsOf(group) {
  return Object.entries(LABELS[group] ?? {}).map(([value, { label }]) => ({ value, label }));
}
