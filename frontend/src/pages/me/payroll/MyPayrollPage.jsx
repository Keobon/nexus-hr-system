import { useEffect, useState } from 'react';
import { Result, Spin } from 'antd';
import { useQueries, useQuery } from '@tanstack/react-query';
import { getMyPayslip, getMyPayslips, getMySalaries, myPayslipKey, myPayslipsKey, mySalariesKey } from '../../../api/payroll';
import { downloadFile, openFile } from '../../../api/files';
import { useMe } from '../../../auth/useMe';
import { getMyDocuments, myDocumentsKey } from '../../../api/profile';
import { useNotify } from '../../../components/common/useNotify';
import { usePendingFeature } from '../../../components/common/usePendingFeature';
import { errorMessage } from '../../../constants/errors';
import { LABELS } from '../../../constants/labels';
import { formatMoney } from '../../../utils/format';
import iconChevron from '../../../assets/figma/icon-chevron.svg';
import './payroll.css';

const dot = (d) => (d ? d.replaceAll('-', '.') : '-');
/** '2026-09' → '2026년 9월' */
const ym = (m) => (m ? `${m.slice(0, 4)}년 ${Number(m.slice(5, 7))}월` : '-');

const MENU = [
  ['monthly', '월별 급여 명세서'],
  ['bonus', '직접 입력 지급'],
  ['tax', '원천징수·연말정산'],
  ['contract', '연봉 계약·퇴직금'],
  ['history', '급여 변경 이력'],
];

/** 지급 줄의 과세 구분 — 과세 · 비과세 · 일부 비과세 */
function taxLabel(line) {
  if (line.itemKind !== 'EARNING') return null;
  if (line.nonTaxableAmount > 0 && line.taxableAmount === 0) return '비과세';
  if (line.nonTaxableAmount > 0) return '일부 비과세';
  return '과세';
}

/** 명세서 값 칸 — 기본급(줄이 아니라 basePay) → 지급 줄 → 소득세 · 지방소득세 → 공제 줄 → 실수령액 (근태 연동 줄은 산식을 같이, F-PAY-06) */
function payslipTiles(p) {
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
function PayTable({ p }) {
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

function CalcTable({ p }) {
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

/* ───── 월별 급여 명세서 ───── */
function MonthlySection({ rows, selectedId, onSelect }) {
  const { me } = useMe();
  const q = useQuery({
    queryKey: myPayslipKey(selectedId),
    queryFn: () => getMyPayslip(selectedId),
    enabled: !!selectedId,
  });
  const p = q.data;
  const row = rows.find((r) => r.id === selectedId);

  return (
    <>
      <section className="ps-card" id="ps-monthly">
        <dl className="ps-who">
          {[
            ['성명', p?.name ?? me?.employee?.name],
            ['사번', p?.employeeNo ?? me?.employee?.employeeNo],
            ['부서', p?.orgUnitName ?? me?.employee?.orgUnitName],
            ['직급', me?.employee?.jobGradeName],
          ].map(([k, v]) => (
            <div key={k}>
              <dt>{k}</dt>
              <dd>{v || '-'}</dd>
            </div>
          ))}
        </dl>
        <div className="ps-card-head">
          <span className="ps-label">귀속연월</span>
          <div className="ps-actions">
            <label className="ps-select">
              <select
                aria-label="귀속연월"
                value={selectedId ?? ''}
                disabled={!rows.length}
                onChange={(e) => onSelect(Number(e.target.value))}
              >
                {!rows.length && <option value="">명세서 없음</option>}
                {rows.map((r) => (
                  <option key={r.id} value={r.id}>
                    {ym(r.payMonth)}
                  </option>
                ))}
              </select>
              <img src={iconChevron} alt="" />
            </label>
            {/* 브라우저 인쇄 창 — "PDF로 저장"을 고르면 PDF 로 받는다 */}
            <button type="button" className="ps-btn" disabled={!p} onClick={() => window.print()}>
              PDF 다운로드
            </button>
            <button type="button" className="ps-btn is-gray" disabled={!p} onClick={() => window.print()}>
              출력
            </button>
          </div>
        </div>
        <div className="ps-tiles">
          <div className="ps-tile is-big">
            <span>총 지급액</span>
            <strong>{p ? formatMoney(p.grossPay) : '-'}</strong>
          </div>
          <div className="ps-tile is-big">
            <span>총 공제액</span>
            <strong>{p ? formatMoney(p.totalDeduction) : '-'}</strong>
          </div>
          <div className="ps-tile is-big">
            <span>실수령액</span>
            <strong className="is-blue">{p ? formatMoney(p.netPay) : row ? formatMoney(row.netPay) : '-'}</strong>
          </div>
          <div className="ps-tile is-big">
            <span>지급일</span>
            <strong>{row ? dot(row.payDate) : '-'}</strong>
          </div>
        </div>
      </section>

      <section className="ps-card">
        <div className="ps-card-head">
          <h2>{row ? `${ym(row.payMonth)} 급여 명세서` : '급여 명세서'}</h2>
          {p && (
            <span className="ps-meta">
              {[
                LABELS.payrollStatus[p.status]?.label,
                `근무 ${p.workedDays}/${p.monthDays}일`,
                `통상시급 ${formatMoney(p.ordinaryHourlyWage)}`,
                p.bankAccount,
                `부양가족 ${p.dependentsCount}명(자녀 ${p.childrenCount})`,
              ]
                .filter(Boolean)
                .join(' · ')}
            </span>
          )}
        </div>
        {!rows.length && <p className="ps-empty">아직 정산된 급여 명세서가 없습니다. 급여가 확정되면 여기에 표시됩니다.</p>}
        {rows.length > 0 && !p && (q.isLoading ? <Spin /> : <p className="ps-empty">{errorMessage(q.error)}</p>)}
        {p && (
          <div className="ps-scroll">
            <PayTable p={p} />
          </div>
        )}
        {p && (
          <div className="ps-tiles">
            {payslipTiles(p).map((t) => (
              <div key={t.key} className="ps-tile">
                <span title={t.label}>{t.label}</span>
                <strong className={t.blue ? 'is-blue' : undefined}>{t.value}</strong>
                {t.note && <em>{t.note}</em>}
              </div>
            ))}
          </div>
        )}
        {p && <CalcTable p={p} />}
        {p?.expenseClaimIds?.length > 0 && (
          <p className="ps-meta" style={{ margin: 0 }}>{`출장 경비 ${p.expenseClaimIds.length}건이 이 달 급여에 반영되었습니다.`}</p>
        )}
      </section>
    </>
  );
}

/* ───── 직접 입력 지급 — 최근 3개월 명세서의 수동 입력 지급 줄. 성과급만이 아니라 소급 조정 등도 있어 항목 이름을 그대로(노션 수정사항 105) ───── */
function BonusCard({ rows }) {
  const recent = rows.slice(0, 3);
  const details = useQueries({
    queries: recent.map((r) => ({
      queryKey: myPayslipKey(r.id),
      queryFn: () => getMyPayslip(r.id),
    })),
  });
  const loading = details.some((d) => d.isLoading);
  const sums = {};
  details.forEach((d) =>
    (d.data?.lines ?? [])
      .filter((l) => l.itemKind === 'EARNING' && l.calcMethod === 'MANUAL' && l.amount > 0)
      .forEach((l) => {
        sums[l.name] = (sums[l.name] ?? 0) + l.amount;
      }),
  );
  const items = Object.entries(sums);
  const total = items.reduce((a, [, v]) => a + v, 0);

  return (
    <section className="ps-card is-side ps-print-hide" id="ps-bonus">
      <h2>직접 입력 지급 명세</h2>
      <div className="ps-rows">
        {loading && <Spin />}
        {!loading && items.length === 0 && <p className="ps-empty">최근 3개월 동안 직접 입력된 지급 항목이 없습니다.</p>}
        {items.map(([name, amount]) => (
          <div key={name} className="ps-row">
            <span>{name}</span>
            <span>{formatMoney(amount)}</span>
          </div>
        ))}
      </div>
      <div className="ps-total">
        <span>직접 입력 지급 합계</span>
        <strong>{formatMoney(total)}</strong>
        <em>최근 3개월 명세서에서 달마다 직접 입력한 지급 항목(성과급 · 소급 조정 등) 합계</em>
      </div>
    </section>
  );
}

/* ───── 원천징수영수증 · 연말정산 — 기능명세서 v2 범위 밖 ───── */
function TaxCard() {
  return (
    <section className="ps-card is-grow ps-print-hide" id="ps-tax">
      <h2>원천징수영수증 및 연말정산</h2>
      <div className="ps-rows">
        {['근로소득 원천징수영수증 발급', '연말정산 예상 결과 · 환급 예정일'].map((t) => (
          <div key={t} className="ps-row">
            <span>{t}</span>
            <span className="is-muted">준비 중</span>
          </div>
        ))}
      </div>
      <div className="ps-note">
        <strong>연말정산 안내</strong>
        <p>
          원천징수영수증 발급과 연말정산은 이 시스템에서 아직 제공하지 않습니다. 필요하면 인사 · 급여 담당자에게 문의해 주세요. 월별
          명세서의 소득세 · 지방소득세는 위 명세서에서 확인할 수 있습니다.
        </p>
      </div>
    </section>
  );
}

/* ───── 연봉 계약 · 퇴직금 ───── */
function ContractCard({ salaries, onHistory }) {
  const pending = usePendingFeature();
  const notify = useNotify();
  const docs = useQuery({ queryKey: myDocumentsKey, queryFn: getMyDocuments });
  const labor = (docs.data ?? []).find((g) => g.docType === 'LABOR_CONTRACT');
  const doc = labor?.current;
  const fileAction = (fn) => () => fn(doc.fileId).catch((err) => notify.error(err));
  const cur = salaries?.current;
  const past = Math.max(0, (salaries?.salaries?.length ?? 0) - 1);
  return (
    <section className="ps-card ps-print-hide" id="ps-contract">
      <div className="ps-two">
        <div>
          <h2>연봉 계약</h2>
          <div className="ps-rows">
            <div className="ps-row">
              <span>현재 급여</span>
              <span>
                {cur
                  ? `${LABELS.salaryType[cur.salaryType]?.label ?? ''} · ${cur.annualSalary != null ? `연 ${formatMoney(cur.annualSalary)}` : `월 ${formatMoney(cur.monthlyBase)}`}`
                  : '등록 전'}
              </span>
            </div>
            <div className="ps-row">
              <span>적용 시작일</span>
              <span>{cur ? dot(cur.effectiveFrom) : '-'}</span>
            </div>
            <div className="ps-row">
              <span>월 기본급</span>
              <span>{cur ? formatMoney(cur.monthlyBase) : '-'}</span>
            </div>
            <div className="ps-row">
              <span>과거 급여 내역</span>
              <span className="is-blue">{`${past}건`}</span>
            </div>
            <div className="ps-row">
              <span>근로계약서</span>
              <span className={doc ? undefined : 'is-muted'}>
                {docs.isLoading ? '…' : doc ? `${doc.issuedDate ? `${dot(doc.issuedDate)} · ` : ''}v${doc.versionNo}${labor.versions?.length > 1 ? ` (이전 ${labor.versions.length - 1}건)` : ''}` : '등록된 계약서 없음'}
              </span>
            </div>
          </div>
          <div className="ps-note">
            <strong>계약 조회 안내</strong>
            <p>
              급여는 인사 담당이 등록한 기준(연봉 · 월 기본급)이고, 근로계약서는 인사 담당이 올린 최신본입니다. 전자서명은 아직 제공하지
              않습니다.
            </p>
          </div>
          <div className="ps-links">
            <button type="button" onClick={onHistory}>{`과거 급여 ${past}건`}</button>
            {doc && (
              <>
                <button type="button" onClick={fileAction(openFile)}>
                  계약서 보기
                </button>
                <button type="button" onClick={fileAction(downloadFile)}>
                  내려받기
                </button>
              </>
            )}
            <button type="button" onClick={() => pending('전자서명', '연봉 계약서 · 전자서명')}>
              전자서명
            </button>
          </div>
        </div>
        <div>
          <h2>예상 퇴직금</h2>
          <div className="ps-rows">
            {['현재 기준 예상 퇴직금', '퇴직연금 가입 유형'].map((t) => (
              <div key={t} className="ps-row">
                <span>{t}</span>
                <span className="is-muted">준비 중</span>
              </div>
            ))}
          </div>
          <div className="ps-note">
            <strong>예상 퇴직금 안내</strong>
            <p>
              퇴직금 추정 · 퇴직연금 정보는 아직 제공하지 않습니다(기능명세서 v2 범위 밖). 필요하면 인사 · 급여 담당자에게 문의해 주세요.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}

/* ───── 급여 변경 이력 ───── */
function HistoryCard({ salaries }) {
  const rows = salaries?.salaries ?? [];
  const items = (salaries?.payItems ?? []).filter((i) => i.history?.length);
  return (
    <section className="ps-card ps-print-hide" id="ps-history">
      <h2>급여 변경 이력</h2>
      {rows.length === 0 ? (
        <p className="ps-empty">등록된 급여 이력이 없습니다.</p>
      ) : (
        <div className="ps-table-wrap">
          <table className="ps-table">
            <thead>
              <tr>
                <th>적용 시작일</th>
                <th>방식</th>
                <th>연봉</th>
                <th>월 기본급</th>
                <th>사유</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td className="is-num">{dot(r.effectiveFrom)}</td>
                  <td>{LABELS.salaryType[r.salaryType]?.label}</td>
                  <td className="is-num">{r.annualSalary != null ? formatMoney(r.annualSalary) : '-'}</td>
                  <td className="is-num">{formatMoney(r.monthlyBase)}</td>
                  <td>{r.reason || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {items.length > 0 && (
        <div className="ps-table-wrap">
          <table className="ps-table">
            <thead>
              <tr>
                <th>수당 항목</th>
                <th>적용 시작일</th>
                <th>금액</th>
                <th>사유</th>
              </tr>
            </thead>
            <tbody>
              {items.flatMap((i) =>
                i.history.map((h) => (
                  <tr key={h.id}>
                    <td>{i.name}</td>
                    <td className="is-num">{dot(h.effectiveFrom)}</td>
                    <td className="is-num">{formatMoney(h.amount)}</td>
                    <td>{h.reason || '-'}</td>
                  </tr>
                )),
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

/** 내 급여 명세서(F-PAY-06 본인 · F-PAY-04 본인 급여) — Figma COMMON_my_payslip */
export default function MyPayrollPage() {
  const [selectedId, setSelectedId] = useState(null);
  const [active, setActive] = useState('monthly');
  const payslips = useQuery({
    queryKey: myPayslipsKey,
    queryFn: getMyPayslips,
  });
  const salaries = useQuery({
    queryKey: mySalariesKey,
    queryFn: getMySalaries,
  });
  const rows = payslips.data ?? [];

  // 처음에는 가장 최근 귀속 월
  useEffect(() => {
    if (selectedId == null && rows.length) setSelectedId(rows[0].id);
  }, [rows, selectedId]);

  if (payslips.isLoading || salaries.isLoading) {
    return (
      <div className="page-center" style={{ minHeight: '25rem' }}>
        <Spin size="large" />
      </div>
    );
  }
  const error = payslips.error ?? salaries.error;
  if (error) return <Result status="warning" title="급여 정보를 불러오지 못했습니다" subTitle={errorMessage(error)} />;

  const go = (key) => {
    setActive(key);
    document.getElementById(`ps-${key}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  return (
    <div className="ps">
      <header className="ps-head">
        <h1>내 급여 명세서</h1>
        <p>월별 급여 명세서와 급여 변경 이력을 확인하세요. 금액은 정산 당시 저장된 값입니다.</p>
      </header>

      <nav className="ps-menu" aria-label="급여 메뉴">
        {MENU.map(([key, label]) => (
          <button key={key} type="button" className={active === key ? 'is-active' : undefined} onClick={() => go(key)}>
            {label}
          </button>
        ))}
      </nav>

      <MonthlySection rows={rows} selectedId={selectedId} onSelect={setSelectedId} />

      <div className="ps-bottom">
        <BonusCard rows={rows} />
        <TaxCard />
      </div>

      <ContractCard salaries={salaries.data} onHistory={() => go('history')} />
      <HistoryCard salaries={salaries.data} />
    </div>
  );
}
