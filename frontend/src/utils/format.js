// 표시 규칙(프론트 가이드 2.4). 서버 값을 바꾸지 않고 모양만 바꾼다.
import dayjs from 'dayjs';
import 'dayjs/locale/ko';

dayjs.locale('ko');

const EMPTY = '-';

/** '2026-10-05' → '2026.10.05 (월)', withWeekday=false 면 요일 생략 */
export function formatDate(value, withWeekday = true) {
  if (!value) return EMPTY;
  const d = dayjs(value);
  return d.format(withWeekday ? 'YYYY.MM.DD (dd)' : 'YYYY.MM.DD');
}

/** '2026-10' → '2026년 10월' */
export function formatMonth(value) {
  if (!value) return EMPTY;
  return dayjs(`${value}-01`).format('YYYY년 M월');
}

/**
 * 오프셋 포함 ISO 시각 → 같은 날이면 'HH:mm', 다른 날이면 'MM.DD HH:mm'.
 * 시간대 변환 없이 문자열의 오프셋 그대로 읽는다(가이드 2.4) — 그래서 dayjs 파싱 대신 잘라 쓴다.
 * baseDate('YYYY-MM-DD')를 주면 그날 기준으로 비교한다(근태 달력의 자정 넘긴 퇴근).
 */
export function formatTime(iso, baseDate = dayjs().format('YYYY-MM-DD')) {
  if (!iso) return EMPTY;
  const date = iso.slice(0, 10);
  const hm = iso.slice(11, 16);
  if (date === baseDate) return hm;
  return `${date.slice(5, 7)}.${date.slice(8, 10)} ${hm}`;
}

/** 오프셋 포함 ISO 시각 → 'YYYY.MM.DD HH:mm' */
export function formatDateTime(iso) {
  if (!iso) return EMPTY;
  return `${iso.slice(0, 10).replaceAll('-', '.')} ${iso.slice(11, 16)}`;
}

/** 90 → '1시간 30분', 0 → '0분' */
export function formatMinutes(minutes) {
  if (minutes === null || minutes === undefined) return EMPTY;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}분`;
  return m === 0 ? `${h}시간` : `${h}시간 ${m}분`;
}

/** 3444976 → '3,444,976원' */
export function formatMoney(amount) {
  if (amount === null || amount === undefined) return EMPTY;
  return `${Number(amount).toLocaleString('ko-KR')}원`;
}

/** 4.5 → '4.5%' */
export function formatRate(rate) {
  if (rate === null || rate === undefined) return EMPTY;
  return `${rate}%`;
}

/** 1.5 → '1.5일' */
export function formatDays(days) {
  if (days === null || days === undefined) return EMPTY;
  return `${days}일`;
}
