// 회사 설정 › 조직(F-ORG-01) — Figma HR_departments(부서 탭) · HR_divisions(본부 탭) · MODAL_department_edit · MODAL_division_edit
// 조직은 단계 수 제한이 없는 트리라서, 회사 바로 아래(1단계)와 그 아래(2단계 이상)를 두 탭으로 나눠 보여 준다.
// 탭 이름은 그 단계 조직의 levelName(예: 본부 · 팀)을 쓴다.
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Form, Input, InputNumber, Modal, Select, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  activateOrgUnit,
  createOrgUnit,
  deactivateOrgUnit,
  flattenOrgTree,
  getOrgMembers,
  getOrgTreeAll,
  moveOrgUnit,
  orgMembersKey,
  orgTreeAllKey,
  updateOrgUnit,
} from '../../api/org';
import { applyFieldErrors } from '../../components/common/formErrors';
import { useNotify } from '../../components/common/useNotify';
import { errorMessage } from '../../constants/errors';
import { formatMoney } from '../../utils/format';
import { ActiveBadge, ConfirmModal, ModalFoot, PageHead, TabBar } from '../hr/parts';

const commonLevel = (list, fallback) => {
  const counts = new Map();
  list.forEach((o) => o.levelName && counts.set(o.levelName, (counts.get(o.levelName) ?? 0) + 1));
  return [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? fallback;
};

export default function OrgSettingsPage() {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [sp, setSp] = useSearchParams();
  const tab = sp.get('tab') === 'upper' ? 'upper' : 'lower';
  const tree = useQuery({ queryKey: orgTreeAllKey, queryFn: getOrgTreeAll });
  const flat = useMemo(() => flattenOrgTree(tree.data), [tree.data]);
  const root = flat.find((o) => o.depth === 0);
  const upper = flat.filter((o) => o.depth === 1);
  const lower = flat.filter((o) => o.depth >= 2);
  const upperLabel = commonLevel(upper, '본부');
  const lowerLabel = commonLevel(lower, '부서');
  const [edit, setEdit] = useState(null); // { unit?, level: 'upper' | 'lower' }
  const [deactivating, setDeactivating] = useState(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['org-units'] });
  const activate = async (u) => {
    try { await activateOrgUnit(u.id); notify.success(`${u.name}을(를) 다시 활성화했습니다`); refresh(); } catch (err) { notify.error(err); }
  };
  const missingLead = lower.filter((o) => o.isActive && !o.lead);

  return (
    <div className="hr">
      <PageHead title="조직 관리" desc={`${upperLabel}와(과) ${lowerLabel}을(를) 등록·수정하고 조직장과 활성 상태를 관리합니다.`}>
        <button type="button" className="hr-btn" onClick={() => setEdit({ level: tab })}>{`${tab === 'upper' ? upperLabel : lowerLabel} 등록`}</button>
      </PageHead>

      <div className="hr-table">
        <TabBar value={tab} onChange={(v) => setSp(v === 'upper' ? { tab: v } : {})}
          tabs={[{ value: 'upper', label: upperLabel }, { value: 'lower', label: lowerLabel }]} />
        {tree.isLoading ? (
          <div className="hr-tr is-empty"><Spin /></div>
        ) : tree.error ? (
          <div className="hr-tr is-empty">{errorMessage(tree.error)}</div>
        ) : tab === 'lower' ? (
          <>
            <div className="hr-toolbar">
              <span>{`총 ${lower.length}개 ${lowerLabel}`}</span>
              {missingLead.length > 0 && (
                <span className="hr-badge is-alert">{`⚠ 조직장 미지정 ${missingLead.length}개 ${lowerLabel} — 해당 ${lowerLabel}원의 휴가 신청이 승인자를 찾지 못할 수 있습니다`}</span>
              )}
            </div>
            <div className="hr-tr is-head">
              <span style={{ width: '15rem' }}>{`${lowerLabel}명`}</span>
              <span style={{ width: '12.5rem' }}>{`소속 ${upperLabel}`}</span>
              <span style={{ width: '10rem' }}>조직장</span>
              <span style={{ width: '6.25rem' }}>현원</span>
              <span style={{ width: '8.75rem' }}>월 인건비 예산</span>
              <span style={{ width: '8.75rem' }}>활성 상태</span>
              <span>관리</span>
            </div>
            {lower.length === 0 && <div className="hr-tr is-empty">{`${lowerLabel}이(가) 없습니다`}</div>}
            {lower.map((u) => (
              <div key={u.id} className="hr-tr">
                <span className="hr-strong" style={{ width: '15rem' }}>{u.name}</span>
                <span style={{ width: '12.5rem' }}>{u.parent?.path.join(' › ') ?? '-'}</span>
                <span style={{ width: '10rem' }}>{u.lead?.name ?? (u.isActive ? <span className="hr-missing">⚠ 미지정</span> : '-')}</span>
                <span style={{ width: '6.25rem' }}>{`${u.directCount}명`}</span>
                <span style={{ width: '8.75rem' }}>{u.monthlyBudget === undefined ? '-' : formatMoney(u.monthlyBudget)}</span>
                <span style={{ width: '8.75rem' }}><ActiveBadge active={u.isActive} /></span>
                <span className="hr-row-actions">
                  <button type="button" className="hr-link" onClick={() => setEdit({ unit: u, level: 'lower' })}>수정</button>
                  {u.isActive
                    ? <button type="button" className="hr-link is-red" onClick={() => setDeactivating(u)}>비활성화</button>
                    : <button type="button" className="hr-link" onClick={() => activate(u)}>활성화</button>}
                </span>
              </div>
            ))}
          </>
        ) : (
          <>
            <div className="hr-toolbar"><span>{`총 ${upper.length}개 ${upperLabel}`}</span></div>
            <div className="hr-tr is-head">
              <span style={{ width: '12.5rem' }}>{`${upperLabel}명`}</span>
              <span style={{ width: '22rem' }}>{`소속 ${lowerLabel}`}</span>
              <span style={{ width: '6.25rem' }}>{`${lowerLabel} 수`}</span>
              <span style={{ width: '6.25rem' }}>현원</span>
              <span style={{ width: '8.75rem' }}>월 인건비 예산</span>
              <span style={{ width: '6.25rem' }}>활성 상태</span>
              <span>관리</span>
            </div>
            {upper.length === 0 && <div className="hr-tr is-empty">{`${upperLabel}이(가) 없습니다`}</div>}
            {upper.map((u) => {
              const kids = (u.children ?? []).filter((c) => c.isActive);
              return (
                <div key={u.id} className="hr-tr">
                  <span className="hr-strong" style={{ width: '12.5rem' }}>{u.name}</span>
                  <span style={{ width: '22rem' }}>{kids.map((c) => c.name).join(' · ') || '-'}</span>
                  <span style={{ width: '6.25rem' }}>{`${kids.length}개`}</span>
                  <span style={{ width: '6.25rem' }}>{`${u.totalCount}명`}</span>
                  <span style={{ width: '8.75rem' }}>{u.monthlyBudget === undefined ? '-' : formatMoney(u.monthlyBudget)}</span>
                  <span style={{ width: '6.25rem' }}><ActiveBadge active={u.isActive} /></span>
                  <span className="hr-row-actions">
                    <button type="button" className="hr-link" onClick={() => setEdit({ unit: u, level: 'upper' })}>수정</button>
                    {u.isActive
                      ? <button type="button" className="hr-link is-red" onClick={() => setDeactivating(u)}>비활성화</button>
                      : <button type="button" className="hr-link" onClick={() => activate(u)}>활성화</button>}
                  </span>
                </div>
              );
            })}
          </>
        )}
      </div>
      <p className="hr-note">※ 쓰인 적 있는 조직은 지우지 않고 비활성화합니다. 소속 직원이나 활성 하위 조직이 있으면 비활성화할 수 없습니다.</p>

      {edit && (
        <OrgUnitModal edit={edit} flat={flat} root={root} levelLabel={edit.level === 'upper' ? upperLabel : lowerLabel}
          onClose={() => setEdit(null)} onSaved={refresh} />
      )}
      <ConfirmModal open={!!deactivating} danger okText="비활성화"
        desc={`${deactivating?.name}을(를) 비활성화합니다. 소속 직원이나 활성 하위 조직이 있으면 비활성화할 수 없습니다. 계속하시겠습니까?`}
        onClose={() => setDeactivating(null)}
        onConfirm={async () => { await deactivateOrgUnit(deactivating.id); notify.success(`${deactivating.name} 비활성화`); refresh(); }} />
    </div>
  );
}

/** 등록 · 수정 창. 상위 조직이 바뀌면 이동 API 를 먼저 부른다(하위 조직 아래로는 서버가 ORG_CYCLE) */
function OrgUnitModal({ edit, flat, root, levelLabel, onClose, onSaved }) {
  const notify = useNotify();
  const [form] = Form.useForm();
  const u = edit.unit;
  const isNew = !u;
  const isUpper = edit.level === 'upper';
  const members = useQuery({ queryKey: orgMembersKey(u?.id), queryFn: () => getOrgMembers(u.id), enabled: !!u });

  // 자기 자신과 그 하위는 상위 조직 후보에서 뺀다
  const blocked = new Set();
  const block = (node) => { blocked.add(node.id); (node.children ?? []).forEach(block); };
  if (u) block(u);
  const parentOptions = flat.filter((o) => o.depth >= 1 && o.isActive && !blocked.has(o.id)).map((o) => ({ value: o.id, label: o.path.join(' › ') }));

  const save = useMutation({
    mutationFn: async (v) => {
      const budget = v.monthlyBudget ?? null;
      if (isNew) {
        return createOrgUnit({ parentId: isUpper ? root.id : v.parentId, name: v.name.trim(), levelName: levelLabel, monthlyBudget: budget });
      }
      if (!isUpper && v.parentId !== u.parent?.id) await moveOrgUnit(u.id, v.parentId);
      return updateOrgUnit(u.id, { name: v.name.trim(), monthlyBudget: budget, leadEmployeeId: v.leadEmployeeId ?? null });
    },
    onSuccess: (data) => {
      notify.success(isNew ? `${data.name}을(를) 등록했습니다` : '조직 정보를 저장했습니다');
      onSaved();
      onClose();
    },
    onError: (err) => { if (!applyFieldErrors(form, err)) notify.error(err); },
  });

  return (
    <Modal open title={`${levelLabel} 등록·수정`} footer={null} onCancel={onClose} width={440} className="hr-modal" destroyOnHidden centered>
      <div style={{ height: '1.25rem' }} />
      <Form form={form} layout="vertical" requiredMark={false} onFinish={(v) => save.mutate(v)}
        initialValues={{ name: u?.name, parentId: u?.parent?.depth >= 1 ? u.parent.id : undefined, monthlyBudget: u?.monthlyBudget ?? null, leadEmployeeId: u?.lead?.id }}>
        <Form.Item name="name" label={`${levelLabel}명`} rules={[{ required: true, whitespace: true, message: '이름을 입력하세요' }]}>
          <Input maxLength={50} placeholder={isUpper ? '예: 개발본부' : '예: 백엔드팀'} />
        </Form.Item>
        {!isUpper && (
          <Form.Item name="parentId" label="소속 상위 조직" rules={[{ required: true, message: '상위 조직을 선택하세요' }]}
            extra={u ? '하위 조직 아래로는 옮길 수 없어 목록에서 빠져 있습니다' : undefined}>
            <Select placeholder="상위 조직을 선택하세요" options={parentOptions} showSearch optionFilterProp="label" />
          </Form.Item>
        )}
        <Form.Item name="monthlyBudget" label="월 인건비 예산 (선택)">
          <InputNumber className="full-width" min={0} step={1000000} addonAfter="원" placeholder="예: 40,000,000"
            formatter={(v) => (v === undefined || v === null || v === '' ? '' : Number(v).toLocaleString('ko-KR'))}
            parser={(v) => (v ? v.replace(/[^0-9]/g, '') : '')} />
        </Form.Item>
        <Form.Item name="leadEmployeeId" label="조직장"
          extra={<span className="hr-hint is-warn">{isNew ? '※ 조직장은 등록 후 이 조직에 직원을 발령하고 지정합니다.' : '※ 조직장이 비어 있으면 이 조직 직원의 휴가 신청이 승인자를 찾지 못할 수 있습니다.'}</span>}>
          <Select allowClear placeholder="미지정" disabled={isNew} loading={members.isLoading}
            options={(members.data ?? []).map((m) => ({ value: m.id, label: `${m.name}${m.jobGradeName ? ` (${m.jobGradeName})` : ''}` }))} />
        </Form.Item>
        <ModalFoot onCancel={onClose} busy={save.isPending} />
      </Form>
    </Modal>
  );
}
