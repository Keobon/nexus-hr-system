// 급여 화면 탭들이 같이 쓰는 것 — 월 선택지 · 숫자 · 상태 배지 · 급여 대장(CSV) 내려받기
import dayjs from 'dayjs';
import { formatMoney } from '../../../utils/format';

/** '2026-09' → '2026년 9월' */
export const ym = (m) => (m ? `${m.slice(0, 4)}년 ${Number(m.slice(5, 7))}월` : '-');
export const won = (n) => (n == null ? '-' : formatMoney(n));
export const dot = (d) => (d ? d.slice(0, 10) : '-');

/** 귀속 월 선택지 — 이번 달부터 거꾸로 12개월 + 정산된 달(더 옛날 것) */
export function monthOptions(runs = []) {
  const set = new Set();
  for (let i = 0; i < 12; i += 1) set.add(dayjs().subtract(i, 'month').format('YYYY-MM'));
  runs.forEach((r) => set.add(r.payMonth));
  return [...set].sort().reverse().map((m) => ({ value: m, label: ym(m) }));
}

const STATUS = { NONE: ['미정산', 'is-gray'], CONFIRMED: ['확정', 'is-info'], PAID: ['지급완료', 'is-ok'] };
export function RunBadge({ status }) {
  const [label, tone] = STATUS[status ?? 'NONE'] ?? STATUS.NONE;
  return <span className={`hr-badge ${tone}`}>{label}</span>;
}

/** 급여 대장 — 엑셀에서 열리는 CSV(UTF-8 BOM). 서버에 엑셀 API 가 없어 화면에 있는 값으로 만든다 */
export function downloadLedger(payMonth, rows) {
  const head = ['사원번호', '이름', '소속', '지급 합계', '공제 합계', '실지급액', '회사 부담'];
  const esc = (v) => `"${String(v ?? '').replaceAll('"', '""')}"`;
  const body = rows.map((r) => [r.employeeNo, r.name, r.orgUnitName, r.grossPay, r.totalDeduction, r.netPay, r.companyBurdenTotal ?? ''].map(esc).join(','));
  const blob = new Blob([`﻿${[head.map(esc).join(','), ...body].join('\r\n')}`], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `급여대장_${payMonth}.csv`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

/** 정산 단계 — ① 수동 입력 → ② 미리보기 → ③ 확정 → ④ 지급완료 */
export function Steps({ step }) {
  const names = ['수동 입력', '미리보기', '확정', '지급완료'];
  return (
    <ol className="pr-steps">
      {names.map((n, i) => (
        <li key={n} className={i + 1 === step ? 'is-on' : i + 1 < step ? 'is-done' : undefined}>
          <span>{i + 1}</span>
          {n}
        </li>
      ))}
    </ol>
  );
}
