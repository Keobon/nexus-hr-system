// 급여 항목 수정 창 › 적용 대상(2026-11-06) — 고정액 항목의 부서 · 직급 규칙. 값은 덮어쓰지 않고 적용 시작일과 함께 더한다(해제도 같다)
// 금액은 직원 개별 금액 > 직급 규칙 > 부서 규칙 > 항목 기본 금액 중 처음 맞는 하나만 쓴다(서버 계산). 개별 금액은 급여 › 직원 급여에서
import { useState } from 'react';
import { Checkbox, DatePicker, Form, InputNumber, Modal, Segmented, Select, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { jobGradesApi } from '../../../api/orgSettings';
import { addPayItemRule, getPayItemRules, payItemRulesKey } from '../../../api/paySettings';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { ModalFoot, useOrgOptions } from '../../hr/parts';
import { won } from './shared';

const money = { formatter: (v) => (v || v === 0 ? `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''), parser: (v) => v.replace(/,/g, '') };

/** "개발본부 (하위 포함)" · "백엔드팀만" · "과장 이상" · "과장" */
export const targetText = (r) =>
  r.targetType === 'ORG_UNIT' ? `${r.orgUnitName}${r.includeSubOrgs ? ' (하위 포함)' : '만'}` : `${r.jobGradeName}${r.gradeOrAbove ? ' 이상' : ''}`;
const amountText = (row) => (!row ? '–' : row.amount == null ? '해제' : won(row.amount));

export default function ItemRulesPanel({ item }) {
  const rules = useQuery({ queryKey: payItemRulesKey(item.id), queryFn: () => getPayItemRules(item.id) });
  const [editing, setEditing] = useState(null); // null · 'new' · 규칙 하나

  if (rules.isLoading) return <Spin />;
  if (rules.error) return <p className="hr-notice is-error">{errorMessage(rules.error)}</p>;
  const list = rules.data ?? [];

  return (
    <>
      <p className="pay-hint">
        {`금액은 직원 개별 금액 → 직급 규칙 → 부서 규칙 → 기본 금액(${item.applyTo === 'ALL' ? won(item.defaultAmount) : '선택 직원 항목이라 없음'}) 순서로 처음 맞는 하나만 씁니다. 직급은 "그 직급만"이 먼저, 부서는 가까운 부서가 먼저입니다. 개별 금액은 급여 › 직원 급여에서 정합니다.`}
      </p>
      <div className="hr-table is-sm">
        <div className="hr-tr is-head">
          <span style={{ width: '10rem' }}>대상</span>
          <span style={{ width: '7rem' }}>지금 금액</span>
          <span style={{ width: '7rem' }}>적용 시작일</span>
          <span style={{ width: '8rem' }}>예정</span>
          <span>관리</span>
        </div>
        {list.length === 0 && <div className="hr-tr is-empty">부서 · 직급 규칙이 없습니다</div>}
        {list.map((r) => {
          const next = r.upcoming?.[0];
          return (
            <div key={`${r.targetType}-${r.orgUnitId ?? r.jobGradeId}-${r.includeSubOrgs}-${r.gradeOrAbove}`} className="hr-tr">
              <span className="hr-strong" style={{ width: '10rem' }}>{targetText(r)}</span>
              <span style={{ width: '7rem' }}>{amountText(r.current)}</span>
              <span style={{ width: '7rem' }}>{r.current?.effectiveFrom ?? '–'}</span>
              <span style={{ width: '8rem' }} className="pay-muted">{next ? `${amountText(next)} · ${next.effectiveFrom}` : '–'}</span>
              <span>
                <button type="button" className="hr-link" onClick={() => setEditing(r)}>
                  변경 · 해제
                </button>
              </span>
            </div>
          );
        })}
      </div>
      <div className="hr-actions" style={{ justifyContent: 'flex-start' }}>
        <button type="button" className="hr-btn is-sm" onClick={() => setEditing('new')}>
          규칙 추가
        </button>
      </div>
      {editing && <RuleModal item={item} rule={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

/** 규칙 추가 · 변경 · 해제 — 대상은 추가할 때만 고른다 */
function RuleModal({ item, rule, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const org = useOrgOptions();
  const grades = useQuery({ queryKey: jobGradesApi.key(true), queryFn: () => jobGradesApi.list(true) });
  const targetType = Form.useWatch('targetType', form) ?? rule?.targetType ?? 'ORG_UNIT';
  const release = Form.useWatch('release', form);
  const save = useMutation({
    mutationFn: (v) =>
      addPayItemRule(item.id, {
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
        targetType: rule?.targetType ?? v.targetType,
        orgUnitId: rule ? rule.orgUnitId : v.orgUnitId,
        includeSubOrgs: rule ? rule.includeSubOrgs : !!v.includeSubOrgs,
        jobGradeId: rule ? rule.jobGradeId : v.jobGradeId,
        gradeOrAbove: rule ? rule.gradeOrAbove : !!v.gradeOrAbove,
        amount: v.release ? null : v.amount,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: payItemRulesKey(item.id) });
      queryClient.invalidateQueries({ queryKey: ['salaries'] });
      notify.success(release ? '규칙을 해제했습니다' : '규칙을 저장했습니다');
      onClose();
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });
  return (
    <Modal open title={rule ? `규칙 변경 · ${targetText(rule)}` : '규칙 추가'} footer={null} onCancel={onClose} width={480} className="hr-modal pay-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{`${item.name} · 이전 값은 이력으로 남습니다.`}</p>
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{ targetType: 'ORG_UNIT', includeSubOrgs: true, gradeOrAbove: false, amount: rule?.current?.amount ?? undefined, effectiveFrom: dayjs().add(1, 'month').startOf('month'), release: false }}
        onFinish={(v) => save.mutate(v)}
      >
        {!rule && (
          <>
            <Form.Item name="targetType" label="대상">
              <Segmented
                options={[
                  { value: 'ORG_UNIT', label: '부서' },
                  { value: 'JOB_GRADE', label: '직급' },
                ]}
              />
            </Form.Item>
            {targetType === 'ORG_UNIT' ? (
              <div className="hr-form-row">
                <Form.Item name="orgUnitId" label="부서" rules={[{ required: true, message: '부서를 고르세요' }]}>
                  <Select showSearch optionFilterProp="label" options={org.options} popupMatchSelectWidth={false} />
                </Form.Item>
                <Form.Item name="includeSubOrgs" valuePropName="checked" label=" ">
                  <Checkbox>하위 부서 포함</Checkbox>
                </Form.Item>
              </div>
            ) : (
              <div className="hr-form-row">
                <Form.Item name="jobGradeId" label="직급" rules={[{ required: true, message: '직급을 고르세요' }]}>
                  <Select loading={grades.isLoading} options={(grades.data ?? []).map((g) => ({ value: g.id, label: g.name }))} />
                </Form.Item>
                <Form.Item name="gradeOrAbove" valuePropName="checked" label=" ">
                  <Checkbox>이 직급 이상</Checkbox>
                </Form.Item>
              </div>
            )}
          </>
        )}
        {rule && (
          <Form.Item name="release" valuePropName="checked">
            <Checkbox>이 규칙 해제(적용 시작일부터 기본 금액 · 다른 규칙을 따름)</Checkbox>
          </Form.Item>
        )}
        {!release && (
          <Form.Item name="amount" label="금액" extra="0원이면 이 대상은 받지 않습니다" rules={[{ required: true, message: '금액을 입력하세요' }]}>
            <InputNumber className="full-width" min={0} step={10000} suffix="원" {...money} />
          </Form.Item>
        )}
        <Form.Item name="effectiveFrom" label="적용 시작일" rules={[{ required: true, message: '적용 시작일을 고르세요' }]}>
          <DatePicker className="full-width" format="YYYY-MM-DD" />
        </Form.Item>
        <p className="hr-notice">귀속 월 말일에 유효한 규칙으로 정산합니다. 이미 확정된 급여는 바뀌지 않습니다.</p>
        <ModalFoot onCancel={onClose} busy={save.isPending} />
      </Form>
    </Modal>
  );
}
