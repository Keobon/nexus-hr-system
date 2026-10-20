// 회사 설정 › 계정 관리(F-AUTH-04 · 06) — Figma Admin_accountlist(384:8545) · _rolechange(384:9222) · _popup(384:10323) · _popup(error)(384:9604)
// 역할 변경 · 활성/비활성 → PATCH(바꾼 필드만, 바로 반영). 잠금 해제 · 비밀번호 초기화(임시 비밀번호는 한 번만 보여 준다)
import { useMemo, useState } from 'react';
import { Form, Input, Modal, Pagination, Select, Spin, Switch } from 'antd';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meKey } from '../../../../api/auth';
import { accountsKey, getAccounts, getRoles, resetPassword, rolesKey, unlockAccount, updateAccount } from '../../../../api/roles';
import { useNotify } from '../../../../components/common/useNotify';
import { errorMessage } from '../../../../constants/errors';
import { ConfirmModal, PageHead } from '../../../hr/parts';
import '../admin.css';

const PAGE_SIZE = 20;
const YES_NO = (yes, no) => [{ value: 'all', label: '전체' }, { value: 'true', label: yes }, { value: 'false', label: no }];
const shortTime = (iso) => (iso ? `${iso.slice(5, 10)} ${iso.slice(11, 16)}` : '-');

export default function AccountsPage() {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState({ roleId: 'all', active: 'all', locked: 'all', keyword: '' });
  const [keywordInput, setKeywordInput] = useState('');
  const [page, setPage] = useState(0);
  const [roleTarget, setRoleTarget] = useState(null);
  const [activeTarget, setActiveTarget] = useState(null); // { row, active }
  const [resetTarget, setResetTarget] = useState(null);
  const [tempPw, setTempPw] = useState(null); // { row, password }

  const roles = useQuery({ queryKey: rolesKey, queryFn: getRoles });
  const params = {
    roleId: filter.roleId === 'all' ? undefined : filter.roleId,
    active: filter.active === 'all' ? undefined : filter.active,
    locked: filter.locked === 'all' ? undefined : filter.locked,
    keyword: filter.keyword || undefined,
    page,
    size: PAGE_SIZE,
  };
  const list = useQuery({ queryKey: accountsKey(params), queryFn: () => getAccounts(params), placeholderData: keepPreviousData });
  const rows = list.data?.content ?? [];

  // 역할 배지 색 — 시스템 역할은 순서대로(최고 관리자 · 인사 담당 · 경영진), 회사가 만든 역할은 노랑
  const tone = useMemo(() => {
    const m = {};
    let i = 0;
    (roles.data ?? []).forEach((r) => { m[r.id] = r.isSystem ? (i < 3 ? `is-c${i}` : '') : 'is-custom'; if (r.isSystem) i += 1; });
    return m;
  }, [roles.data]);

  const change = (patch) => {
    setPage(0);
    setFilter((f) => ({ ...f, ...patch }));
  };
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['accounts'] });
    queryClient.invalidateQueries({ queryKey: rolesKey }); // 배정 인원
  };

  const unlock = async (row) => {
    try {
      await unlockAccount(row.employeeId);
      notify.success(`${row.name} 계정의 잠금을 풀었습니다`);
      refresh();
    } catch (err) { notify.error(err); }
  };

  return (
    <div className="hr">
      <PageHead title="계정 관리" desc="직원 계정에 역할을 지정하고, 활성 · 잠금 · 비밀번호를 관리합니다. 바꾸면 바로 반영됩니다." />

      <div className="hr-filter">
        <div className="hr-field">
          <label>역할</label>
          <Select className="hr-select" style={{ width: '9.25rem' }} value={filter.roleId} onChange={(v) => change({ roleId: v })}
            options={[{ value: 'all', label: '전체' }, ...(roles.data ?? []).map((r) => ({ value: r.id, label: r.name }))]} />
        </div>
        <div className="hr-field">
          <label>활성</label>
          <Select className="hr-select" style={{ width: '8rem' }} value={filter.active} onChange={(v) => change({ active: v })} options={YES_NO('활성', '비활성')} />
        </div>
        <div className="hr-field">
          <label>잠김</label>
          <Select className="hr-select" style={{ width: '8rem' }} value={filter.locked} onChange={(v) => change({ locked: v })} options={YES_NO('잠김', '정상')} />
        </div>
        <div className="hr-field">
          <label>검색</label>
          <Input style={{ width: '18.75rem', height: '2.25rem' }} placeholder="이름 · 사원번호 · 이메일" value={keywordInput} allowClear
            onChange={(e) => { setKeywordInput(e.target.value); if (!e.target.value) change({ keyword: '' }); }}
            onPressEnter={() => change({ keyword: keywordInput.trim() })} />
        </div>
        <button type="button" className="hr-btn is-14" style={{ width: '4.125rem' }} onClick={() => change({ keyword: keywordInput.trim() })}>검색</button>
        <span className="ad-total">전체 <strong>{list.data?.totalElements ?? 0}</strong>명</span>
      </div>

      <div className="hr-table">
        <div className="hr-tr is-head">
          <span style={{ width: '7.25rem' }}>사원번호</span>
          <span style={{ width: '4.75rem' }}>이름</span>
          <span style={{ width: '12.5rem' }}>이메일</span>
          <span style={{ width: '5.25rem' }}>소속</span>
          <span style={{ width: '6.25rem' }}>역할</span>
          <span style={{ width: '3.75rem' }}>활성</span>
          <span style={{ width: '3.25rem' }}>잠김</span>
          <span style={{ width: '6rem' }}>마지막 로그인</span>
          <span style={{ flex: '1 0 0', textAlign: 'right' }}>작업</span>
        </div>
        {list.isLoading ? (
          <div className="hr-tr is-empty"><Spin /></div>
        ) : list.error ? (
          <div className="hr-tr is-empty">{errorMessage(list.error)}</div>
        ) : rows.length === 0 ? (
          <div className="hr-tr is-empty">조건에 맞는 계정이 없습니다</div>
        ) : rows.map((r) => (
          <div key={r.employeeId} className={`hr-tr${r.active ? '' : ' is-dim'}`}>
            <span style={{ width: '7.25rem' }}>{r.employeeNo}</span>
            <span className="hr-strong" style={{ width: '4.75rem' }}>{r.name}</span>
            <span style={{ width: '12.5rem' }} title={r.email}>{r.email}</span>
            <span style={{ width: '5.25rem', color: '#64748b' }}>{r.orgUnitName}</span>
            <span style={{ width: '6.25rem' }}><span className={`ad-role-badge ${tone[r.roleId] ?? ''}`}>{r.roleName}</span></span>
            <span style={{ width: '3.75rem' }}>
              <Switch size="small" checked={r.active} aria-label={`${r.name} 활성`} onChange={(v) => setActiveTarget({ row: r, active: v })} />
            </span>
            <span style={{ width: '3.25rem' }}>{r.locked ? <span className="ad-locked">잠김</span> : '-'}</span>
            <span style={{ width: '6rem' }}>{shortTime(r.lastLoginAt)}</span>
            <span className="ad-mini-row" style={{ flex: '1 0 0', paddingRight: 0 }}>
              <button type="button" className="ad-mini" onClick={() => setRoleTarget(r)}>역할변경</button>
              <button type="button" className="ad-mini" onClick={() => setActiveTarget({ row: r, active: !r.active })}>{r.active ? '비활성화' : '활성화'}</button>
              {r.locked && <button type="button" className="ad-mini is-on" onClick={() => unlock(r)}>잠금해제</button>}
              <button type="button" className="ad-mini" onClick={() => setResetTarget(r)}>비밀번호초기화</button>
            </span>
          </div>
        ))}
      </div>

      <div className="ad-bar">
        <span className="hr-note">＊퇴직한 직원의 계정은 자동으로 비활성화됩니다</span>
        {(list.data?.totalPages ?? 0) > 1 && (
          <Pagination size="small" current={page + 1} pageSize={PAGE_SIZE} total={list.data.totalElements} showSizeChanger={false} onChange={(p) => setPage(p - 1)} />
        )}
      </div>

      {roleTarget && <RoleModal row={roleTarget} roles={roles.data ?? []} onClose={() => setRoleTarget(null)} onDone={refresh} />}

      <ConfirmModal open={!!activeTarget} danger={activeTarget && !activeTarget.active}
        title={activeTarget?.active ? '계정 활성화' : '계정 비활성화'} okText={activeTarget?.active ? '활성화' : '비활성화'}
        desc={activeTarget ? (activeTarget.active
          ? `${activeTarget.row.name}님의 계정을 다시 쓸 수 있게 합니다. 재직 중이면 승인자 재지정 표시도 지워집니다.`
          : `${activeTarget.row.name}님은 바로 로그인할 수 없게 됩니다. 이 사람이 승인자인 대기 건은 승인선 › 승인자 재지정 필요에 표시됩니다.`) : ''}
        onClose={() => setActiveTarget(null)}
        onConfirm={async () => {
          await updateAccount(activeTarget.row.employeeId, { active: activeTarget.active });
          refresh();
          queryClient.invalidateQueries({ queryKey: meKey }); // 재지정 필요 배지
          queryClient.invalidateQueries({ queryKey: ['approvals'] });
          notify.success(`${activeTarget.row.name} 계정을 ${activeTarget.active ? '활성화' : '비활성화'}했습니다`);
        }} />

      <ConfirmModal open={!!resetTarget} title="비밀번호 초기화" okText="초기화"
        desc={resetTarget ? `${resetTarget.name}님의 비밀번호를 임시 비밀번호로 바꿉니다. 지금 비밀번호는 더 쓸 수 없고, 다음 로그인 때 새 비밀번호로 바꿔야 합니다.` : ''}
        onClose={() => setResetTarget(null)}
        onConfirm={async () => {
          const res = await resetPassword(resetTarget.employeeId);
          setTempPw({ row: resetTarget, password: res.temporaryPassword });
        }} />

      <Modal open={!!tempPw} title="임시 비밀번호가 발급되었습니다" footer={null} closable={false} mask={{ closable: false }} keyboard={false}
        width={460} className="hr-modal" centered>
        {tempPw && (
          <>
            <p className="hr-modal-sub">{`${tempPw.row.name} (${tempPw.row.employeeNo}) · ${tempPw.row.email}`}</p>
            <div className="ad-pw">
              <span className="k">임시 비밀번호</span>
              <div className="v">{tempPw.password}</div>
              <button type="button" className="ad-mini"
                onClick={() => navigator.clipboard?.writeText(tempPw.password).then(() => notify.success('복사했습니다'))}>복사</button>
            </div>
            <p className="ad-warn" style={{ marginTop: '0.875rem' }}>
              이 창을 닫으면 <strong>다시 볼 수 없습니다.</strong> 직원에게 안전하게 전달해 주세요. 직원은 첫 로그인 때 비밀번호를 바꿔야 합니다.
            </p>
            <div className="hr-modal-foot">
              <button type="button" className="hr-btn" onClick={() => setTempPw(null)}>확인</button>
            </div>
          </>
        )}
      </Modal>
    </div>
  );
}

/** 역할 변경 — 저장하면 즉시 반영(다시 로그인 필요 없음). 마지막 최고 관리자 강등은 창 안에 오류 */
function RoleModal({ row, roles, onClose, onDone }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const save = useMutation({
    mutationFn: (roleId) => updateAccount(row.employeeId, { roleId }),
    onSuccess: (data) => {
      onDone();
      queryClient.invalidateQueries({ queryKey: meKey });
      notify.success(`${data.name}님의 역할을 ${data.roleName}(으)로 바꿨습니다`);
      onClose();
    },
    onError: (err) => form.setFields([{ name: 'roleId', errors: [`${errorMessage(err)}${err?.code === 'LAST_SUPER_ADMIN' ? ' (LAST_SUPER_ADMIN)' : ''}`] }]),
  });
  return (
    <Modal open title="역할 변경" footer={null} onCancel={onClose} width={460} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">저장하면 즉시 반영됩니다. 다시 로그인할 필요가 없습니다.</p>
      <div className="ad-target">
        대상<strong>{`${row.name} (${row.employeeNo})`}</strong>현재 역할<strong>{row.roleName}</strong>
      </div>
      <Form form={form} layout="vertical" requiredMark initialValues={{ roleId: row.roleId }}
        onFinish={(v) => (v.roleId === row.roleId ? onClose() : save.mutate(v.roleId))}>
        <Form.Item name="roleId" label="새 역할" rules={[{ required: true, message: '역할을 고르세요' }]}>
          <Select options={roles.map((r) => ({ value: r.id, label: r.name }))} />
        </Form.Item>
        <div className="hr-modal-foot">
          <button type="button" className="hr-btn is-secondary" onClick={onClose} disabled={save.isPending}>취소</button>
          <button type="submit" className="hr-btn" disabled={save.isPending}>{save.isPending ? '저장 중…' : '변경 저장'}</button>
        </div>
      </Form>
    </Modal>
  );
}
