// 계산 변수 탭(Figma 453:8899) + 변경 창(453:9936). 값은 바꾸는 게 아니라 "적용 시작일이 있는 새 값"을 더한다(POST)
// Figma 의 식대 비과세 한도 · 연장근로 가산율은 변수가 아니라 항목마다의 설정이라 수당·공제 항목에서 바꾼다
import { useState } from 'react';
import { DatePicker, Form, InputNumber, Modal, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { createPayVariable, getPayVariables, payVariablesKey } from '../../../api/paySettings';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { ModalFoot } from '../../hr/parts';
import { VAR_META, VAR_ORDER, varText } from './shared';

const day = (iso) => (iso ? iso.slice(0, 10) : '–');

/** 변경 이력 — 변수마다 적용 시작일 순으로 이어 "이전 → 변경"(정정으로 밀린 행은 뺀다) */
function changes(vars) {
  const out = [];
  vars.forEach((v) => {
    const list = [...(v.history ?? []), ...(v.upcoming ?? [])]
      .filter((r) => !r.superseded)
      .sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom) || a.createdAt.localeCompare(b.createdAt));
    list.forEach((r, i) => out.push({ code: v.varCode, before: i > 0 ? list[i - 1].value : null, after: r.value, effectiveFrom: r.effectiveFrom, createdAt: r.createdAt }));
  });
  return out.sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.effectiveFrom.localeCompare(a.effectiveFrom));
}

export default function VariablesTab() {
  const vars = useQuery({ queryKey: payVariablesKey, queryFn: getPayVariables });
  const [target, setTarget] = useState(null);

  if (vars.isLoading) return <Spin />;
  if (vars.error) return <p className="hr-notice is-error">{errorMessage(vars.error)}</p>;
  const byCode = Object.fromEntries((vars.data ?? []).map((v) => [v.varCode, v]));
  const codes = VAR_ORDER.filter((c) => byCode[c]);
  const log = changes(vars.data ?? []);

  return (
    <>
      <section className="hr-table pay-card">
        <div className="hr-tr is-head">
          <span className="pay-c-var">변수</span>
          <span className="pay-c-val">현재 값</span>
          <span className="pay-c-val">적용 시작일</span>
          <span className="pay-c-val">예정</span>
          <span className="pay-c-btn">변경</span>
        </div>
        {codes.map((c) => {
          const v = byCode[c];
          const next = v.upcoming?.[0];
          return (
            <div key={c} className="hr-tr pay-row">
              <span className="pay-c-var pay-two">
                <strong className="hr-strong">{labelOf('payVariable', c)}</strong>
                <em>{VAR_META[c]?.desc}</em>
              </span>
              <span className="pay-c-val">{varText(c, v.current?.value)}</span>
              <span className="pay-c-val">{v.current?.effectiveFrom ?? '–'}</span>
              <span className="pay-c-val">{next ? `${varText(c, next.value)} · ${next.effectiveFrom}부터` : '–'}</span>
              <span className="pay-c-btn">
                <button type="button" className="hr-btn is-sm" onClick={() => setTarget(v)}>
                  변경
                </button>
              </span>
            </div>
          );
        })}
        <div className="pay-card-foot">
          <span>식대 비과세 한도 · 연장근로 가산율은 항목마다 정합니다 — 수당·공제 항목에서 해당 항목을 눌러 바꾸세요.</span>
        </div>
      </section>

      <section className="hr-card pay-log">
        <p className="hr-card-title">변경 이력</p>
        <div className="hr-table is-sm">
          <div className="hr-tr is-head">
            <span className="pay-c-var">변수</span>
            <span className="pay-c-val">이전 → 변경</span>
            <span className="pay-c-val">적용 시작일</span>
            <span className="pay-c-val">등록일</span>
          </div>
          {log.length === 0 && <div className="hr-tr is-empty">변경 이력이 없습니다</div>}
          {log.map((r) => (
            <div key={`${r.code}-${r.createdAt}`} className="hr-tr">
              <span className="pay-c-var">{labelOf('payVariable', r.code)}</span>
              <span className="pay-c-val">{r.before == null ? `처음 등록 · ${varText(r.code, r.after)}` : `${varText(r.code, r.before)} → ${varText(r.code, r.after)}`}</span>
              <span className="pay-c-val">{r.effectiveFrom}</span>
              <span className="pay-c-val">{day(r.createdAt)}</span>
            </div>
          ))}
        </div>
      </section>

      {target && <VariableModal v={target} onClose={() => setTarget(null)} />}
    </>
  );
}

function VariableModal({ v, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const meta = VAR_META[v.varCode] ?? {};
  const name = labelOf('payVariable', v.varCode);
  const save = useMutation({
    mutationFn: (f) => createPayVariable({ varCode: v.varCode, value: f.value, effectiveFrom: f.effectiveFrom.format('YYYY-MM-DD') }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: payVariablesKey });
      notify.success(`${name}을(를) 저장했습니다`);
      onClose();
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });
  return (
    <Modal open title={`${name} 변경`} footer={null} onCancel={onClose} width={440} className="hr-modal pay-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{`현재 ${varText(v.varCode, v.current?.value)} · ${v.current?.effectiveFrom ?? '-'}부터`}</p>
      <Form form={form} layout="vertical" requiredMark={false} initialValues={{ value: v.current?.value, effectiveFrom: dayjs() }} onFinish={(f) => save.mutate(f)}>
        <Form.Item name="value" label="새 값" rules={[{ required: true, message: '값을 입력하세요' }]}>
          <InputNumber
            className="full-width"
            min={0}
            step={meta.step}
            suffix={meta.unit}
            formatter={meta.unit === '원' ? (x) => (x || x === 0 ? `${x}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : '') : undefined}
            parser={meta.unit === '원' ? (x) => x.replace(/,/g, '') : undefined}
          />
        </Form.Item>
        <Form.Item name="effectiveFrom" label="적용 시작일" rules={[{ required: true, message: '적용 시작일을 고르세요' }]}>
          <DatePicker className="full-width" format="YYYY-MM-DD" />
        </Form.Item>
        <p className="hr-notice">변경 내용은 적용 시작일 이후 정산부터 반영됩니다. 확정된 급여는 바뀌지 않습니다.</p>
        <ModalFoot onCancel={onClose} busy={save.isPending} />
      </Form>
    </Modal>
  );
}
