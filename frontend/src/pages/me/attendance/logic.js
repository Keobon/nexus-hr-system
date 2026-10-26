// 출퇴근 화면 계산 도우미 — 근태 규칙(지각 · 연장 등)은 서버 값 그대로 쓰고,
// 여기서는 날짜 범위를 고르고 서버가 준 분(minute) 값을 더하기만 한다(프론트 가이드: 계산을 다시 하지 않는다).
import dayjs from 'dayjs';

export const WEEK_LIMIT = 52 * 60; // 주 52시간(연장 포함 법정 한도)
export const WEEK_OT_LIMIT = 12 * 60; // 주 연장 12시간

/** 510 → "08시간 30분" (Figma 표기 — 시간은 두 자리) */
export function hm(minutes) {
  if (minutes === null || minutes === undefined) return '-';
  const m = Math.max(0, Math.round(minutes));
  return `${String(Math.floor(m / 60)).padStart(2, '0')}시간 ${String(m % 60).padStart(2, '0')}분`;
}

/** "2026-10-08T09:03:12+09:00" → "09:03" (오프셋 그대로) */
export const clock = (iso) => (iso ? iso.slice(11, 16) : '-');

/** "2026-10-08" → "2026.10.08" */
export const dot = (date) => (date ? date.replaceAll('-', '.') : '-');

export const monthOf = (date) => date.slice(0, 7);
export const prevMonth = (month) => dayjs(`${month}-01`).subtract(1, 'month').format('YYYY-MM');

/** 그 날짜가 든 주(월요일 시작)의 날짜 7개 */
export function weekDates(date) {
  const d = dayjs(date);
  const monday = d.subtract((d.day() + 6) % 7, 'day');
  return Array.from({ length: 7 }, (_, i) => monday.add(i, 'day').format('YYYY-MM-DD'));
}

/** 오늘부터 거꾸로 n일 */
export const lastDates = (date, n) => Array.from({ length: n }, (_, i) => dayjs(date).subtract(i, 'day').format('YYYY-MM-DD'));

/** 근무 · 연장 합계 — 기본 = 근무 − 연장(평일 연장 + 휴일 연장) */
export function sumMinutes(days) {
  const sum = (k) => days.reduce((acc, d) => acc + (d?.[k] ?? 0), 0);
  const work = sum('workMinutes');
  const overtime = sum('overtimeMinutes') + sum('holidayOvertimeMinutes');
  return { work, overtime, base: Math.max(0, work - overtime) };
}

/**
 * 하루 근태 → 표 배지(tone · label). 근태 상태 코드(부록 A) + 지각 · 조퇴 여부
 * 휴무: 근무일이 아니고 기록이 없는 날 / 예정: 오늘 이후 근무일
 */
export function dayBadge(day, today) {
  if (!day) return { tone: '', label: '-' };
  const s = day.status;
  if (!s) return day.isWorkday ? { tone: '', label: '-' } : { tone: '', label: day.holidayName ?? '휴무' };
  if (s === 'CHECKED_IN') return { tone: 'green', label: '근무중' };
  if (s === 'CHECKED_OUT') {
    if (day.late) return { tone: 'red', label: '지각' };
    if (day.earlyLeave) return { tone: 'red', label: '조퇴' };
    return { tone: 'blue', label: '정상' };
  }
  if (s === 'MISSING_CHECKOUT') return { tone: 'amber', label: '퇴근미기록' };
  if (s === 'ON_VACATION') return { tone: 'purple', label: '휴가' };
  if (s === 'ON_BUSINESS_TRIP') return { tone: 'amber', label: '출장' };
  if (s === 'ABSENT') return { tone: 'red', label: '결근' };
  if (s === 'ON_LEAVE') return { tone: '', label: '휴직' };
  if (s === 'NOT_RECORDED') return { tone: '', label: day.date > today ? '예정' : day.date === today ? '출근 전' : '미기록' };
  return { tone: '', label: '-' };
}

/** 하루 근태 → 달력 칸(Figma 색 이름 · 글자) */
export function dayCell(day, today) {
  if (!day) return { cls: 'off', label: '—' };
  const s = day.status;
  if (!s) return { cls: 'off', label: day.holidayName ?? (day.isWorkday ? '—' : '휴무') };
  if (s === 'CHECKED_IN') return { cls: 'ok', label: '근무중' };
  if (s === 'CHECKED_OUT') {
    if (day.late) return { cls: 'late', label: '지각' };
    if (day.earlyLeave) return { cls: 'late', label: '조퇴' };
    if (day.workType === 'FIELD') return { cls: 'field', label: '외근' };
    if (day.workType === 'REMOTE') return { cls: 'remote', label: '재택' };
    return { cls: 'ok', label: '정상' };
  }
  if (s === 'MISSING_CHECKOUT') return { cls: 'missing', label: '미체크' };
  if (s === 'ON_BUSINESS_TRIP') return { cls: 'trip', label: '출장' };
  if (s === 'ON_VACATION') return { cls: 'vacation', label: '휴가' };
  if (s === 'ABSENT') return { cls: 'absent', label: '결근' };
  if (s === 'ON_LEAVE') return { cls: 'off', label: '휴직' };
  if (s === 'NOT_RECORDED') return { cls: '', label: day.date > today ? '예정' : day.date === today ? '출근 전' : '미기록' };
  return { cls: 'off', label: '—' };
}

/** 표의 비고 — 휴일 이름 · 근무 형태 · 자정 넘긴 퇴근 */
/**
 * 소명할 수 있는 날(F-ATT-10) — 어제까지의 지각 · 조퇴 · 퇴근미기록 · 결근 · (지난) 출근 상태인 날, 대기 중인 소명이 없을 때.
 * 그 달 정산 여부는 서버가 본다(PAY_MONTH_SETTLED)
 */
export function canExplain(day, today) {
  if (!day || day.date >= today || day.explanationStatus === 'PENDING') return false;
  return !!(day.late || day.earlyLeave) || ['MISSING_CHECKOUT', 'ABSENT', 'CHECKED_IN'].includes(day.status);
}

/** 소명 상태 배지 */
export const EXPLANATION_BADGE = {
  PENDING: ['amber', '소명 대기'],
  APPROVED: ['blue', '소명 승인'],
  REJECTED: ['red', '소명 반려'],
  CANCELLED: ['', '소명 철회'],
};

export function dayNote(day) {
  if (!day) return '-';
  if (day.holidayName) return day.holidayName;
  if (day.checkOutAt && day.checkOutAt.slice(0, 10) !== day.date) return '자정 넘긴 퇴근';
  if (day.workType === 'REMOTE') return '재택 근무';
  if (day.workType === 'FIELD') return '외근';
  if (day.status === 'CHECKED_OUT') return '정상 출퇴근';
  return '-';
}

/** 그 달 달력 칸 — 앞뒤 빈칸은 이전 · 다음 달 날짜(흐리게) */
export function calendarDates(month) {
  const first = dayjs(`${month}-01`);
  const start = first.subtract(first.day(), 'day'); // 일요일 시작(Figma)
  const end = first.endOf('month');
  const last = end.add(6 - end.day(), 'day');
  const out = [];
  for (let d = start; !d.isAfter(last, 'day'); d = d.add(1, 'day')) out.push(d);
  return out;
}

export const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];
