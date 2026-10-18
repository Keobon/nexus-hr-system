// 월 급여 정산 — Figma 456-15895(① 수동 입력) · 456-16092(② 미리보기) · 456-16382(확정 창) · 456-16681(③ 확정) · 456-17050(조회 전용)
// 정산 전: 수동 입력 → 미리보기(POST preview, 저장 안 함) → 확정(같은 입력으로 POST, 귀속 월이 끝난 뒤에만)
// 정산 후: 확정 → 지급완료. 확정된 급여는 고칠 수 없고 정정은 다음 달 소급 조정으로(서버 규칙)
import { useState } from 'react';
import { App, DatePicker, InputNumber, Select, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchAllPages } from '../../../api/employees';
import { getPayItems, payItemsKey } from '../../../api/paySettings';
import {
  confirmPayroll,
  getPayrollRun,
  getPayrollRuns,
  getPayslip,
  getSalaries,
  markPayrollPaid,
  payrollRunKey,
  payrollRunsKey,
  payslipKey,
  previewPayroll,
  salariesKey,
} from '../../../api/payrollAdmin';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage, warningMessage } from '../../../constants/errors';
import PayslipModal from './PayslipModal';
import { RunBadge, Steps, downloadLedger, monthOptions, won, ym } from './shared';

const hours = (min) => `${Math.round((min / 60) * 10) / 10}시간`;
const money = { formatter: (v) => (v || v === 0 ? `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''), parser: (v) => v.replace(/,/g, '') };

export default function RunTab({ month, setMonth, manage, onUnregistered }) {
  const runs = useQuery({ queryKey: payrollRunsKey, queryFn: getPayrollRuns });
  if (runs.isLoading) return <Spin />;
  if (runs.error) return <p className="hr-notice is-error">{errorMessage(runs.error)}</p>;
  const run = runs.data.find((r) => r.payMonth === month);
  const options = monthOptions(runs.data);
  if (run) return <SettledRun key={run.id} run={run} options={options} setMonth={setMonth} manage={manage} />;
  if (!manage) return <UnsettledReadonly month={month} options={options} setMonth={setMonth} />;
  return <DraftRun key={month} month={month} options={options} setMonth={setMonth} onUnregistered={onUnregistered} />;
}

/* ───── 위 카드 — 귀속 월 · 지급일 · 상태 · 급여 대장 ───── */
function RunBar({ month, options, setMonth, payDate, onPayDate, status, ledger }) {
  return (
    <div className="pr-bar">
      <div className="hr-field">
        <label>귀속 월</label>
        <Select className="pr-select" value={month} onChange={setMonth} options={options} />
      </div>
      <div className="hr-field">
        <label>지급일</label>
        {onPayDate ? (
          <DatePicker className="pr-select" value={payDate} onChange={onPayDate} format="YYYY-MM-DD" placeholder="기본 지급일" />
        ) : (
          <span className="pr-readonly">{payDate ?? '-'}</span>
        )}
      </div>
      <RunBadge status={status} />
      <span className="pr-spacer" />
      <button type="button" className="hr-btn is-secondary" disabled={!ledger?.length} onClick={() => downloadLedger(month, ledger)}>
        급여 대장 엑셀
      </button>
    </div>
  );
}

function Unregistered({ list, onClick }) {
  if (!list?.length) return null;
  return (
    <div className="pr-alert">
      <div>
        <strong>{`급여 미등록 직원 ${list.length}명 — 정산에서 제외됩니다`}</strong>
        <span>{list.map((u) => u.name).join(' · ')}</span>
      </div>
      {onClick && (
        <button type="button" className="hr-link" onClick={onClick}>
          직원 급여에서 등록
        </button>
      )}
    </div>
  );
}

/* ───── 합계 카드 5개 ───── */
function Totals({ t }) {
  return (
    <div className="pr-totals">
      <div>
        <span>대상 인원</span>
        <strong>{`${t.headcount}명`}</strong>
      </div>
      <div>
        <span>지급 총액</span>
        <strong>{won(t.grossPay)}</strong>
      </div>
      <div>
        <span>공제 총액</span>
        <strong>{won(t.totalDeduction)}</strong>
      </div>
      <div className="is-main">
        <span>실지급 총액</span>
        <strong>{won(t.netPay)}</strong>
      </div>
      <div>
        <span>회사 부담 4대보험</span>
        <strong>{won(t.companyBurdenTotal)}</strong>
      </div>
    </div>
  );
}

/** 펼친 줄 — 지급 | 공제 두 칸 + 근태 · 출장 경비 한 줄 */
function Detail({ p, attendance }) {
  const earnings = [['기본급', p.basePay], ...p.lines.filter((l) => l.itemKind === 'EARNING').map((l) => [l.name, l.amount])];
  const deductions = [...p.lines.filter((l) => l.itemKind === 'DEDUCTION').map((l) => [l.name, l.amount]), ['소득세', p.incomeTax], ['지방소득세', p.localIncomeTax]];
  const trip = p.lines.filter((l) => l.calcMethod === 'TRIP_EXPENSE').reduce((a, l) => a + l.amount, 0);
  return (
    <div className="pr-detail">
      <div className="pr-detail-cols">
        <dl>
          <dt>
            <span>지급</span>
            <strong>{won(p.grossPay)}</strong>
          </dt>
          {earnings.map(([n, v]) => (
            <dd key={n}>
              <span>{n}</span>
              <span>{won(v)}</span>
            </dd>
          ))}
        </dl>
        <dl>
          <dt>
            <span>공제</span>
            <strong>{won(p.totalDeduction)}</strong>
          </dt>
          {deductions.map(([n, v]) => (
            <dd key={n}>
              <span>{n}</span>
              <span>{won(v)}</span>
            </dd>
          ))}
        </dl>
      </div>
      <p>
        {attendance
          ? `근태: 연장 ${hours(attendance.overtimeMinutes)} · 야간 ${hours(attendance.nightMinutes)} · 휴일 ${hours(attendance.holidayMinutes + attendance.holidayOvertimeMinutes)} · 결근 ${attendance.absentDays}일 / 출장 경비 반영 ${won(trip)}`
          : `근무 ${p.workedDays}/${p.monthDays}일 · 통상시급 ${won(p.ordinaryHourlyWage)} / 출장 경비 반영 ${won(trip)}`}
      </p>
    </div>
  );
}

/* ───── 정산 전 — 수동 입력 · 미리보기 · 확정 ───── */
function DraftRun({ month, options, setMonth, onUnregistered }) {
  const notify = useNotify();
  const { modal } = App.useApp();
  const queryClient = useQueryClient();
  const [step, setStep] = useState(1);
  const [payDate, setPayDate] = useState(null);
  const [inputs, setInputs] = useState({}); // `${employeeId}-${payItemId}` → 금액
  const [preview, setPreview] = useState(null);
  const [open, setOpen] = useState(null);

  const salaries = useQuery({ queryKey: salariesKey({ all: true }), queryFn: () => fetchAllPages(getSalaries, {}) });
  const items = useQuery({ queryKey: payItemsKey, queryFn: getPayItems });
  const manual = (items.data ?? []).filter((i) => i.calcMethod === 'MANUAL' && i.isActive);
  const registered = (salaries.data ?? []).filter((s) => s.registered);
  const unregistered = (salaries.data ?? []).filter((s) => !s.registered);

  const body = () => ({
    payMonth: month,
    payDate: payDate ? payDate.format('YYYY-MM-DD') : null,
    manualInputs: Object.entries(inputs)
      .filter(([, v]) => v)
      .map(([k, amount]) => {
        const [employeeId, payItemId] = k.split('-').map(Number);
        return { employeeId, payItemId, amount };
      }),
  });
  const runPreview = useMutation({
    mutationFn: () => previewPayroll(body()),
    onSuccess: (data) => {
      setPreview(data);
      setStep(2);
    },
    onError: (err) => notify.error(err),
  });
  const confirm = () =>
    modal.confirm({
      title: `${ym(month)} 급여를 확정할까요?`,
      content: '확정하면 명세서가 만들어지고 되돌릴 수 없습니다. 정정은 다음 달 소급 조정으로 처리합니다.',
      okText: '확정',
      cancelText: '취소',
      onOk: async () => {
        try {
          await confirmPayroll(body());
          queryClient.invalidateQueries({ queryKey: payrollRunsKey });
          queryClient.invalidateQueries({ queryKey: ['payslips'] });
          notify.success(`${ym(month)} 급여를 확정했습니다`);
        } catch (err) {
          notify.error(err);
        }
      },
    });

  const blocked = preview && (preview.warning === 'PAY_MONTH_NOT_ENDED' || preview.negativeNetPayEmployeeIds?.length > 0);
  const ledger = preview?.employees.map((e) => ({ ...e, companyBurdenTotal: e.companyBurdenTotal })) ?? [];

  return (
    <>
      <RunBar month={month} options={options} setMonth={setMonth} payDate={step === 2 ? preview.payDate : payDate} onPayDate={step === 1 ? setPayDate : undefined} status="NONE" ledger={ledger} />
      <Steps step={step} />
      <Unregistered list={step === 2 ? preview.unregistered : unregistered} onClick={onUnregistered} />

      {step === 1 && (
        <>
          <section className="hr-table pr-card">
            <div className="pr-card-head">
              <div>
                <strong>수동 입력</strong>
                <span>매월 직접 입력 항목만 표시됩니다.</span>
              </div>
              <span className="pr-muted">{`대상 인원 ${registered.length}명`}</span>
            </div>
            <div className="hr-tr is-head">
              <span className="pr-c-no">사원번호</span>
              <span className="pr-c-name">이름</span>
              <span className="pr-c-org">소속</span>
              {manual.map((m) => (
                <span key={m.id} className="pr-c-in">
                  {m.name}
                </span>
              ))}
            </div>
            {(salaries.isLoading || items.isLoading) && (
              <div className="hr-tr is-empty">
                <Spin />
              </div>
            )}
            {!salaries.isLoading && registered.length === 0 && <div className="hr-tr is-empty">급여가 등록된 직원이 없습니다</div>}
            {manual.length === 0 && !items.isLoading && <div className="hr-tr is-empty">매월 직접 입력 항목이 없습니다 — 회사 설정 › 급여 항목·변수에서 추가할 수 있어요</div>}
            {registered.map((s) => (
              <div key={s.employeeId} className="hr-tr pr-row">
                <span className="pr-c-no">{s.employeeNo}</span>
                <span className="pr-c-name hr-strong">{s.name}</span>
                <span className="pr-c-org">{s.orgUnitName}</span>
                {manual.map((m) => (
                  <span key={m.id} className="pr-c-in">
                    <InputNumber
                      className="pr-input"
                      min={0}
                      step={10000}
                      suffix="원"
                      value={inputs[`${s.employeeId}-${m.id}`] ?? 0}
                      onChange={(v) => setInputs((d) => ({ ...d, [`${s.employeeId}-${m.id}`]: v ?? 0 }))}
                      {...money}
                    />
                  </span>
                ))}
              </div>
            ))}
          </section>
          <div className="pr-foot">
            <button type="button" className="hr-btn" disabled={runPreview.isPending || !registered.length} onClick={() => runPreview.mutate()}>
              {runPreview.isPending ? '계산 중…' : '미리보기'}
            </button>
          </div>
        </>
      )}

      {step === 2 && preview && (
        <>
          {preview.warning && <p className="pr-warn">{warningMessage(preview.warning)}</p>}
          {preview.negativeNetPayEmployeeIds?.length > 0 && (
            <p className="hr-notice is-error">{`실지급액이 음수인 직원 ${preview.negativeNetPayEmployeeIds.length}명이 있어 확정할 수 없습니다. 수동 입력을 고쳐 주세요.`}</p>
          )}
          <Totals t={preview.totals} />
          <section className="hr-table pr-card">
            <div className="pr-card-head">
              <strong>{`${ym(month)} 정산 내역`}</strong>
              <span className="pr-muted">{`대상 ${preview.totals.headcount}명 · 직원별 상세 내역 확인`}</span>
            </div>
            <div className="hr-tr is-head">
              <span className="pr-c-no">사원번호</span>
              <span className="pr-c-name">이름</span>
              <span className="pr-c-org">소속</span>
              <span className="pr-c-num">기본급</span>
              <span className="pr-c-num">수당 합계</span>
              <span className="pr-c-num">공제 합계</span>
              <span className="pr-c-num">실지급액</span>
              <span className="pr-c-x">상세</span>
            </div>
            {preview.employees.map((e) => (
              <div key={e.employeeId} className="pr-group">
                <div className={`hr-tr pr-row is-click${open === e.employeeId ? ' is-open' : ''}`} onClick={() => setOpen(open === e.employeeId ? null : e.employeeId)}>
                  <span className="pr-c-no">{e.employeeNo}</span>
                  <span className="pr-c-name hr-strong">{e.name}</span>
                  <span className="pr-c-org">{e.orgUnitName}</span>
                  <span className="pr-c-num">{won(e.basePay)}</span>
                  <span className="pr-c-num">{won(e.grossPay - e.basePay)}</span>
                  <span className="pr-c-num">{won(e.totalDeduction)}</span>
                  <span className={`pr-c-num hr-strong${e.netPay < 0 ? ' pr-red' : ''}`}>{won(e.netPay)}</span>
                  <span className="pr-c-x">{open === e.employeeId ? '▴' : '▾'}</span>
                </div>
                {open === e.employeeId && <Detail p={e} attendance={e.attendance} />}
              </div>
            ))}
            <div className="hr-tr pr-row is-sum">
              <span className="pr-c-no">합계</span>
              <span className="pr-c-name">{`${preview.totals.headcount}명`}</span>
              <span className="pr-c-org" />
              <span className="pr-c-num">{won(preview.employees.reduce((a, e) => a + e.basePay, 0))}</span>
              <span className="pr-c-num">{won(preview.employees.reduce((a, e) => a + e.grossPay - e.basePay, 0))}</span>
              <span className="pr-c-num">{won(preview.totals.totalDeduction)}</span>
              <span className="pr-c-num pr-blue">{won(preview.totals.netPay)}</span>
              <span className="pr-c-x" />
            </div>
          </section>
          <div className="pr-foot">
            <button type="button" className="hr-btn is-secondary" onClick={() => setStep(1)}>
              수동 입력 수정
            </button>
            <button type="button" className="hr-btn" disabled={blocked} title={blocked ? '귀속 월이 끝난 뒤, 음수 실지급이 없을 때 확정할 수 있습니다' : undefined} onClick={confirm}>
              확정
            </button>
          </div>
        </>
      )}
    </>
  );
}

/* ───── 정산 후 — 확정 · 지급완료 ───── */
function SettledRun({ run, options, setMonth, manage }) {
  const notify = useNotify();
  const { modal } = App.useApp();
  const queryClient = useQueryClient();
  const detail = useQuery({ queryKey: payrollRunKey(run.id), queryFn: () => getPayrollRun(run.id) });
  const [open, setOpen] = useState(null);
  const [slip, setSlip] = useState(null);
  const stubs = detail.data?.paystubs ?? [];
  const paid = run.status === 'PAID';

  const markPaid = () =>
    modal.confirm({
      title: `${ym(run.payMonth)} 급여를 지급완료로 바꿀까요?`,
      content: '실제로 급여를 이체한 뒤에 누르세요. 지급완료로 바꾸면 되돌릴 수 없습니다.',
      okText: '지급완료',
      cancelText: '취소',
      onOk: async () => {
        try {
          await markPayrollPaid(run.id);
          queryClient.invalidateQueries({ queryKey: payrollRunsKey });
          notify.success(`${ym(run.payMonth)} 급여를 지급완료로 바꿨습니다`);
        } catch (err) {
          notify.error(err);
        }
      },
    });

  return (
    <>
      <RunBar month={run.payMonth} options={options} setMonth={setMonth} payDate={run.payDate} status={run.status} ledger={stubs} />
      <Steps step={paid ? 4 : 3} />
      <Totals t={run.totals} />
      <section className="hr-table pr-card">
        <div className="pr-card-head">
          <strong>{`${ym(run.payMonth)} 정산 내역`}</strong>
          <span className="pr-muted">{`${paid ? `지급완료 · ${run.paidByName ?? ''}` : `확정 · ${run.confirmedByName ?? ''}`} · 대상 ${run.totals.headcount}명${manage ? '' : ' · 조회 전용'}`}</span>
        </div>
        <div className="hr-tr is-head">
          <span className="pr-c-no">사원번호</span>
          <span className="pr-c-name">이름</span>
          <span className="pr-c-org">소속</span>
          <span className="pr-c-num">지급 합계</span>
          <span className="pr-c-num">공제 합계</span>
          <span className="pr-c-num">회사 부담</span>
          <span className="pr-c-num">실지급액</span>
          <span className="pr-c-x">상세</span>
        </div>
        {detail.isLoading && (
          <div className="hr-tr is-empty">
            <Spin />
          </div>
        )}
        {stubs.map((s) => (
          <div key={s.paystubId} className="pr-group">
            <div className={`hr-tr pr-row is-click${open === s.paystubId ? ' is-open' : ''}`} onClick={() => setOpen(open === s.paystubId ? null : s.paystubId)}>
              <span className="pr-c-no">{s.employeeNo}</span>
              <span className="pr-c-name hr-strong">{s.name}</span>
              <span className="pr-c-org">{s.orgUnitName}</span>
              <span className="pr-c-num">{won(s.grossPay)}</span>
              <span className="pr-c-num">{won(s.totalDeduction)}</span>
              <span className="pr-c-num">{won(s.companyBurdenTotal)}</span>
              <span className="pr-c-num hr-strong">{won(s.netPay)}</span>
              <span className="pr-c-x">{open === s.paystubId ? '▴' : '▾'}</span>
            </div>
            {open === s.paystubId && <StubDetail id={s.paystubId} onSlip={() => setSlip(s)} />}
          </div>
        ))}
        <div className="hr-tr pr-row is-sum">
          <span className="pr-c-no">합계</span>
          <span className="pr-c-name">{`${run.totals.headcount}명`}</span>
          <span className="pr-c-org" />
          <span className="pr-c-num">{won(run.totals.grossPay)}</span>
          <span className="pr-c-num">{won(run.totals.totalDeduction)}</span>
          <span className="pr-c-num">{won(run.totals.companyBurdenTotal)}</span>
          <span className="pr-c-num pr-blue">{won(run.totals.netPay)}</span>
          <span className="pr-c-x" />
        </div>
      </section>
      <p className="pr-note">확정된 급여는 수정할 수 없습니다. 정정은 다음 달 소급 조정 항목으로 입력하세요.</p>
      {manage && !paid && (
        <div className="pr-foot">
          <button type="button" className="hr-btn" onClick={markPaid}>
            지급완료
          </button>
        </div>
      )}
      <PayslipModal id={slip?.paystubId} name={slip?.name} onClose={() => setSlip(null)} />
    </>
  );
}

/** 확정된 명세서 한 장을 펼쳐 볼 때 — 그때 받아 온다 */
function StubDetail({ id, onSlip }) {
  const q = useQuery({ queryKey: payslipKey(id), queryFn: () => getPayslip(id) });
  if (q.isLoading) return <div className="pr-detail"><Spin /></div>;
  if (q.error) return <div className="pr-detail">{errorMessage(q.error)}</div>;
  return (
    <div className="pr-detail-wrap">
      <Detail p={q.data} />
      <button type="button" className="hr-link pr-slip-link" onClick={onSlip}>
        명세서 보기 →
      </button>
    </div>
  );
}

/* ───── 조회만 되는 사람 · 정산 전 달 ───── */
function UnsettledReadonly({ month, options, setMonth }) {
  return (
    <>
      <RunBar month={month} options={options} setMonth={setMonth} status="NONE" ledger={[]} />
      <div className="pr-empty">
        <strong>{`${ym(month)}은 아직 정산되지 않았습니다.`}</strong>
        <span>급여 정산은 급여 관리 권한이 있는 담당자가 합니다.</span>
      </div>
    </>
  );
}
