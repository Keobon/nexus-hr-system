// 급여 설정 탭들이 같이 쓰는 라벨 · 표시 함수
import { labelOf } from '../../../constants/labels';
import { formatMoney } from '../../../utils/format';
import iconLock from '../../../assets/figma/icon-lock.svg';

/** 계산 방식 — Figma 문구 */
export const CALC_LABEL = {
  FIXED: '고정액',
  BASE_RATE: '기본급 대비 %',
  MANUAL: '매월 직접 입력',
  ATTENDANCE: '근태 연동',
  TRIP_EXPENSE: '출장 경비 연동',
  TAXABLE_RATE: '4대보험',
};
/** 구분마다 고를 수 있는 계산 방식(4대보험 · 출장 경비는 각자 자리에서) */
export const CALC_BY_KIND = {
  EARNING: ['FIXED', 'BASE_RATE', 'MANUAL', 'ATTENDANCE', 'TRIP_EXPENSE'],
  DEDUCTION: ['FIXED', 'BASE_RATE', 'MANUAL', 'ATTENDANCE', 'TAXABLE_RATE'],
};
export const KIND_LABEL = { EARNING: '지급', DEDUCTION: '공제' };

const num = (n) => Number(n).toLocaleString('ko-KR', { maximumFractionDigits: 4 });
export const pct = (n) => (n == null ? '–' : `${num(n)}%`);
export const won = (n) => (n == null ? '–' : formatMoney(n));

/** 금액 · 요율 칸 */
export function amountText(it) {
  switch (it.calcMethod) {
    case 'FIXED':
      return it.defaultAmount != null ? formatMoney(it.defaultAmount) : '직원별 금액';
    case 'BASE_RATE':
      return `기본급 × ${pct(it.baseRate)}`;
    case 'ATTENDANCE':
      return `${it.attendanceBasis === 'ABSENCE' ? '결근 시간' : `${labelOf('attendanceBasis', it.attendanceBasis)} 시간`} × 통상시급 × ${num(it.multiplier)}`;
    case 'TRIP_EXPENSE':
      return '승인된 경비';
    case 'TAXABLE_RATE':
      return `근로자 ${pct(it.employeeRate)} · 회사 ${pct(it.companyRate)}`;
    default:
      return '–';
  }
}

export function KindBadge({ kind }) {
  return <span className={`pay-kind is-${kind === 'DEDUCTION' ? 'ded' : 'earn'}`}>{KIND_LABEL[kind]}</span>;
}

export function Lock() {
  return <img src={iconLock} alt="고정 항목" className="pay-lock" />;
}

/** 계산 변수 — 단위와 입력 범위(서버가 다시 검사) */
export const VAR_META = {
  MONTHLY_STANDARD_HOURS: { unit: '시간', step: 1, desc: '통상시급 = 월 통상임금 ÷ 월 소정근로시간' },
  ANNUAL_SPLIT_MONTHS: { unit: '개월', step: 1, desc: '월 기본급 = 연봉 ÷ 분할 개월' },
  LOCAL_TAX_RATE: { unit: '%', step: 0.1, desc: '지방소득세 = 소득세 × 이 비율' },
  DEPENDENT_DEDUCTION: { unit: '원', step: 1000, desc: '과세표준에서 부양가족 1명마다 빼는 금액' },
  MULTI_CHILD_DEDUCTION: { unit: '원', step: 1000, desc: '자녀 2명부터 1명마다 더 빼는 금액' },
};
export const VAR_ORDER = ['MONTHLY_STANDARD_HOURS', 'ANNUAL_SPLIT_MONTHS', 'LOCAL_TAX_RATE', 'DEPENDENT_DEDUCTION', 'MULTI_CHILD_DEDUCTION'];
export function varText(code, value) {
  if (value == null) return '–';
  const unit = VAR_META[code]?.unit;
  return unit === '원' ? formatMoney(value) : `${num(value)}${unit ?? ''}`;
}
