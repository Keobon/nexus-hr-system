// 회사 설정 › 역할·권한(F-AUTH-05) — Figma Admin_role(384:8183) · Admin_role_popup(384:9984)
// 왼쪽 역할 목록, 오른쪽 권한 표(18행: 사용 · 코드 · 설명 · 관련 기능 · 범위). 저장은 확인 창을 거쳐 PUT(권한 목록 통째로 교체)
// 시스템 역할(isSystem)은 읽기 전용 — 서버도 SYSTEM_ROLE_READONLY 로 막는다
import { useEffect, useMemo, useState } from 'react';
import { Checkbox, Form, Input, Modal, Select, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meKey } from '../../../../api/auth';
import { createRole, deleteRole, getPermissions, getRole, getRoles, permissionsKey, roleKey, rolesKey, updateRole } from '../../../../api/roles';
import { applyFieldErrors } from '../../../../components/common/formErrors';
import { useNotify } from '../../../../components/common/useNotify';
import { errorMessage } from '../../../../constants/errors';
import { labelOf } from '../../../../constants/labels';
import { ConfirmModal, PageHead } from '../../../hr/parts';
import '../admin.css';

const NEW = 'new';

export default function RolesPage() {
  const roles = useQuery({ queryKey: rolesKey, queryFn: getRoles });
  const perms = useQuery({ queryKey: permissionsKey, queryFn: getPermissions });
  const [selected, setSelected] = useState(null);

  useEffect(() => {
    if (selected === null && roles.data?.length) setSelected(roles.data[0].id);
  }, [roles.data, selected]);

  return (
    <div className="hr">
      <PageHead title="역할·권한" desc="역할에 권한을 붙이고, 계정 관리에서 직원에게 역할을 지정합니다. 저장하면 바로 반영됩니다." />
      <div className="ad-split">
        <section className="ad-section ad-roles">
          <div className="ad-section-head">
            <h2 className="is-20" style={{ fontSize: '1rem' }}>역할 목록</h2>
            <button type="button" className="ad-btn" onClick={() => setSelected(NEW)}>+ 역할 추가</button>
          </div>
          {roles.isLoading ? <Spin /> : roles.error ? <p className="hr-notice is-error">{errorMessage(roles.error)}</p> : (
            <>
              {selected === NEW && (
                <button type="button" className="ad-role is-on">
                  <div className="row"><strong>새 역할</strong><span className="ad-tag is-custom">회사 지정</span></div>
                  <span>이름과 권한을 정하고 저장하세요</span>
                </button>
              )}
              {roles.data.map((r) => (
                <button key={r.id} type="button" className={`ad-role${selected === r.id ? ' is-on' : ''}`} onClick={() => setSelected(r.id)}>
                  <div className="row">
                    <strong>{r.name}</strong>
                    <span className={`ad-tag${r.isSystem ? '' : ' is-custom'}`}>{r.isSystem ? '시스템' : '회사 지정'}</span>
                  </div>
                  <div className="row">
                    <span>{r.description || '설명 없음'}</span>
                    <span className="count">{`${r.accountCount}명`}</span>
                  </div>
                </button>
              ))}
              <p className="hr-hint" style={{ fontSize: '0.6875rem' }}>시스템 역할은 수정·삭제할 수 없습니다</p>
            </>
          )}
        </section>
        <section className="ad-section ad-editor">
          {perms.isLoading || selected === null ? <Spin /> : perms.error ? <p className="hr-notice is-error">{errorMessage(perms.error)}</p> : (
            <RoleEditor key={selected} roleId={selected} perms={perms.data} onSelect={setSelected} />
          )}
        </section>
      </div>
    </div>
  );
}

/** 권한 표 값: { [code]: scope | undefined } */
const toMap = (list = []) => Object.fromEntries(list.map((p) => [p.code, p.scope]));

function RoleEditor({ roleId, perms, onSelect }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const isNew = roleId === NEW;
  const role = useQuery({ queryKey: roleKey(roleId), queryFn: () => getRole(roleId), enabled: !isNew });
  const [grants, setGrants] = useState({});
  const [confirm, setConfirm] = useState(null); // 저장할 본문
  const [removing, setRemoving] = useState(false);

  useEffect(() => {
    if (role.data) {
      form.setFieldsValue({ name: role.data.name, description: role.data.description });
      setGrants(toMap(role.data.permissions));
    }
  }, [role.data, form]);

  const readOnly = !isNew && role.data?.isSystem;
  const used = useMemo(() => perms.filter((p) => grants[p.code]), [perms, grants]);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: rolesKey });
    queryClient.invalidateQueries({ queryKey: meKey }); // 내 역할이면 사이드바가 바로 바뀐다
  };

  const save = useMutation({
    mutationFn: (body) => (isNew ? createRole(body) : updateRole(roleId, body)),
    onSuccess: (data) => {
      queryClient.setQueryData(roleKey(data.id), data);
      refresh();
      notify.success(isNew ? `${data.name} 역할을 만들었습니다` : `${data.name} 권한을 저장했습니다`);
      if (isNew) onSelect(data.id);
    },
  });

  const toggle = (p, on) => setGrants((g) => ({ ...g, [p.code]: on ? (p.allowedScopes.includes('ALL') ? 'ALL' : p.allowedScopes[0]) : undefined }));

  const onFinish = (v) => setConfirm({
    name: v.name.trim(),
    description: v.description?.trim() || null,
    permissions: used.map((p) => ({ code: p.code, scope: grants[p.code] })),
  });

  if (!isNew && role.isLoading) return <Spin />;
  if (!isNew && role.error) return <p className="hr-notice is-error">{errorMessage(role.error)}</p>;
  const count = isNew ? 0 : role.data.accountCount;

  return (
    <Form form={form} layout="vertical" requiredMark onFinish={onFinish} disabled={readOnly} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      {readOnly && (
        <p className="ad-banner">
          이 역할은 시스템 역할이라 수정·삭제할 수 없습니다. (SYSTEM_ROLE_READONLY)
          {role.data.name === '최고 관리자' ? ' · 마지막 1명은 강등·비활성화·퇴직 처리할 수 없습니다.' : ' 다른 권한 묶음이 필요하면 [+ 역할 추가]로 새 역할을 만드세요.'}
        </p>
      )}
      <div className="ad-grid" style={{ '--cols': 2 }}>
        <Form.Item name="name" label="역할 이름" rules={[{ required: true, whitespace: true, message: '역할 이름을 입력하세요' }]}>
          <Input maxLength={50} placeholder="예: 급여 담당" />
        </Form.Item>
        <Form.Item name="description" label="설명">
          <Input maxLength={255} placeholder="예: 급여 정산 담당" />
        </Form.Item>
      </div>

      <div>
        <div className="ad-perm-head"><span>사용</span><span>권한 코드</span><span>설명 · 관련 기능</span><span>범위</span></div>
        {perms.map((p) => {
          const on = !!grants[p.code];
          const teamOk = p.allowedScopes.length > 1;
          return (
            <div key={p.code} className={`ad-perm${on ? '' : ' is-off'}`}>
              <Checkbox checked={on} disabled={readOnly} onChange={(e) => toggle(p, e.target.checked)} aria-label={p.code} />
              <code>{p.code}</code>
              <span className="desc">{p.description}<span className="rel">{p.relatedFeatures}</span></span>
              {!on ? (
                <span className="ad-scope-fixed" style={{ border: 0 }}>-</span>
              ) : teamOk && !readOnly ? (
                <Select size="small" value={grants[p.code]} onChange={(v) => setGrants((g) => ({ ...g, [p.code]: v }))}
                  options={p.allowedScopes.map((s) => ({ value: s, label: labelOf('permissionScope', s) }))} />
              ) : (
                <span className="ad-scope-fixed">{teamOk ? labelOf('permissionScope', grants[p.code]) : '전사 (고정)'}</span>
              )}
            </div>
          );
        })}
        <p className="hr-hint" style={{ marginTop: '0.5rem' }}>팀 범위는 조회 권한 5개만 고를 수 있고, 조직장에게만 효력이 있습니다(자기 팀만 보입니다).</p>
      </div>

      <div className="ad-editor-foot">
        <span>
          {readOnly ? '읽기 전용 · 변경할 수 없습니다' : isNew ? '저장하면 계정 관리에서 직원에게 이 역할을 지정할 수 있습니다' : (
            <>저장하면 이 역할을 가진 <strong>{`${count}명`}</strong>에게 즉시 반영됩니다</>
          )}
        </span>
        <div className="hr-actions">
          {!isNew && (
            <button type="button" className="hr-btn is-outline-red" disabled={readOnly} onClick={() => setRemoving(true)}>삭제</button>
          )}
          <button type="submit" className="hr-btn" disabled={readOnly || save.isPending}>저장</button>
        </div>
      </div>

      <Modal open={!!confirm} title="권한 변경을 저장할까요?" footer={null} onCancel={() => !save.isPending && setConfirm(null)}
        width={460} className="hr-modal" destroyOnHidden centered>
        {confirm && (
          <>
            <p className="hr-modal-sub">{isNew ? `'${confirm.name}' 역할을 새로 만듭니다.` : `'${confirm.name}' 역할의 권한 목록이 통째로 교체됩니다.`}</p>
            {!isNew && (
              <p className="ad-warn">{`저장 즉시 이 역할을 가진 ${count}명에게 반영됩니다. 다시 로그인하지 않아도 사이드바가 바뀝니다.`}</p>
            )}
            <div className="ad-codes">
              <span className="k">{`사용 중인 권한 ${confirm.permissions.length}개`}</span>
              {confirm.permissions.length === 0 ? <span>없음 — 내 업무만 보입니다</span> : confirm.permissions.map((p) => (
                <span key={p.code}>{`${p.code} (${labelOf('permissionScope', p.scope)})`}</span>
              ))}
            </div>
            <div className="hr-modal-foot">
              <button type="button" className="hr-btn is-secondary" onClick={() => setConfirm(null)} disabled={save.isPending}>취소</button>
              <button type="button" className="hr-btn" disabled={save.isPending}
                onClick={() => save.mutate(confirm, {
                  onSuccess: () => setConfirm(null),
                  onError: (err) => {
                    setConfirm(null);
                    if (!applyFieldErrors(form, err)) notify.error(err);
                  },
                })}>
                {save.isPending ? '저장 중…' : '저장'}
              </button>
            </div>
          </>
        )}
      </Modal>

      <ConfirmModal open={removing} danger okText="삭제" title="역할 삭제"
        desc={`'${role.data?.name ?? ''}' 역할을 삭제합니다. 계정이 배정돼 있거나 승인선 조건에 쓰인 역할은 지울 수 없습니다(다른 역할로 옮긴 뒤 삭제).`}
        onClose={() => setRemoving(false)}
        onConfirm={async () => {
          await deleteRole(roleId);
          onSelect(null);
          queryClient.removeQueries({ queryKey: roleKey(roleId) });
          refresh();
          notify.success('역할을 삭제했습니다');
        }} />
    </Form>
  );
}
