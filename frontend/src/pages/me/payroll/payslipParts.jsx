// 급여명세서 표 조각 — 내 급여 명세서(직원)와 인사 급여 화면(명세서 창)이 같이 쓴다. Figma 319-6577 · 460-12084
import { formatMoney } from '../../../utils/format';
import './payroll.css';

/** 지급 줄의 과세 구분 — 과세 · 비과세 · 일부 비과세 */
export function taxLabel(line) {
  if (line.itemKind !== 'EARNING') return null;
  if (line.nonTaxableAmount > 0 && line.taxableAmount === 0) return '비과세';
  if (line.nonTaxableAmount > 0) return '일부 비과세';
  return '과세';
}

/** 명세서 값 칸 — 기본급(줄이 아니라 basePay) → 지급 줄 → 소득세 · 지방소득세 → 공제 줄 → 실수령액 (근태 연동 줄은 산식을 같이, F-PAY-06) */
export function payslipTiles(p) {
  const earnings = p.lines.filter((l) => l.itemKind === 'EARNING');
  const deductions = p.lines.filter((l) => l.itemKind === 'DEDUCTION');
  return [
    { key: 'base', label: '기본급 · 과세', value: formatMoney(p.basePay) },
    ...earnings.map((l) => ({
      key: `e${l.payItemId}`,
      label: `${l.name} · ${taxLabel(l)}`,
      value: formatMoney(l.amount),
      note: l.formulaNote,
    })),
    { key: 'income', label: '소득세', value: formatMoney(p.incomeTax) },
    { key: 'local', label: '지방소득세', value: formatMoney(p.localIncomeTax) },
    ...deductions.map((l) => ({
      key: `d${l.payItemId}`,
      label: l.name,
      value: formatMoney(l.amount),
      note: l.formulaNote,
    })),
    { key: 'net', label: '실수령액', value: formatMoney(p.netPay), blue: true },
  ];
}

/** 표 칸 숫자 — 원 없이 "4,000,000", 0 원은 "-" 대신 그대로 0 */
const won = (n) => (n == null ? '-' : Number(n).toLocaleString('ko-KR'));
const pct = (part, whole) => (whole > 0 ? `${Math.round((part / whole) * 10000) / 100}%` : '-');

/**
 * 지급 · 공제 표(Figma 429:8395) — 지급은 매월 지급(기본급 · 고정 · 근태 연동)과 부정기 지급(직접 입력 · 출장 경비)으로 묶고,
 * 공제는 소득세 → 공제 줄 → 지방소득세. 짧은 쪽은 빈 칸으로 맞춘다
 */
export function PayTable({ p }) {
  const earnings = p.lines.filter((l) => l.itemKind === 'EARNING');
  const irregular = earnings.filter((l) => l.calcMethod === 'MANUAL' || l.calcMethod === 'TRIP_EXPENSE');
  const monthly = [{ key: 'base', name: '기본급', amount: p.basePay }, ...earnings.filter((l) => !irregular.includes(l)).map((l) => ({ key: `e${l.payItemId}`, name: l.name, amount: l.amount }))];
  const irr = irregular.map((l) => ({ key: `e${l.payItemId}`, name: l.name, amount: l.amount }));
  const deductions = [
    { key: 'income', name: '소득세', amount: p.incomeTax },
    ...p.lines.filter((l) => l.itemKind === 'DEDUCTION').map((l) => ({ key: `d${l.payItemId}`, name: l.name, amount: l.amount })),
    { key: 'local', name: '지방소득세', amount: p.localIncomeTax },
  ];
  // 왼쪽 줄 수를 공제 줄 수에 맞춘다(마지막 묶음에 빈 칸)
  const pad = Math.max(0, deductions.length - monthly.length - irr.length);
  const groups = [
    { label: ['매월', '지급'], rows: irr.length ? monthly : [...monthly, ...Array(pad).fill(null)] },
    ...(irr.length ? [{ label: ['격월 또는', '부정기', '지급'], rows: [...irr, ...Array(pad).fill(null)] }] : []),
  ];
  let i = 0;
  return (
    <table className="ps-paytable">
      <colgroup>
        <col style={{ width: '5rem' }} />
        <col />
        <col style={{ width: '25%' }} />
        <col style={{ width: '25%' }} />
        <col style={{ width: '25%' }} />
      </colgroup>
      <thead>
        <tr className="is-top">
          <th colSpan={3}>지 급</th>
          <th colSpan={2}>공 제</th>
        </tr>
        <tr className="is-sub">
          <th colSpan={2}>임금 항목</th>
          <th className="is-num">지급 금액(원)</th>
          <th>공제 항목</th>
          <th className="is-num">공제 금액(원)</th>
        </tr>
      </thead>
      <tbody>
        {groups.map((g) =>
          g.rows.map((r, j) => {
            const d = deductions[i++];
            return (
              <tr key={`${g.label[0]}-${r?.key ?? j}`}>
                {j === 0 && (
                  <td className="ps-group" rowSpan={g.rows.length}>
                    {g.label.map((t) => (
                      <span key={t}>{t}</span>
                    ))}
                  </td>
                )}
                <td>{r?.name ?? ''}</td>
                <td className="is-num">{r ? won(r.amount) : ''}</td>
                <td>{d?.name ?? ''}</td>
                <td className="is-num">{d ? won(d.amount) : ''}</td>
              </tr>
            );
          }),
        )}
        <tr className="is-sum">
          <td colSpan={2}>지급액 계</td>
          <td className="is-num">{won(p.grossPay)}</td>
          <td className="is-red">공제액 계</td>
          <td className="is-num is-red">{won(p.totalDeduction)}</td>
        </tr>
        <tr className="is-net">
          <td colSpan={5}>
            <span>실 수령액 (원)</span>
            <strong>{won(p.netPay)}</strong>
          </td>
        </tr>
      </tbody>
    </table>
  );
}

/** 계산 방법(Figma 429:8503) — 서버 계산식(PayrollCalculator)을 말로. 근태 연동 줄은 서버가 준 산식(formulaNote)을 그대로 */
function calcRows(p) {
  const days = `근무일 ${p.workedDays}/${p.monthDays}일 비례`;
  const earning = (l) => {
    if (l.formulaNote) return l.formulaNote;
    const base = {
      FIXED: `정해진 금액 × ${days}`,
      BASE_RATE: `월 기본급 × 지급률 × ${days}`,
      MANUAL: '인사 담당이 이번 달 금액을 직접 입력',
      TRIP_EXPENSE: '승인된 출장 경비 청구 합계',
      ATTENDANCE: l.quantity != null ? `근태 ${l.quantity}시간 × 시간당 ${won(l.unitPrice)}원` : '근태 기록 시간 × 통상시급 × 배율',
    }[l.calcMethod];
    return l.nonTaxableAmount > 0 ? `${base} · 비과세 ${won(l.nonTaxableAmount)}원` : base;
  };
  const deduction = (l) =>
    l.formulaNote ??
    {
      TAXABLE_RATE: `과세 급여 ${won(p.taxablePay)}원 × 근로자 요율(약 ${pct(l.amount, p.taxablePay)}, 기준 상·하한 적용)`,
      FIXED: '정해진 금액',
      BASE_RATE: '월 기본급 × 공제율',
      MANUAL: '인사 담당이 이번 달 금액을 직접 입력',
    }[l.calcMethod] ??
    '-';
  return [
    { key: 'base', name: '기본급', how: `월 기본급 × ${days}`, amount: p.basePay },
    ...p.lines.filter((l) => l.itemKind === 'EARNING').map((l) => ({ key: `e${l.payItemId}`, name: l.name, how: earning(l), amount: l.amount })),
    {
      key: 'income',
      name: '소득세',
      how: `과세표준(과세 급여 ${won(p.taxablePay)}원 − 부양가족 ${p.dependentsCount}명 공제) × 세율 − 누진공제`,
      amount: p.incomeTax,
      ded: true,
    },
    ...p.lines.filter((l) => l.itemKind === 'DEDUCTION').map((l) => ({ key: `d${l.payItemId}`, name: l.name, how: deduction(l), amount: l.amount, ded: true })),
    { key: 'local', name: '지방소득세', how: `소득세 ${won(p.incomeTax)}원 × 지방소득세율(${pct(p.localIncomeTax, p.incomeTax)})`, amount: p.localIncomeTax, ded: true },
  ];
}

export function CalcTable({ p }) {
  return (
    <div className="ps-calc">
      <h3>계산 방법</h3>
      <div className="ps-scroll">
      <table className="ps-calctable">
        <colgroup>
          <col style={{ width: '12.5rem' }} />
          <col />
          <col style={{ width: '10rem' }} />
        </colgroup>
        <thead>
          <tr>
            <th>구분</th>
            <th>산출식 또는 산출방법</th>
            <th className="is-num">금액 (원)</th>
          </tr>
        </thead>
        <tbody>
          {calcRows(p).map((r) => (
            <tr key={r.key} className={r.ded ? 'is-ded' : undefined}>
              <td>{r.name}</td>
              <td className="is-how">{r.how}</td>
              <td className="is-num">{won(r.amount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </div>
  );
}
