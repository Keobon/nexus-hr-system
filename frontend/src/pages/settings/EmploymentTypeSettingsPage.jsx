// 회사 설정 › 고용형태(F-ORG-03) — 직급·직책 화면(Figma HR_positions)과 같은 모양, 탭 없이 한 목록
import { useState } from 'react';
import { Form, Input, InputNumber, Modal, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { employmentTypesApi } from '../../api/orgSettings';
import { applyFieldErrors } from '../../components/common/formErrors';
import { useNotify } from '../../components/common/useNotify';
import { errorMessage } from '../../constants/errors';
import { ActiveBadge, ConfirmModal, ModalFoot, PageHead } from '../hr/parts';

export default function EmploymentTypeSettingsPage() {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const label = '고용형태';
  const api = employmentTypesApi;
  const list = useQuery({ queryKey: api.key(false), queryFn: () => api.list(false) });
  const rows = [...(list.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const [edit, setEdit] = useState(null);
  const [deactivating, setDeactivating] = useState(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['employment-types'] });
  const setActive = (r, isActive) => api.update(r.id, { name: r.name, sortOrder: r.sortOrder, isActive });
  const activate = async (r) => {
    try { await setActive(r, true); notify.success(`${r.name}을(를) 다시 활성화했습니다`); refresh(); } catch (err) { notify.error(err); }
  };

  return (
    <div className="hr">
      <PageHead title="고용형태 관리" desc="정규직 · 계약직처럼 직원 등록에서 고르는 고용형태를 관리합니다.">
        <button type="button" className="hr-btn" onClick={() => setEdit({})}>{`${label} 등록`}</button>
      </PageHead>
      <div className="hr-table">
        <div className="hr-tr is-head">
          <span style={{ width: '18.75rem' }}>이름</span>
          <span style={{ width: '10rem' }}>정렬순서</span>
          <span style={{ width: '10rem' }}>활성 상태</span>
          <span>관리</span>
        </div>
        {list.isLoading ? (
          <div className="hr-tr is-empty"><Spin /></div>
        ) : list.error ? (
          <div className="hr-tr is-empty">{errorMessage(list.error)}</div>
        ) : rows.length === 0 ? (
          <div className="hr-tr is-empty">{`등록된 ${label}이(가) 없습니다`}</div>
        ) : (
          rows.map((r) => (
            <div key={r.id} className="hr-tr">
              <span className="hr-strong" style={{ width: '18.75rem' }}>{r.name}</span>
              <span style={{ width: '10rem' }}>{r.sortOrder}</span>
              <span style={{ width: '10rem' }}><ActiveBadge active={r.isActive} /></span>
              <span className="hr-row-actions">
                <button type="button" className="hr-link" onClick={() => setEdit(r)}>수정</button>
                {r.isActive
                  ? <button type="button" className="hr-link is-red" onClick={() => setDeactivating(r)}>비활성화</button>
                  : <button type="button" className="hr-link" onClick={() => activate(r)}>활성화</button>}
              </span>
            </div>
          ))
        )}
      </div>
      <p className="hr-note">※ 직원에게 쓰인 고용형태는 삭제할 수 없으며 비활성화만 가능합니다.</p>

      {edit && <ItemModal item={edit} api={api} label={label} nextOrder={(rows.at(-1)?.sortOrder ?? 0) + 1} onClose={() => setEdit(null)} onSaved={refresh} />}
      <ConfirmModal open={!!deactivating} danger okText="비활성화"
        desc={`${deactivating?.name}을(를) 비활성화하면 새로 등록·발령할 때 고를 수 없습니다. 이미 쓰고 있는 직원의 값은 그대로 남습니다. 계속하시겠습니까?`}
        onClose={() => setDeactivating(null)}
        onConfirm={async () => { await setActive(deactivating, false); notify.success(`${deactivating.name} 비활성화`); refresh(); }} />
    </div>
  );
}

function ItemModal({ item, api, label, nextOrder, onClose, onSaved }) {
  const notify = useNotify();
  const [form] = Form.useForm();
  const isNew = !item.id;
  const save = useMutation({
    mutationFn: (v) => (isNew ? api.create({ name: v.name.trim(), sortOrder: v.sortOrder }) : api.update(item.id, { name: v.name.trim(), sortOrder: v.sortOrder })),
    onSuccess: () => {
      notify.success(isNew ? `${label}을(를) 등록했습니다` : `${label}을(를) 저장했습니다`);
      onSaved();
      onClose();
    },
    onError: (err) => { if (!applyFieldErrors(form, err)) notify.error(err); },
  });
  return (
    <Modal open title={`${label} ${item.id ? '수정' : '등록'}`} footer={null} onCancel={onClose} width={440} className="hr-modal" destroyOnHidden centered>
      <div style={{ height: '1.25rem' }} />
      <Form form={form} layout="vertical" requiredMark={false} onFinish={(v) => save.mutate(v)}
        initialValues={{ name: item.name, sortOrder: item.sortOrder ?? nextOrder }}>
        <Form.Item name="name" label="이름" rules={[{ required: true, whitespace: true, message: '이름을 입력하세요' }]}>
          <Input maxLength={50} placeholder="예: 인턴" />
        </Form.Item>
        <Form.Item name="sortOrder" label="정렬순서" rules={[{ required: true, message: '정렬순서를 입력하세요' }]}>
          <InputNumber className="full-width" min={0} precision={0} />
        </Form.Item>
        <ModalFoot onCancel={onClose} busy={save.isPending} />
      </Form>
    </Modal>
  );
}
