// 직원 상세 › 추가 정보(F-EMP-07) — 회사 설정 › 직원 정보 항목에서 만든 항목의 값. 인사팀(EMPLOYEE_MANAGE)이 모든 항목을 고친다
// PUT /employees/{id}/field-values 는 활성 항목 전체를 보낸 값으로 바꾸므로, 창에서 모든 항목 값을 같이 보낸다
// "본인 수정" 이 켜진 항목은 직원도 내정보에서 고칠 수 있다(꺼진 항목은 인사팀만)
import { useState } from 'react';
import { DatePicker, Form, Input, InputNumber, Modal, Select } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { api } from '../../../api/client';
import { employeeKey } from '../../../api/employees';
import { employeeFieldsKey, getEmployeeFields } from '../../../api/profile';
import { useNotify } from '../../../components/common/useNotify';
import { ModalFoot } from '../parts';

/** PUT /employees/{id}/field-values { values: [{ fieldDefId, seq, value }] } — 활성 항목 전체 교체(필수 검사 포함). 잘못된 값은 fields["values.{항목 ID}"] */
const saveFieldValues = (id, values) => api.put(`/employees/${id}/field-values`, { values });

export default function EmployeeFieldsCard({ employee: e, canEdit }) {
  const defs = useQuery({ queryKey: employeeFieldsKey, queryFn: getEmployeeFields });
  const [open, setOpen] = useState(false);
  const list = [...(defs.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const valuesOf = (id) => (e.fieldValues ?? []).filter((v) => v.fieldDefId === id).sort((a, b) => a.seq - b.seq).map((v) => v.value);
  if (!defs.data || list.length === 0) return null;

  return (
    <div className="hr-card hr-info" style={{ gap: '0.75rem' }}>
      <div className="hr-card-head">
        <p className="hr-card-title">추가 정보</p>
        {canEdit && (
          <button type="button" className="hr-link" onClick={() => setOpen(true)}>
            수정
          </button>
        )}
      </div>
      <div className="hr-row2">
        {[list.slice(0, Math.ceil(list.length / 2)), list.slice(Math.ceil(list.length / 2))].map((half, i) => (
          <div key={i} className="hr-info">
            {half.map((d) => {
              const vals = valuesOf(d.id);
              return (
                <div key={d.id} className="hr-kv">
                  <span className="k">{d.name}</span>
                  <span className="v">{vals.length ? vals.join(', ') : '-'}</span>
                </div>
              );
            })}
          </div>
        ))}
      </div>
      {open && <FieldValuesModal employee={e} defs={list} valuesOf={valuesOf} onClose={() => setOpen(false)} />}
    </div>
  );
}

/** 타입별 입력칸 — 값은 문자열로 보낸다(날짜 YYYY-MM-DD) */
function ValueInput({ def, value, onChange }) {
  if (def.fieldType === 'SELECT') return <Select value={value || undefined} onChange={onChange} allowClear placeholder="선택" options={(def.options ?? []).map((o) => ({ value: o, label: o }))} />;
  if (def.fieldType === 'DATE') return <DatePicker className="full-width" value={value ? dayjs(value) : null} onChange={(d) => onChange(d ? d.format('YYYY-MM-DD') : '')} />;
  if (def.fieldType === 'NUMBER') return <InputNumber className="full-width" value={value === '' || value == null ? null : Number(value)} onChange={(n) => onChange(n == null ? '' : String(n))} />;
  if (def.fieldType === 'LONG_TEXT') return <Input.TextArea value={value} onChange={(ev) => onChange(ev.target.value)} autoSize={{ minRows: 2, maxRows: 5 }} maxLength={1000} />;
  return <Input value={value} onChange={(ev) => onChange(ev.target.value)} maxLength={200} />;
}

function FieldValuesModal({ employee: e, defs, valuesOf, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  // 항목마다 값 배열(여러 건 허용이 아니면 한 칸)
  const [vals, setVals] = useState(() => Object.fromEntries(defs.map((d) => [d.id, valuesOf(d.id).length ? valuesOf(d.id) : ['']])));
  const [errors, setErrors] = useState({});
  const set = (id, i, v) => setVals((s) => ({ ...s, [id]: s[id].map((x, j) => (j === i ? v : x)) }));

  const save = useMutation({
    mutationFn: () =>
      saveFieldValues(
        e.id,
        defs.flatMap((d) => vals[d.id].map((v) => (v ?? '').trim()).filter(Boolean).map((value, i) => ({ fieldDefId: d.id, seq: i + 1, value }))),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: employeeKey(e.id) });
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      notify.success('추가 정보를 저장했습니다');
      onClose();
    },
    onError: (err) => {
      if (err?.code === 'VALIDATION_ERROR' && err.fields) {
        setErrors(Object.fromEntries(Object.entries(err.fields).map(([k, m]) => [k.replace('values.', ''), m])));
        return;
      }
      notify.error(err);
    },
  });
  const submit = () => {
    // 필수 항목은 먼저 확인
    const missing = Object.fromEntries(defs.filter((d) => d.isRequired && !vals[d.id].some((v) => (v ?? '').trim())).map((d) => [String(d.id), '필수 항목입니다']));
    setErrors(missing);
    if (Object.keys(missing).length === 0) save.mutate();
  };

  return (
    <Modal open title="추가 정보 수정" footer={null} onCancel={onClose} width={560} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{[e.name, e.employeeNo, e.orgUnitName].filter(Boolean).join(' · ')}</p>
      <Form layout="vertical" requiredMark={false} onFinish={submit}>
        {defs.map((d) => (
          <Form.Item
            key={d.id}
            label={`${d.name}${d.isRequired ? ' *' : ''}`}
            validateStatus={errors[String(d.id)] ? 'error' : undefined}
            help={errors[String(d.id)]}
            extra={d.isSelfEditable ? '직원도 내정보에서 고칠 수 있습니다' : '인사팀만 고칠 수 있습니다'}
          >
            <div className="hr-multi">
              {vals[d.id].map((v, i) => (
                <div key={i} className="hr-multi-row">
                  <ValueInput def={d} value={v} onChange={(x) => set(d.id, i, x)} />
                  {d.isMultiple && vals[d.id].length > 1 && (
                    <button type="button" className="hr-icon-btn" aria-label="이 값 지우기" onClick={() => setVals((s) => ({ ...s, [d.id]: s[d.id].filter((_, j) => j !== i) }))}>
                      <MinusCircleOutlined />
                    </button>
                  )}
                </div>
              ))}
              {d.isMultiple && (
                <button type="button" className="hr-link" onClick={() => setVals((s) => ({ ...s, [d.id]: [...s[d.id], ''] }))}>
                  <PlusOutlined /> 값 추가
                </button>
              )}
            </div>
          </Form.Item>
        ))}
        <ModalFoot onCancel={onClose} busy={save.isPending} />
      </Form>
    </Modal>
  );
}
