// 평가 관리 탭들이 같이 쓰는 것 — 기간 이름 · 상태 배지 · 척도 수준
import dayjs from 'dayjs';

/** 평가 기간 이름에서 연도 · 반기 — "2026 상반기 평가" → { year: '2026', half: '상반기' } (이름에 없으면 시작일로) */
export function cycleParts(c) {
  const year = c.name.match(/(\d{4})/)?.[1] ?? c.startDate.slice(0, 4);
  const half = c.name.includes('하반기') ? '하반기' : c.name.includes('상반기') ? '상반기' : null;
  return { year, half };
}
export const HALF_MONTHS = { 상반기: '01–06월', 하반기: '07–12월' };
export const yearOptions = (cycles) => {
  const set = new Set([dayjs().format('YYYY'), ...cycles.map((c) => cycleParts(c).year)]);
  return [...set].sort().reverse().map((y) => ({ value: y, label: `${y}년` }));
};
export const dots = (a, b) => `${a.replaceAll('-', '.')}–${b.slice(5).replaceAll('-', '.')}`;

const CYCLE = { SCHEDULED: ['예정', 'is-gray'], IN_PROGRESS: ['진행 중', 'is-ok'], CLOSED: ['마감', 'is-info'] };
export function CycleBadge({ status }) {
  const [label, tone] = CYCLE[status] ?? CYCLE.SCHEDULED;
  return <span className={`hr-badge ${tone}`}>{label}</span>;
}

/** 결과 상태 — 확정 · 확정 대기(제출) · 미제출(작성 전 · 작성 중 · 재오픈) */
export function resultState(status) {
  if (status === 'CONFIRMED') return ['확정', 'is-info'];
  if (status === 'SUBMITTED') return ['확정 대기', 'is-warn'];
  return ['미제출', 'is-gray'];
}
export function EvalStatusChip({ status }) {
  const text = { NOT_STARTED: '작성 전', IN_PROGRESS: '작성 중', SUBMITTED: '제출', CONFIRMED: '제출', REOPENED: '재오픈' }[status] ?? status;
  const done = status === 'SUBMITTED' || status === 'CONFIRMED';
  return <span className={`ea-chip${done ? ' is-done' : ''}`}>{text}</span>;
}

/** 척도 수준 — 총점 ÷ 20 = 배점 가중 항목 평균(1–5). 등급(S · A · B)은 서버에 없어 수준 이름으로(직원 화면과 같은 규칙) */
const SCALE = [
  [4.5, '매우 우수'],
  [3.5, '우수'],
  [2.5, '보통'],
  [1.5, '미흡'],
  [0, '매우 미흡'],
];
export const avgOf = (total) => (total == null ? null : Math.round((Number(total) / 20) * 10) / 10);
export const levelOf = (avg) => (avg == null ? '–' : SCALE.find(([min]) => avg >= min)[1]);
export const num = (n) => (n == null ? '–' : Number(n).toLocaleString('ko-KR', { maximumFractionDigits: 1 }));
