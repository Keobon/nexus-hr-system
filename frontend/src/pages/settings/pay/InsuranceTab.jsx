// 4대보험 탭(Figma 71:5113) + 요율 수정 창(452:9254)
// 서버는 4대보험을 "과세 급여 × 요율" 공제 항목(TAXABLE_RATE)으로만 둔다 — 적용 시작일 · 변경 이력 · 산재보험은 뺐다
import { useState } from 'react';
import { InputNumber, Modal, Select, Spin } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getPayItems, payItemsKey, updatePayItem } from '../../../api/paySettings';
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

export default function InsuranceTab() {
  const items = useQuery({ queryKey: payItemsKey, queryFn: getPayItems });
  const [editing, setEditing] = useState(false);

  if (items.isLoading) return <Spin />;
  if (items.error) return <p className="hr-notice is-error">{errorMessage(items.error)}</p>;
  const rows = (items.data ?? []).filter((i) => i.calcMethod === 'TAXABLE_RATE');

  return (
    <>
      <div className="pay-bar is-plain">
        <div className="pay-basis">
          <span>적용 기준</span>
          <strong>지금 적용 중인 요율</strong>
          <em className="pay-chip-on">적용 중</em>
        </div>
        <div className="hr-actions">
          <button type="button" className="hr-btn" disabled={!rows.length} onClick={() => setEditing(true)}>
            요율 수정
          </button>
        </div>
      </div>

      <section className="hr-table pay-card">
        <div className="pay-card-head">
          <strong>현재 적용 요율</strong>
          <span>다음 급여 정산부터 이 요율로 계산합니다</span>
        </div>
        <div className="hr-tr is-head">
          <span className="pay-c-name">보험</span>
          <span className="pay-c-num">근로자 요율</span>
          <span className="pay-c-num">회사 요율</span>
          <span className="pay-c-num">계산 기준</span>
          <span className="pay-c-num">상한</span>
          <span className="pay-c-num">하한</span>
        </div>
        {rows.map((r) => (
          <div key={r.id} className={`hr-tr pay-row${r.isActive ? '' : ' is-off'}`}>
            <span className="pay-c-name hr-strong">{r.name}</span>
            <span className="pay-c-num">{pct(r.employeeRate)}</span>
            <span className="pay-c-num">{pct(r.companyRate)}</span>
            <span className="pay-c-num pay-muted">과세 급여</span>
            <span className="pay-c-num">{won(r.baseUpperLimit)}</span>
            <span className="pay-c-num">{won(r.baseLowerLimit)}</span>
          </div>
        ))}
        <div className="pay-card-foot">
          <span>모든 보험은 과세 급여에 요율을 곱해 계산합니다(상한 · 하한이 있으면 그 사이로 맞춤). 근로자 요율만 급여에서 공제합니다.</span>
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
    Object.fromEntries(rows.map((r) => [r.id, { employeeRate: r.employeeRate, companyRate: r.companyRate, baseUpperLimit: r.baseUpperLimit, baseLowerLimit: r.baseLowerLimit }])),
  );
  const [busy, setBusy] = useState(false);
  const set = (id, key, v) => setDraft((d) => ({ ...d, [id]: { ...d[id], [key]: v ?? null } }));
  const employment = rows.find((r) => r.name.includes('고용'));

  // 바뀐 보험만 PATCH
  const save = async () => {
    const changed = rows.filter((r) => ['employeeRate', 'companyRate', 'baseUpperLimit', 'baseLowerLimit'].some((k) => draft[r.id][k] !== r[k]));
    if (!changed.length) return onClose();
    setBusy(true);
    try {
      for (const r of changed) await updatePayItem(r.id, draft[r.id]);
      queryClient.invalidateQueries({ queryKey: payItemsKey });
      notify.success(`${changed.map((r) => r.name).join(' · ')} 요율을 저장했습니다`);
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
    <Modal open title="요율 수정" footer={null} onCancel={onClose} width={960} className="hr-modal pay-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">바꿀 요율 · 상한 · 하한을 입력하세요. 저장하면 다음 급여 정산부터 새 요율이 쓰입니다.</p>
      <div className="hr-table pay-edit-table">
        <div className="hr-tr is-head">
          <span className="pay-c-name">보험</span>
          <span className="pay-c-in">근로자 요율</span>
          <span className="pay-c-in">회사 요율</span>
          <span className="pay-c-in">상한</span>
          <span className="pay-c-in">하한</span>
        </div>
        {rows.map((r) => (
          <div key={r.id} className="hr-tr pay-row">
            <span className="pay-c-name hr-strong">{r.name}</span>
            <span className="pay-c-in">{rate(r.id, 'employeeRate')}</span>
            <span className="pay-c-in">{rate(r.id, 'companyRate')}</span>
            <span className="pay-c-in">{amount(r.id, 'baseUpperLimit')}</span>
            <span className="pay-c-in">{amount(r.id, 'baseLowerLimit')}</span>
          </div>
        ))}
      </div>
      {employment && (
        <div className="pay-field">
          <label>고용보험 회사 요율(회사 규모)</label>
          <Select
            className="full-width"
            value={EMPLOYMENT_COMPANY.some(([v]) => v === draft[employment.id].companyRate) ? draft[employment.id].companyRate : undefined}
            placeholder="회사 규모를 고르면 회사 요율을 채웁니다"
            onChange={(v) => set(employment.id, 'companyRate', v)}
            options={EMPLOYMENT_COMPANY.map(([v, l]) => ({ value: v, label: l }))}
          />
        </div>
      )}
      <p className="hr-notice">이미 확정된 급여는 바뀌지 않습니다. 저장하는 즉시 다음 정산부터 쓰입니다.</p>
      <ModalFoot onCancel={onClose} busy={busy} okType="button" onOk={save} />
    </Modal>
  );
}
