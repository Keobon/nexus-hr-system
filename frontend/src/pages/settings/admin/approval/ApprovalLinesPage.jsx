// 회사 설정 › 승인선(F-APPR-01 · 02) — Figma 없음, 회사 설정 화면과 같은 조각으로
// 탭: 승인선(업무 종류별 목록 · 단계 편집) · 승인자 재지정 필요(배지 — 승인자가 퇴직 · 비활성이 된 대기 단계)
import { useState } from 'react';
import { Form, Input, InputNumber, Modal, Pagination, Select, Spin, Switch } from 'antd';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meKey } from '../../../../api/auth';
import { approvalLinesKey, createApprovalLine, deleteApprovalLine, getApprovalLines, getReassignNeeded, reassignApprover, reassignKey, updateApprovalLine } from '../../../../api/approvalLines';
import { jobTitlesApi } from '../../../../api/orgSettings';
import { getRoles, rolesKey } from '../../../../api/roles';
import { useMe } from '../../../../auth/useMe';
import { applyFieldErrors } from '../../../../components/common/formErrors';
import { useNotify } from '../../../../components/common/useNotify';
import { errorMessage } from '../../../../constants/errors';
import { labelOf } from '../../../../constants/labels';
import { ConfirmModal, PageHead, TabBar } from '../../../hr/parts';
import EmployeeSelect from '../EmployeeSelect';
import { AdTabs, useTab } from '../parts';

const WORK_TYPES = ['LEAVE', 'OVERTIME', 'BUSINESS_TRIP', 'TRIP_EXPENSE']; // 휴가 취소는 승인선이 없다
const APPROVER_TYPES = ['ORG_LEAD', 'ORG_LEAD_UP', 'JOB_TITLE', 'EMPLOYEE'];

/** 단계 한 줄 설명 — "소속 조직장" · "2단계 위 조직장" · "직책: 본부장" · "김민수" */
export function stepText(s) {
  switch (s.approverType) {
    case 'ORG_LEAD': return '소속 조직장';
    case 'ORG_LEAD_UP': return `${s.upLevels ?? 'N'}단계 위 조직장`;
    case 'JOB_TITLE': return `직책: ${s.jobTitleName ?? '-'}`;
    case 'EMPLOYEE': return s.employeeName ?? '특정 직원';
    default: return '-';
  }
}

const condText = (l) => (l.isDefault ? '기본 승인선 · 조건 없음(다른 승인선에 맞지 않으면 이것)'
  : l.condJobTitleName ? `신청자 직책이 ${l.condJobTitleName}일 때`
    : l.condRoleName ? `신청자 역할이 ${l.condRoleName}일 때` : '-');

export default function ApprovalLinesPage() {
  const { me } = useMe();
  const [tab, setTab] = useTab(['lines', 'reassign']);
  const count = me?.todos?.reassignNeeded ?? 0;
  return (
    <div className="hr">
      <PageHead title="승인선" desc="업무 종류별로 누가 몇 단계로 승인할지 정합니다. 신청자의 직책 · 역할 조건에 맞는 승인선이 먼저 쓰입니다." />
      <AdTabs tabs={[{ value: 'lines', label: '승인선' }, { value: 'reassign', label: '승인자 재지정 필요', count }]} value={tab} onChange={setTab} />
      {tab === 'lines' ? <LinesTab /> : <ReassignTab />}
    </div>
  );
}

function LinesTab() {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [workType, setWorkType] = useState('LEAVE');
  const list = useQuery({ queryKey: approvalLinesKey(workType), queryFn: () => getApprovalLines(workType) });
  const [edit, setEdit] = useState(null);
  const [removing, setRemoving] = useState(null);
  const rows = list.data ?? [];

  return (
    <>
      <div className="ad-bar">
        <TabBar value={workType} onChange={setWorkType} tabs={WORK_TYPES.map((w) => ({ value: w, label: labelOf('approvalWorkType', w) }))} />
        <button type="button" className="ad-btn is-lg" onClick={() => setEdit({ workType })}>+ 승인선 추가</button>
      </div>
      {list.isLoading ? <Spin /> : list.error ? <p className="hr-notice is-error">{errorMessage(list.error)}</p> : (
        <div className="ad-lines">
          {rows.length === 0 && <p className="hr-note">등록된 승인선이 없습니다.</p>}
          {rows.map((l) => (
            <div key={l.id} className={`ad-line${l.isActive ? '' : ' is-off'}`}>
              <div className="ad-line-info">
                <strong>
                  {l.name}
                  {l.isDefault && <span className="ad-tag">기본</span>}
                  {!l.isActive && <span className="hr-badge is-gray">비활성</span>}
                </strong>
                <span>{condText(l)}</span>
                {!l.isDefault && <span>{`우선순위 ${l.priority}`}</span>}
              </div>
              <div className="ad-flow">
                <span className="hr-hint" style={{ margin: 0 }}>신청</span>
                {[...l.steps].sort((a, b) => a.stepOrder - b.stepOrder).map((s) => (
                  <span key={s.stepOrder} style={{ display: 'contents' }}>
                    <span className="ad-arrow">→</span>
                    <span className="step">{`${s.stepOrder}. ${stepText(s)}`}</span>
                  </span>
                ))}
              </div>
              <span className="ad-mini-row">
                <button type="button" className="ad-mini" onClick={() => setEdit(l)}>수정</button>
                <button type="button" className="ad-mini is-red" disabled={l.isDefault} title={l.isDefault ? '기본 승인선은 삭제할 수 없습니다' : undefined}
                  onClick={() => setRemoving(l)}>삭제</button>
              </span>
            </div>
          ))}
        </div>
      )}
      <p className="hr-note">※ 승인선을 바꿔도 진행 중인 건에는 영향이 없습니다. 신청할 때의 승인선으로 끝까지 진행됩니다.</p>

      {edit && <LineModal line={edit} onClose={() => setEdit(null)} />}
      <ConfirmModal open={!!removing} danger okText="삭제" title="승인선 삭제"
        desc={removing ? `'${removing.name}' 승인선을 삭제합니다. 이미 쓰인 승인선이면 지우지 않고 비활성화합니다.` : ''}
        onClose={() => setRemoving(null)}
        onConfirm={async () => {
          const res = await deleteApprovalLine(removing.id);
          queryClient.invalidateQueries({ queryKey: ['approval-lines'] });
          notify.success(res?.result === 'DEACTIVATED' ? '사용 중이라 비활성화했습니다' : '승인선을 삭제했습니다');
        }} />
    </>
  );
}

const condKindOf = (l) => (l.condJobTitleId ? 'JOB_TITLE' : l.condRoleId ? 'ROLE' : 'NONE');

function LineModal({ line, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const isNew = !line.id;
  const isDefault = !!line.isDefault;
  const titles = useQuery({ queryKey: jobTitlesApi.key(true), queryFn: () => jobTitlesApi.list(true) });
  const roles = useQuery({ queryKey: rolesKey, queryFn: getRoles });
  const condKind = Form.useWatch('condKind', form) ?? condKindOf(line);
  const titleOptions = (titles.data ?? []).map((t) => ({ value: t.id, label: t.name }));

  const save = useMutation({
    mutationFn: (body) => (isNew ? createApprovalLine(body) : updateApprovalLine(line.id, body)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approval-lines'] });
      notify.success(isNew ? '승인선을 추가했습니다' : '승인선을 저장했습니다');
      onClose();
    },
    onError: (err) => { if (!applyFieldErrors(form, err)) notify.error(err); },
  });

  const onFinish = (v) => save.mutate({
    name: v.name.trim(),
    workType: line.workType,
    condJobTitleId: v.condKind === 'JOB_TITLE' ? v.condJobTitleId : null,
    condRoleId: v.condKind === 'ROLE' ? v.condRoleId : null,
    priority: v.priority,
    isActive: v.isActive,
    steps: v.steps.map((s, i) => ({
      stepOrder: i + 1,
      approverType: s.approverType,
      upLevels: s.approverType === 'ORG_LEAD_UP' ? s.upLevels : null,
      jobTitleId: s.approverType === 'JOB_TITLE' ? s.jobTitleId : null,
      employeeId: s.approverType === 'EMPLOYEE' ? s.employeeId : null,
    })),
  });

  const init = {
    name: line.name,
    condKind: isNew ? 'JOB_TITLE' : condKindOf(line),
    condJobTitleId: line.condJobTitleId ?? undefined,
    condRoleId: line.condRoleId ?? undefined,
    priority: line.priority ?? 10,
    isActive: line.isActive ?? true,
    steps: line.steps ? [...line.steps].sort((a, b) => a.stepOrder - b.stepOrder) : [{ approverType: 'ORG_LEAD' }],
  };

  return (
    <Modal open title={`${labelOf('approvalWorkType', line.workType)} 승인선 ${isNew ? '추가' : '수정'}`} footer={null} onCancel={onClose}
      width={760} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">
        {isDefault ? '기본 승인선은 조건 · 비활성을 바꿀 수 없고 단계만 고칠 수 있습니다.' : '조건(신청자의 직책 또는 역할)이 같은 활성 승인선은 하나만 둘 수 있습니다. 바꿔도 진행 중인 건에는 영향이 없습니다.'}
      </p>
      <Form form={form} layout="vertical" requiredMark initialValues={init} onFinish={onFinish}>
        <div className="ad-grid" style={{ '--cols': 3, marginBottom: '1rem' }}>
          <Form.Item name="name" label="이름" className="ad-span-2" rules={[{ required: true, whitespace: true, message: '승인선 이름을 입력하세요' }]}>
            <Input maxLength={50} placeholder="예: 팀장 휴가" />
          </Form.Item>
          <Form.Item name="isActive" label="활성" valuePropName="checked">
            <Switch disabled={isDefault} />
          </Form.Item>
          {!isDefault && (
            <>
              <Form.Item name="condKind" label="적용 조건">
                <Select options={[{ value: 'JOB_TITLE', label: '신청자 직책' }, { value: 'ROLE', label: '신청자 역할' }]} />
              </Form.Item>
              {condKind === 'ROLE' ? (
                <Form.Item name="condRoleId" label="역할" rules={[{ required: true, message: '역할을 고르세요' }]}>
                  <Select options={(roles.data ?? []).map((r) => ({ value: r.id, label: r.name }))} placeholder="선택하세요" />
                </Form.Item>
              ) : (
                <Form.Item name="condJobTitleId" label="직책" rules={[{ required: true, message: '직책을 고르세요' }]}>
                  <Select options={titleOptions} placeholder="선택하세요" />
                </Form.Item>
              )}
              <Form.Item name="priority" label="우선순위" extra={<span className="hr-hint">숫자가 작을수록 먼저 맞춰 봅니다</span>}
                rules={[{ required: true, message: '우선순위를 입력하세요' }]}>
                <InputNumber precision={0} />
              </Form.Item>
            </>
          )}
          {isDefault && <Form.Item name="priority" hidden><InputNumber /></Form.Item>}
        </div>

        <Form.List name="steps">
          {(fields, { add, remove, move }) => (
            <div className="ad-steps">
              <span className="hr-label" style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>승인 단계(1~5단계)</span>
              {fields.map((f, i) => (
                <StepRow key={f.key} field={f} index={i} total={fields.length} form={form} titleOptions={titleOptions}
                  onUp={() => move(i, i - 1)} onDown={() => move(i, i + 1)} onRemove={() => remove(i)} />
              ))}
              {fields.length < 5 && (
                <button type="button" className="ad-mini" style={{ alignSelf: 'flex-start' }} onClick={() => add({ approverType: 'ORG_LEAD' })}>+ 단계 추가</button>
              )}
            </div>
          )}
        </Form.List>

        <div className="hr-modal-foot">
          <button type="button" className="hr-btn is-secondary" onClick={onClose} disabled={save.isPending}>취소</button>
          <button type="submit" className="hr-btn" disabled={save.isPending}>{save.isPending ? '저장 중…' : '저장'}</button>
        </div>
      </Form>
    </Modal>
  );
}

function StepRow({ field, index, total, form, titleOptions, onUp, onDown, onRemove }) {
  const type = Form.useWatch(['steps', field.name, 'approverType'], form);
  const employeeName = form.getFieldValue(['steps', field.name, 'employeeName']);
  return (
    <div className="ad-step">
      <span className="no">{index + 1}</span>
      <Form.Item name={[field.name, 'approverType']} rules={[{ required: true, message: '지정 방식을 고르세요' }]}>
        <Select options={APPROVER_TYPES.map((t) => ({ value: t, label: labelOf('approverType', t) }))} />
      </Form.Item>
      <div>
        {type === 'ORG_LEAD_UP' && (
          <Form.Item name={[field.name, 'upLevels']} rules={[{ required: true, message: '몇 단계 위인지 입력하세요' }]}>
            <InputNumber min={1} max={10} precision={0} suffix="단계 위 조직장" style={{ width: '11rem' }} />
          </Form.Item>
        )}
        {type === 'JOB_TITLE' && (
          <Form.Item name={[field.name, 'jobTitleId']} rules={[{ required: true, message: '직책을 고르세요' }]}>
            <Select options={titleOptions} placeholder="직책 선택" />
          </Form.Item>
        )}
        {type === 'EMPLOYEE' && (
          <Form.Item name={[field.name, 'employeeId']} rules={[{ required: true, message: '직원을 고르세요' }]}>
            <EmployeeSelect initialLabel={employeeName} />
          </Form.Item>
        )}
        {type === 'ORG_LEAD' && <span className="hr-hint">신청자가 속한 조직의 조직장(값 없음)</span>}
      </div>
      <span className="ops">
        <button type="button" className="ad-mini" disabled={index === 0} onClick={onUp} aria-label="위로">▲</button>
        <button type="button" className="ad-mini" disabled={index === total - 1} onClick={onDown} aria-label="아래로">▼</button>
        <button type="button" className="ad-mini is-red" disabled={total === 1} onClick={onRemove}>삭제</button>
      </span>
    </div>
  );
}

const PAGE_SIZE = 20;

function ReassignTab() {
  const [page, setPage] = useState(0);
  const params = { page, size: PAGE_SIZE };
  const list = useQuery({ queryKey: reassignKey(params), queryFn: () => getReassignNeeded(params), placeholderData: keepPreviousData });
  const rows = list.data?.content ?? [];
  const [target, setTarget] = useState(null);

  return (
    <>
      <p className="hr-note">승인자가 퇴직하거나 계정이 비활성이 된 대기 단계입니다. 오래된 순이며, 계정을 다시 활성화하면 원래 승인자가 그대로 처리합니다.</p>
      <div className="hr-table">
        <div className="hr-tr is-head">
          <span style={{ width: '6.5rem' }}>업무</span>
          <span style={{ width: '6.5rem' }}>신청자</span>
          <span style={{ flex: '1 0 0' }}>내용</span>
          <span style={{ width: '4.5rem' }}>단계</span>
          <span style={{ width: '5.5rem' }}>상태</span>
          <span style={{ width: '8rem' }}>현재 승인자</span>
          <span style={{ width: '4.5rem' }} />
        </div>
        {list.isLoading ? <div className="hr-tr is-empty"><Spin /></div>
          : list.error ? <div className="hr-tr is-empty">{errorMessage(list.error)}</div>
            : rows.length === 0 ? <div className="hr-tr is-empty">재지정할 건이 없습니다</div>
              : rows.map((r) => (
                <div key={r.stepId} className="hr-tr">
                  <span style={{ width: '6.5rem' }}>{labelOf('approvalWorkType', r.workType)}</span>
                  <span className="hr-strong" style={{ width: '6.5rem' }}>{r.applicantName}</span>
                  <span style={{ flex: '1 0 0' }}>{r.title}</span>
                  <span style={{ width: '4.5rem' }}>{`${r.stepOrder}단계`}</span>
                  <span style={{ width: '5.5rem' }}>{labelOf('approvalStepStatus', r.status)}</span>
                  <span style={{ width: '8rem', color: '#dc2626' }}>{r.approverName ?? '-'}</span>
                  <span style={{ width: '4.5rem' }}>
                    <button type="button" className="ad-mini is-on" onClick={() => setTarget(r)}>재지정</button>
                  </span>
                </div>
              ))}
      </div>
      {(list.data?.totalPages ?? 0) > 1 && (
        <div className="hr-pager">
          <Pagination size="small" current={page + 1} pageSize={PAGE_SIZE} total={list.data.totalElements} showSizeChanger={false} onChange={(p) => setPage(p - 1)} />
        </div>
      )}

      {target && <ReassignModal target={target} onClose={() => setTarget(null)} />}
    </>
  );
}

function ReassignModal({ target, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const save = useMutation({
    mutationFn: (approverId) => reassignApprover(target.stepId, approverId),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['approvals'] });
      queryClient.invalidateQueries({ queryKey: meKey });
      notify.success(`승인자를 ${data.approverName}(으)로 바꿨습니다`);
      onClose();
    },
    onError: (err) => { if (!applyFieldErrors(form, err)) notify.error(err); },
  });
  return (
    <Modal open title="승인자 재지정" footer={null} onCancel={onClose} width={480} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{`${target.applicantName} · ${target.title} · ${target.stepOrder}단계`}</p>
      <Form form={form} layout="vertical" requiredMark onFinish={(v) => save.mutate(v.approverId)}>
        <Form.Item name="approverId" label="새 승인자" extra={<span className="hr-hint">재직 중이고 계정이 활성인 직원만, 신청자 본인은 고를 수 없습니다</span>}
          rules={[{ required: true, message: '새 승인자를 고르세요' }]}>
          <EmployeeSelect excludeId={target.applicantId} />
        </Form.Item>
        <div className="hr-modal-foot">
          <button type="button" className="hr-btn is-secondary" onClick={onClose} disabled={save.isPending}>취소</button>
          <button type="submit" className="hr-btn" disabled={save.isPending}>{save.isPending ? '저장 중…' : '재지정'}</button>
        </div>
      </Form>
    </Modal>
  );
}
