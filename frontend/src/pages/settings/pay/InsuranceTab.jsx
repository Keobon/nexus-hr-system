// 4대보험 탭(Figma 71:5113) + 요율 변경 창(452:9254)
// 요율 · 상한 · 하한은 해마다 바뀌므로 덮어쓰지 않고 "적용 시작일이 있는 새 값"을 더한다(계산 변수 탭과 같은 방식). 산재보험은 뺐다
import { useState } from 'react';
import { DatePicker, InputNumber, Modal, Select, Spin } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { getInsuranceRates, insuranceRatesKey, payItemsKey, saveInsuranceRates } from '../../../api/paySettings';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { ModalFoot } from '../../hr/parts';
import { pct, won } from './shared';

// 고용보험 회사 요율 — 회사 규모별(고용보험법 시행령, 실업급여 0.9% + 고용안정 · 직업능력개발)
const EMPLOYMENT_COMPANY = [
  [1.15, '150인 미만 1.15%'],
  [1.35, '150인 이상 우선지원 1.35%'],
  [1.55, '150~1,000인 미만 1.55%'],
  [1.75, '1,000인 이상 1.75%'],
];
const VALUE_KEYS = ['employeeRate', 'companyRate', 'baseUpperLimit', 'baseLowerLimit'];

const day = (iso) => (iso ? iso.slice(0, 10) : '–');
const rateText = (r) => `근로자 ${pct(r.employeeRate)} · 회사 ${pct(r.companyRate)} · 상한 ${won(r.baseUpperLimit)} · 하한 ${won(r.baseLowerLimit)}`;

/** 변경 이력 — 보험마다 적용 시작일 순으로 이어 "이전 → 변경"(정정으로 밀린 행은 뺀다) */
function changes(rows) {
  const out = [];
  rows.forEach((item) => {
    const list = [...(item.history ?? []), ...(item.upcoming ?? [])]
      .filter((r) => !r.superseded)
      .sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom) || a.createdAt.localeCompare(b.createdAt));
    list.forEach((r, i) => out.push({ id: item.payItemId, name: item.name, before: i > 0 ? list[i - 1] : null, after: r }));
  });
  return out.sort((a, b) => b.after.effectiveFrom.localeCompare(a.after.effectiveFrom) || a.id - b.id);
}

export default function InsuranceTab() {
  const rates = useQuery({ queryKey: insuranceRatesKey, queryFn: getInsuranceRates });
  const [editing, setEditing] = useState(false);

  if (rates.isLoading) return <Spin />;
  if (rates.error) return <p className="hr-notice is-error">{errorMessage(rates.error)}</p>;
  const rows = rates.data ?? [];
  const log = changes(rows);

  return (
    <>
      <div className="pay-bar is-plain">
        <div className="pay-basis">
          <span>적용 기준</span>
          <strong>오늘 유효한 요율</strong>
          <em className="pay-chip-on">적용 중</em>
        </div>
        <div className="hr-actions">
          <button type="button" className="hr-btn" disabled={!rows.length} onClick={() => setEditing(true)}>
            요율 변경
          </button>
        </div>
      </div>

      <section className="hr-table pay-card">
        <div className="pay-card-head">
          <strong>현재 적용 요율</strong>
          <span>급여 정산은 귀속 월 말일에 유효한 요율로 계산합니다</span>
        </div>
        <div className="hr-tr is-head">
          <span className="pay-c-name">보험</span>
          <span className="pay-c-num">근로자 요율</span>
          <span className="pay-c-num">회사 요율</span>
          <span className="pay-c-num">상한</span>
          <span className="pay-c-num">하한</span>
          <span className="pay-c-num">적용 시작일</span>
          <span className="pay-c-num">예정</span>
        </div>
        {rows.map((r) => {
          const next = r.upcoming?.[0];
          return (
            <div key={r.payItemId} className="hr-tr pay-row">
              <span className="pay-c-name hr-strong">{r.name}</span>
              <span className="pay-c-num">{pct(r.current?.employeeRate)}</span>
              <span className="pay-c-num">{pct(r.current?.companyRate)}</span>
              <span className="pay-c-num">{won(r.current?.baseUpperLimit)}</span>
              <span className="pay-c-num">{won(r.current?.baseLowerLimit)}</span>
              <span className="pay-c-num">{r.current?.effectiveFrom ?? '–'}</span>
              <span className="pay-c-num pay-muted">{next ? `${next.effectiveFrom}부터` : '–'}</span>
            </div>
          );
        })}
        <div className="pay-card-foot">
          <span>모든 보험은 과세 급여에 요율을 곱해 계산합니다(상한 · 하한이 있으면 그 사이로 맞춤). 근로자 요율만 급여에서 공제합니다.</span>
        </div>
      </section>

      <section className="hr-card pay-log">
        <p className="hr-card-title">변경 이력</p>
        <div className="hr-table is-sm">
          <div className="hr-tr is-head">
            <span className="pay-c-name">보험</span>
            <span className="pay-c-grow">이전 → 변경</span>
            <span className="pay-c-val">적용 시작일</span>
            <span className="pay-c-val">등록일</span>
          </div>
          {log.length === 0 && <div className="hr-tr is-empty">변경 이력이 없습니다</div>}
          {log.map((r) => (
            <div key={`${r.id}-${r.after.effectiveFrom}-${r.after.createdAt}`} className="hr-tr">
              <span className="pay-c-name">{r.name}</span>
              <span className="pay-c-grow">{r.before ? `${rateText(r.before)} → ${rateText(r.after)}` : `처음 등록 · ${rateText(r.after)}`}</span>
              <span className="pay-c-val">{r.after.effectiveFrom}</span>
              <span className="pay-c-val">{day(r.after.createdAt)}</span>
            </div>
          ))}
        </div>
      </section>

      {editing && <RateModal rows={rows} onClose={() => setEditing(false)} />}
    </>
  );
}

function RateModal({ rows, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(() =>
    Object.fromEntries(rows.map((r) => [r.payItemId, Object.fromEntries(VALUE_KEYS.map((k) => [k, r.current?.[k] ?? null]))])),
  );
  // 보험 요율은 보통 달 초부터 바뀐다 — 다음 달 1일을 먼저 채운다
  const [effectiveFrom, setEffectiveFrom] = useState(() => dayjs().add(1, 'month').startOf('month'));
  const [busy, setBusy] = useState(false);
  const set = (id, key, v) => setDraft((d) => ({ ...d, [id]: { ...d[id], [key]: v ?? null } }));
  const employment = rows.find((r) => r.name.includes('고용'));

  // 바뀐 보험만 한 번에 저장한다 — 하나라도 틀리면 서버가 아무것도 저장하지 않는다
  const save = async () => {
    const changed = rows.filter((r) => VALUE_KEYS.some((k) => draft[r.payItemId][k] !== (r.current?.[k] ?? null)));
    if (!changed.length) return onClose();
    setBusy(true);
    try {
      await saveInsuranceRates({
        effectiveFrom: effectiveFrom.format('YYYY-MM-DD'),
        items: changed.map((r) => ({ payItemId: r.payItemId, ...draft[r.payItemId] })),
      });
      queryClient.invalidateQueries({ queryKey: insuranceRatesKey });
      queryClient.invalidateQueries({ queryKey: payItemsKey });
      notify.success(`${changed.map((r) => r.name).join(' · ')} 요율을 ${effectiveFrom.format('YYYY-MM-DD')}부터 적용합니다`);
      onClose();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };
  const rate = (id, key) => (
    <InputNumber className="pay-input" min={0} max={100} step={0.1} value={draft[id][key]} onChange={(v) => set(id, key, v)} suffix="%" />
  );
  const amount = (id, key) => (
    <InputNumber
      className="pay-input"
      min={0}
      step={10000}
      value={draft[id][key]}
      onChange={(v) => set(id, key, v)}
      placeholder="없음"
      formatter={(v) => (v ? `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : '')}
      parser={(v) => v.replace(/,/g, '')}
    />
  );

  return (
    <Modal open title="요율 변경" footer={null} onCancel={onClose} width={960} className="hr-modal pay-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">바뀌는 요율 · 상한 · 하한과 적용 시작일을 입력하세요. 이전 값은 이력으로 남습니다.</p>
      <div className="pay-field">
        <label>적용 시작일</label>
        <DatePicker className="full-width" format="YYYY-MM-DD" value={effectiveFrom} onChange={setEffectiveFrom} allowClear={false} />
      </div>
      <div className="hr-table pay-edit-table">
        <div className="hr-tr is-head">
          <span className="pay-c-name">보험</span>
          <span className="pay-c-in">근로자 요율</span>
          <span className="pay-c-in">회사 요율</span>
          <span className="pay-c-in">상한</span>
          <span className="pay-c-in">하한</span>
        </div>
        {rows.map((r) => (
          <div key={r.payItemId} className="hr-tr pay-row">
            <span className="pay-c-name hr-strong">{r.name}</span>
            <span className="pay-c-in">{rate(r.payItemId, 'employeeRate')}</span>
            <span className="pay-c-in">{rate(r.payItemId, 'companyRate')}</span>
            <span className="pay-c-in">{amount(r.payItemId, 'baseUpperLimit')}</span>
            <span className="pay-c-in">{amount(r.payItemId, 'baseLowerLimit')}</span>
          </div>
        ))}
      </div>
      {employment && (
        <div className="pay-field">
          <label>고용보험 회사 요율(회사 규모)</label>
          <Select
            className="full-width"
            value={EMPLOYMENT_COMPANY.some(([v]) => v === draft[employment.payItemId].companyRate) ? draft[employment.payItemId].companyRate : undefined}
            placeholder="회사 규모를 고르면 회사 요율을 채웁니다"
            onChange={(v) => set(employment.payItemId, 'companyRate', v)}
            options={EMPLOYMENT_COMPANY.map(([v, l]) => ({ value: v, label: l }))}
          />
        </div>
      )}
      <p className="hr-notice">귀속 월 말일에 유효한 값으로 정산합니다. 이미 확정된 급여는 바뀌지 않습니다.</p>
      <ModalFoot onCancel={onClose} busy={busy} okType="button" onOk={save} />
    </Modal>
  );
}
