// 직원 급여 — Figma 458-17828(목록) · 458-18062(옆 창) · 458-18377(급여 변경) · 458-18729(수당·공제 추가) · 458-19078(미등록) · 458-19327(조회 전용)
// 원천징수 비율(80 · 100 · 120%)은 서버에 없어 뺐다(모두 100%로 계산)
import { useState } from 'react';
import { DatePicker, Drawer, Form, Input, InputNumber, Modal, Pagination, Segmented, Select, Spin, Switch } from 'antd';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { employeeSalariesKey, getEmployeeSalaries } from '../../../api/payroll';
import { getPayItems, getPayVariables, payItemsKey, payVariablesKey } from '../../../api/paySettings';
import { assignPayItem, getSalaries, registerSalary, salariesKey } from '../../../api/payrollAdmin';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { ModalFoot, useOrgOptions } from '../parts';
import { won } from './shared';

const PAGE_SIZE = 20;
const money = { formatter: (v) => (v || v === 0 ? `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''), parser: (v) => v.replace(/,/g, '') };

export default function SalaryTab({ manage, registering, setRegistering, onlyUnregistered }) {
  const org = useOrgOptions();
  const [keyword, setKeyword] = useState('');
  const [kw, setKw] = useState('');
  const [orgUnitId, setOrgUnitId] = useState();
  const [type, setType] = useState('ALL');
  const [onlyMissing, setOnlyMissing] = useState(onlyUnregistered);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null); // 옆 창에 연 직원(목록 행)

  const params = { keyword: keyword || undefined, orgUnitId, page, size: PAGE_SIZE };
  const list = useQuery({ queryKey: salariesKey(params), queryFn: () => getSalaries(params), placeholderData: keepPreviousData });
  const all = list.data?.content ?? [];
  // 급여 방식 · 미등록만은 받은 페이지 안에서 거른다(서버 조건이 없다)
  const rows = all.filter((r) => (!onlyMissing || !r.registered) && (type === 'ALL' || r.salaryType === type));
  const missing = all.filter((r) => !r.registered).length;

  return (
    <>
      <div className="pr-bar">
        <div className="hr-field pr-grow">
          <label>직원</label>
          <Input.Search
            allowClear
            placeholder="이름 · 사원번호 검색"
            value={kw}
            onChange={(e) => setKw(e.target.value)}
            onSearch={(v) => {
              setKeyword(v.trim());
              setPage(0);
            }}
          />
        </div>
        <div className="hr-field">
          <label>조직</label>
          <Select className="pr-select" allowClear placeholder="전체 조직" value={orgUnitId} options={org.options} popupMatchSelectWidth={false} onChange={(v) => { setOrgUnitId(v); setPage(0); }} />
        </div>
        <div className="hr-field">
          <label>급여 방식</label>
          <Select
            className="pr-select"
            value={type}
            onChange={setType}
            options={[
              { value: 'ALL', label: '전체' },
              { value: 'ANNUAL', label: '연봉제' },
              { value: 'MONTHLY', label: '월급제' },
            ]}
          />
        </div>
        <span className="pr-spacer" />
        <label className="pr-switch">
          미등록만 보기
          <Switch size="small" checked={onlyMissing} onChange={setOnlyMissing} />
        </label>
      </div>

      <section className="hr-table pr-card">
        <div className="pr-card-head">
          <strong>{`총 ${list.data?.totalElements ?? 0}명 · 미등록 ${missing}명`}</strong>
          <span className="pr-muted">직원을 선택하면 급여 상세를 확인할 수 있습니다.</span>
        </div>
        <div className="hr-tr is-head">
          <span className="pr-c-no">사원번호</span>
          <span className="pr-c-name">이름</span>
          <span className="pr-c-org">소속</span>
          <span className="pr-c-type">급여 방식</span>
          <span className="pr-c-num">연봉</span>
          <span className="pr-c-num">월 기본급</span>
          <span className="pr-c-date">적용 시작일</span>
          <span className="pr-c-st">상태</span>
        </div>
        {list.isLoading && (
          <div className="hr-tr is-empty">
            <Spin />
          </div>
        )}
        {list.error && <div className="hr-tr is-empty">{errorMessage(list.error)}</div>}
        {!list.isLoading && rows.length === 0 && <div className="hr-tr is-empty">조건에 맞는 직원이 없습니다</div>}
        {rows.map((r) => (
          <div key={r.employeeId} className={`hr-tr pr-row is-click${selected?.employeeId === r.employeeId ? ' is-selected' : ''}`} onClick={() => setSelected(r)}>
            <span className="pr-c-no">{r.employeeNo}</span>
            <span className="pr-c-name hr-strong">{r.name}</span>
            <span className="pr-c-org">{r.orgUnitName}</span>
            <span className="pr-c-type">{r.salaryType ? labelOf('salaryType', r.salaryType) : '–'}</span>
            <span className="pr-c-num">{r.annualSalary != null ? won(r.annualSalary) : '–'}</span>
            <span className="pr-c-num">{r.monthlyBase != null ? won(r.monthlyBase) : '–'}</span>
            <span className="pr-c-date">{r.effectiveFrom ?? '–'}</span>
            <span className="pr-c-st">
              <span className={`hr-badge ${r.registered ? 'is-ok' : 'is-warn'}`}>{r.registered ? '등록' : '미등록'}</span>
            </span>
          </div>
        ))}
        {(list.data?.totalPages ?? 0) > 1 && (
          <div className="pr-pager">
            <span className="pr-muted">{`전체 ${list.data.totalElements}명 중 ${page * PAGE_SIZE + 1}–${Math.min((page + 1) * PAGE_SIZE, list.data.totalElements)}명`}</span>
            <Pagination size="small" current={page + 1} pageSize={PAGE_SIZE} total={list.data.totalElements} showSizeChanger={false} onChange={(p) => setPage(p - 1)} />
          </div>
        )}
      </section>

      <Drawer open={!!selected} onClose={() => setSelected(null)} size={520} className="pr-drawer" destroyOnHidden title={null} closable={false}>
        {selected && <SalaryPanel row={selected} manage={manage} onClose={() => setSelected(null)} />}
      </Drawer>
      {registering && <SalaryModal employees={all} onClose={() => setRegistering(false)} />}
    </>
  );
}

/* ───── 옆 창 — 현재 급여 · 직원별 수당·공제 · 원천징수 비율 · 변경 이력 ───── */
function SalaryPanel({ row, manage, onClose }) {
  const q = useQuery({ queryKey: employeeSalariesKey(row.employeeId), queryFn: () => getEmployeeSalaries(row.employeeId) });
  const [salaryOpen, setSalaryOpen] = useState(false);
  const [itemOpen, setItemOpen] = useState(null); // { payItemId? }
  const s = q.data;
  const cur = s?.current;
  const items = s?.payItems ?? [];
  const history = s ? buildHistory(s) : [];

  return (
    <div className="pr-panel">
      <div className="pr-panel-head">
        <div>
          <strong>{row.name}</strong>
          <span className={`hr-badge ${cur ? 'is-ok' : 'is-warn'}`}>{cur ? '등록' : '미등록'}</span>
          <p>{`${row.employeeNo} · ${row.orgUnitName}`}</p>
        </div>
        <button type="button" className="pr-x" aria-label="닫기" onClick={onClose}>
          ✕
        </button>
      </div>
      {q.isLoading && <Spin />}
      {q.error && <p className="hr-notice is-error">{errorMessage(q.error)}</p>}
      {s && !cur && (
        <div className="pr-panel-empty">
          <strong>등록된 급여가 없습니다.</strong>
          <span>급여를 등록하면 직원별 수당·공제와 변경 이력을 함께 관리할 수 있습니다.</span>
          {manage && (
            <button type="button" className="hr-btn" onClick={() => setSalaryOpen(true)}>
              급여 등록
            </button>
          )}
        </div>
      )}
      {cur && (
        <>
          <section className="pr-box">
            <div className="pr-box-head">
              <strong>현재 급여</strong>
              {manage && (
                <button type="button" className="hr-btn is-sm" onClick={() => setSalaryOpen(true)}>
                  급여 변경
                </button>
              )}
            </div>
            <dl className="pr-kv">
              <dt>급여 방식</dt>
              <dd>{labelOf('salaryType', cur.salaryType)}</dd>
              <dt>연봉</dt>
              <dd>{cur.annualSalary != null ? won(cur.annualSalary) : '–'}</dd>
              <dt>월 기본급</dt>
              <dd>{won(cur.monthlyBase)}</dd>
              <dt>적용 시작일</dt>
              <dd>{cur.effectiveFrom}</dd>
            </dl>
          </section>

          <section className="pr-box">
            <div className="pr-box-head">
              <strong>직원별 수당·공제</strong>
              {manage && (
                <button type="button" className="hr-btn is-sm" onClick={() => setItemOpen({})}>
                  추가
                </button>
              )}
            </div>
            <div className="pr-mini">
              <div className="pr-mini-tr is-head">
                <span>항목</span>
                <span className="is-num">금액</span>
                <span>적용 시작일</span>
                <span />
              </div>
              {items.length === 0 && <p className="pr-muted">적용되는 수당·공제가 없습니다.</p>}
              {items.map((it) => {
                const last = it.history?.[0];
                const own = it.applyTo === 'SELECTED';
                return (
                  <div key={it.payItemId} className="pr-mini-tr">
                    <span>{it.name}</span>
                    <span className="is-num hr-strong">{it.currentAmount != null ? won(it.currentAmount) : '–'}</span>
                    <span>{own ? (last?.effectiveFrom ?? '–') : '전체 적용'}</span>
                    <span>
                      {manage && own && (
                        <button type="button" className="hr-btn is-sm" onClick={() => setItemOpen({ payItemId: it.payItemId })}>
                          변경
                        </button>
                      )}
                    </span>
                  </div>
                );
              })}
            </div>
          </section>

          <section className="pr-box">
            <div className="pr-box-head">
              <strong>변경 이력</strong>
            </div>
            <div className="pr-history">
              {history.length === 0 && <p className="pr-muted">변경 이력이 없습니다.</p>}
              {history.map((h) => (
                <div key={h.key}>
                  <div className="pr-history-top">
                    <strong>{h.title}</strong>
                    <span>{h.effectiveFrom}</span>
                  </div>
                  <span>{h.change}</span>
                  <em>{`사유: ${h.reason || '-'} · 등록자: ${h.by ?? '-'}`}</em>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
      {salaryOpen && <SalaryModal employee={row} current={cur} onClose={() => setSalaryOpen(false)} />}
      {itemOpen && <ItemModal employee={row} presetId={itemOpen.payItemId} assigned={items} onClose={() => setItemOpen(null)} />}
    </div>
  );
}

/** 급여 이력 + 지정 직원 항목 이력을 한 줄로 — 이전 → 변경 */
function buildHistory(s) {
  const out = [];
  const sal = [...(s.salaries ?? [])].sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom) || a.createdAt.localeCompare(b.createdAt));
  sal.forEach((r, i) => {
    const prev = sal[i - 1];
    const show = (x) => (x.annualSalary != null ? won(x.annualSalary) : `월 ${won(x.monthlyBase)}`);
    out.push({ key: `s${r.id}`, title: r.annualSalary != null ? '연봉' : '월 기본급', change: prev ? `${show(prev)} → ${show(r)}` : `처음 등록 · ${show(r)}`, effectiveFrom: r.effectiveFrom, reason: r.reason, by: r.createdByName, at: r.createdAt });
  });
  (s.payItems ?? []).forEach((it) => {
    const hist = [...(it.history ?? [])].sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom) || a.createdAt.localeCompare(b.createdAt));
    hist.forEach((h, i) => {
      out.push({ key: `i${h.id}`, title: it.name, change: `${i ? won(hist[i - 1].amount) : '미등록'} → ${h.amount ? won(h.amount) : '적용 끝'}`, effectiveFrom: h.effectiveFrom, reason: h.reason, by: h.createdByName, at: h.createdAt });
    });
  });
  return out.sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom) || b.at.localeCompare(a.at));
}

/* ───── 급여 등록 · 변경(458-18377) — 연봉제면 월 기본급 = 연봉 ÷ 연봉 분할 개월(서버가 정한다) ───── */
function SalaryModal({ employee, current, employees, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const vars = useQuery({ queryKey: payVariablesKey, queryFn: getPayVariables });
  const split = Number(vars.data?.find((v) => v.varCode === 'ANNUAL_SPLIT_MONTHS')?.current?.value ?? 12);
  const salaryType = Form.useWatch('salaryType', form) ?? current?.salaryType ?? 'ANNUAL';
  const annual = Form.useWatch('annualSalary', form);
  const who = Form.useWatch('employeeId', form);
  const target = employee ?? employees?.find((e) => e.employeeId === who);

  const save = useMutation({
    mutationFn: (v) =>
      registerSalary(target.employeeId, {
        salaryType: v.salaryType,
        annualSalary: v.salaryType === 'ANNUAL' ? v.annualSalary : null,
        monthlyBase: v.salaryType === 'MONTHLY' ? v.monthlyBase : null,
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
        reason: v.reason.trim(),
      }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['salaries'] });
      notify.success(`${target.name} 급여를 저장했습니다`, data);
      onClose();
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });

  return (
    <Modal open title={current ? '급여 변경' : '급여 등록'} footer={null} onCancel={onClose} width={480} className="hr-modal pr-modal" destroyOnHidden centered>
      {employee && <p className="hr-modal-sub">{`${employee.name} · ${employee.orgUnitName}`}</p>}
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{ salaryType: current?.salaryType ?? 'ANNUAL', annualSalary: current?.annualSalary ?? undefined, monthlyBase: current?.monthlyBase ?? undefined, effectiveFrom: dayjs() }}
        onFinish={(v) => save.mutate(v)}
      >
        {!employee && (
          <Form.Item name="employeeId" label="직원" rules={[{ required: true, message: '직원을 고르세요' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="급여를 등록할 직원"
              options={[...(employees ?? [])].sort((a, b) => a.registered - b.registered).map((e) => ({ value: e.employeeId, label: `${e.name} · ${e.employeeNo}${e.registered ? '' : ' (미등록)'}` }))}
            />
          </Form.Item>
        )}
        <Form.Item name="salaryType" label="급여 방식">
          <Segmented
            block
            options={[
              { value: 'MONTHLY', label: '월급제' },
              { value: 'ANNUAL', label: '연봉제' },
            ]}
          />
        </Form.Item>
        {salaryType === 'ANNUAL' ? (
          <>
            <Form.Item name="annualSalary" label="연봉" rules={[{ required: true, message: '연봉을 입력하세요' }]}>
              <InputNumber className="full-width" min={0} step={1000000} suffix="원" {...money} />
            </Form.Item>
            <p className="pr-calc">{`🔒 월 기본급 ${annual ? won(Math.floor(annual / split)) : '-'} (연봉 ÷ ${split})`}</p>
          </>
        ) : (
          <Form.Item name="monthlyBase" label="월 기본급" rules={[{ required: true, message: '월 기본급을 입력하세요' }]}>
            <InputNumber className="full-width" min={0} step={100000} suffix="원" {...money} />
          </Form.Item>
        )}
        <Form.Item name="effectiveFrom" label="적용 시작일" rules={[{ required: true, message: '적용 시작일을 고르세요' }]}>
          <DatePicker className="full-width" format="YYYY-MM-DD" />
        </Form.Item>
        <Form.Item name="reason" label="사유 (필수)" rules={[{ required: true, whitespace: true, message: '사유를 입력하세요' }]}>
          <Input.TextArea rows={3} maxLength={255} placeholder="예: 2026년 연봉 조정" />
        </Form.Item>
        <p className="hr-notice">이미 정산된 달에 걸치면 정산된 명세서는 바뀌지 않습니다. 차액은 다음 달 소급 조정으로 처리하세요.</p>
        <ModalFoot onCancel={onClose} busy={save.isPending} />
      </Form>
    </Modal>
  );
}

/* ───── 수당·공제 추가 · 변경(458-18729) — "선택 직원" 고정액 항목만, 금액 0 이면 적용 끝 ───── */
function ItemModal({ employee, presetId, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const items = useQuery({ queryKey: payItemsKey, queryFn: getPayItems });
  const choices = (items.data ?? []).filter((i) => i.applyTo === 'SELECTED' && i.calcMethod === 'FIXED' && i.isActive);
  const save = useMutation({
    mutationFn: (v) => assignPayItem(employee.employeeId, { payItemId: v.payItemId, amount: v.amount, effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'), reason: v.reason.trim() }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['salaries'] });
      notify.success('수당·공제를 저장했습니다', data);
      onClose();
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });
  return (
    <Modal open title={presetId ? '수당·공제 변경' : '수당·공제 추가'} footer={null} onCancel={onClose} width={480} className="hr-modal pr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{`${employee.name} · ${employee.orgUnitName}`}</p>
      <Form form={form} layout="vertical" requiredMark={false} initialValues={{ payItemId: presetId, effectiveFrom: dayjs() }} onFinish={(v) => save.mutate(v)}>
        <Form.Item name="payItemId" label="항목" extra="선택 직원 항목만 표시" rules={[{ required: true, message: '항목을 고르세요' }]}>
          <Select disabled={!!presetId} loading={items.isLoading} options={choices.map((i) => ({ value: i.id, label: i.name }))} placeholder={choices.length ? '항목을 고르세요' : '선택 직원 고정액 항목이 없습니다'} />
        </Form.Item>
        <Form.Item name="amount" label="금액" extra="0원으로 저장하면 이 날짜부터 적용을 끝냅니다" rules={[{ required: true, message: '금액을 입력하세요' }]}>
          <InputNumber className="full-width" min={0} step={10000} suffix="원" {...money} />
        </Form.Item>
        <Form.Item name="effectiveFrom" label="적용 시작일" rules={[{ required: true, message: '적용 시작일을 고르세요' }]}>
          <DatePicker className="full-width" format="YYYY-MM-DD" />
        </Form.Item>
        <Form.Item name="reason" label="사유 (필수)" rules={[{ required: true, whitespace: true, message: '사유를 입력하세요' }]}>
          <Input.TextArea rows={3} maxLength={255} placeholder="예: 근속수당 신규 지급" />
        </Form.Item>
        <ModalFoot onCancel={onClose} busy={save.isPending} />
      </Form>
    </Modal>
  );
}
