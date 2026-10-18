// 급여명세서 — Figma 460-11591(목록) · 460-12321(정산 전 달) · 460-12084(명세서 창). 정산된 달의 명세서를 조직 · 이름으로 찾는다
import { useState } from 'react';
import { Input, Select, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { fetchAllPages } from '../../../api/employees';
import { getPayrollRuns, getPayslips, payrollRunsKey, payslipsKey } from '../../../api/payrollAdmin';
import { errorMessage } from '../../../constants/errors';
import { useOrgOptions } from '../parts';
import PayslipModal from './PayslipModal';
import { RunBadge, monthOptions, won, ym } from './shared';

export default function PayslipsTab({ month, setMonth, onRun }) {
  const org = useOrgOptions();
  const [orgUnitId, setOrgUnitId] = useState();
  const [term, setTerm] = useState('');
  const [slip, setSlip] = useState(null);
  const runs = useQuery({ queryKey: payrollRunsKey, queryFn: getPayrollRuns });
  const run = runs.data?.find((r) => r.payMonth === month);
  const params = { payMonth: month, orgUnitId };
  const slips = useQuery({ queryKey: payslipsKey({ ...params, all: true }), queryFn: () => fetchAllPages(getPayslips, params), enabled: !!run });

  const t = term.trim().toLowerCase();
  const rows = (slips.data ?? []).filter((r) => !t || r.name.toLowerCase().includes(t) || r.employeeNo.toLowerCase().includes(t));
  const sum = (k) => rows.reduce((a, r) => a + (r[k] ?? 0), 0);

  return (
    <>
      <div className="pr-bar">
        <div className="hr-field">
          <label>귀속월</label>
          <Select className="pr-select" value={month} onChange={setMonth} options={monthOptions(runs.data)} />
        </div>
        <div className="hr-field">
          <label>조직</label>
          <Select className="pr-select" allowClear placeholder="전체 조직" value={orgUnitId} onChange={setOrgUnitId} options={org.options} popupMatchSelectWidth={false} />
        </div>
        <div className="hr-field pr-grow">
          <label>이름 · 사원번호 검색</label>
          <Input allowClear placeholder="검색" value={term} onChange={(e) => setTerm(e.target.value)} />
        </div>
        {run && <RunBadge status={run.status} />}
      </div>

      {runs.isLoading && <Spin />}
      {!runs.isLoading && !run && (
        <div className="pr-empty">
          <strong>{`${ym(month)}은 아직 정산되지 않았습니다.`}</strong>
          <span>명세서가 생성되면 목록에 자동으로 반영됩니다.</span>
          <button type="button" className="hr-btn" onClick={onRun}>
            월 급여 정산으로
          </button>
        </div>
      )}
      {run && (
        <section className="hr-table pr-card">
          <div className="pr-card-head">
            <strong>{`${ym(month)} 급여명세서 · ${rows.length}명`}</strong>
            <span className="pr-muted">{`지급일 ${run.payDate} · 줄을 누르면 명세서가 열립니다`}</span>
          </div>
          <div className="hr-tr is-head">
            <span className="pr-c-no">사원번호</span>
            <span className="pr-c-name">이름</span>
            <span className="pr-c-org">소속(정산 당시)</span>
            <span className="pr-c-num">지급 합계</span>
            <span className="pr-c-num">공제 합계</span>
            <span className="pr-c-num">실지급액</span>
            <span className="pr-c-st">상태</span>
          </div>
          {slips.isLoading && (
            <div className="hr-tr is-empty">
              <Spin />
            </div>
          )}
          {slips.error && <div className="hr-tr is-empty">{errorMessage(slips.error)}</div>}
          {!slips.isLoading && rows.length === 0 && <div className="hr-tr is-empty">조건에 맞는 명세서가 없습니다</div>}
          {rows.map((r) => (
            <div key={r.id} className="hr-tr pr-row is-click" onClick={() => setSlip(r)}>
              <span className="pr-c-no">{r.employeeNo}</span>
              <span className="pr-c-name hr-strong">{r.name}</span>
              <span className="pr-c-org">{r.orgUnitName}</span>
              <span className="pr-c-num">{won(r.grossPay)}</span>
              <span className="pr-c-num">{won(r.totalDeduction)}</span>
              <span className="pr-c-num pr-blue">{won(r.netPay)}</span>
              <span className="pr-c-st">
                <RunBadge status={run.status} />
              </span>
            </div>
          ))}
          {rows.length > 0 && (
            <div className="hr-tr pr-row is-sum">
              <span className="pr-c-no">합계</span>
              <span className="pr-c-name">{`${rows.length}명`}</span>
              <span className="pr-c-org" />
              <span className="pr-c-num">{won(sum('grossPay'))}</span>
              <span className="pr-c-num">{won(sum('totalDeduction'))}</span>
              <span className="pr-c-num pr-blue">{won(sum('netPay'))}</span>
              <span className="pr-c-st" />
            </div>
          )}
        </section>
      )}
      <p className="pr-note">확정된 명세서는 고칠 수 없습니다. 잘못된 금액은 다음 달 정산의 "소급 조정"(매월 직접 입력)으로 정정하세요.</p>
      <PayslipModal id={slip?.id} name={slip?.name} onClose={() => setSlip(null)} />
    </>
  );
}
