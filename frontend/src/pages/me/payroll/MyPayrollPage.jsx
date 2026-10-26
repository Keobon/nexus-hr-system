import { useEffect, useState } from 'react';
import { Result, Spin } from 'antd';
import { useQueries, useQuery } from '@tanstack/react-query';
import { getMyPayslip, getMyPayslips, getMySalaries, myPayslipKey, myPayslipsKey, mySalariesKey } from '../../../api/payroll';
import { downloadFile, openFile } from '../../../api/files';
import { useMe } from '../../../auth/useMe';
import { getMyDocuments, myDocumentsKey } from '../../../api/profile';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { LABELS } from '../../../constants/labels';
import { formatMoney } from '../../../utils/format';
import iconChevron from '../../../assets/figma/icon-chevron.svg';
import { CalcTable, PayTable, payslipTiles } from './payslipParts';
import './payroll.css';

const dot = (d) => (d ? d.replaceAll('-', '.') : '-');
/** '2026-09' → '2026년 9월' */
const ym = (m) => (m ? `${m.slice(0, 4)}년 ${Number(m.slice(5, 7))}월` : '-');

const MENU = [
  ['monthly', '월별 급여 명세서'],
  ['bonus', '직접 입력 지급'],
  ['contract', '연봉 계약'],
  ['history', '급여 변경 이력'],
];

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
    <section className="ps-card is-grow ps-print-hide" id="ps-bonus">
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

/* ───── 연봉 계약 ───── */
function ContractCard({ salaries, onHistory }) {
  const notify = useNotify();
  const docs = useQuery({ queryKey: myDocumentsKey, queryFn: getMyDocuments });
  const labor = (docs.data ?? []).find((g) => g.docType === 'LABOR_CONTRACT');
  const doc = labor?.current;
  const fileAction = (fn) => () => fn(doc.fileId).catch((err) => notify.error(err));
  const cur = salaries?.current;
  const past = Math.max(0, (salaries?.salaries?.length ?? 0) - 1);
  return (
    <section className="ps-card ps-print-hide" id="ps-contract">
      <div>
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
              급여는 인사 담당이 등록한 기준(연봉 · 월 기본급)이고, 근로계약서는 인사 담당이 올린 최신본입니다.
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
      </div>

      <ContractCard salaries={salaries.data} onHistory={() => go('history')} />
      <HistoryCard salaries={salaries.data} />
    </div>
  );
}
