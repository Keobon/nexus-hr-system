// 회사 설정 › 출장 경비 종류(F-ATT-09, ATTENDANCE_MANAGE) — 설정 목록 A형(휴가 종류와 같은 모양): 비활성 보기 · 추가 · 수정/삭제 · 추가/수정 창
// 청구에 쓰인 종류는 삭제하면 서버가 비활성화만 한다(DEACTIVATED)
import { useState } from 'react';
import { App, Form, Input, InputNumber, Modal, Switch } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../../api/client';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { PageHead } from '../../hr/parts';
import { Pill, Table } from '../../hr/attendance/shared';
import '../../hr/attendance/at.css';
import '../leaveTypes/lt.css';

const typesKey = ['expense-types', 'all'];
const getTypes = () => api.get('/expense-types');
const createType = (body) => api.post('/expense-types', body);
const updateType = (id, patch) => api.patch(`/expense-types/${id}`, patch);
const deleteType = (id) => api.delete(`/expense-types/${id}`);

export default function ExpenseTypeSettingsPage() {
  const { modal, message } = App.useApp();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [showInactive, setShowInactive] = useState(false);
  const [edit, setEdit] = useState(null);
  const list = useQuery({ queryKey: typesKey, queryFn: getTypes });
  const all = [...(list.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const rows = showInactive ? all : all.filter((t) => t.isActive);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['expense-types'] });

  const remove = useMutation({
    mutationFn: (t) => deleteType(t.id),
    onSuccess: (r, t) => {
      refresh();
      if (r?.result === 'DEACTIVATED') {
        message.info('사용 중이라 비활성화했습니다');
        setShowInactive(true);
      } else message.success(`${t.name}을(를) 삭제했습니다`);
    },
    onError: (err) => notify.error(err),
  });
  const askRemove = (t) =>
    modal.confirm({
      title: `${t.name}을(를) 삭제할까요?`,
      content: '청구에 쓰인 종류는 삭제하면 비활성화됩니다.',
      okText: '삭제',
      okButtonProps: { danger: true },
      cancelText: '취소',
      onOk: () => remove.mutateAsync(t),
    });

  return (
    <div className="hr at lt">
      <PageHead title="출장 경비 종류" desc="출장 경비 청구에 쓰는 경비 종류를 관리합니다." />
      <div className="lt-bar">
        <label className="lt-toggle">
          <span>비활성 항목 보기</span>
          <Switch size="small" checked={showInactive} onChange={setShowInactive} />
        </label>
        <button type="button" className="at-btn" onClick={() => setEdit({})}>
          추가
        </button>
      </div>

      <section className="at-card">
        <Table
          cols={[
            { key: 'name', label: '이름' },
            { key: 'receipt', label: '영수증', width: '9rem' },
            { key: 'order', label: '정렬', width: '6rem' },
            { key: 'st', label: '상태', width: '7rem' },
            { key: 'act', label: '관리', width: '8rem' },
          ]}
          loading={list.isLoading}
          error={list.error && errorMessage(list.error)}
          empty={rows.length === 0 && '등록된 출장 경비 종류가 없습니다'}
        >
          {rows.map((t) => (
            <tr key={t.id} className={t.isActive ? undefined : 'is-inactive'}>
              <td className="at-strong">{t.name}</td>
              <td>
                <Pill tone={t.receiptRequired ? 'info' : 'gray'}>{t.receiptRequired ? '필수' : '불필요'}</Pill>
              </td>
              <td>{t.sortOrder}</td>
              <td>
                <Pill tone={t.isActive ? 'ok' : 'gray'}>{t.isActive ? '사용' : '비활성'}</Pill>
              </td>
              <td>
                <span className="lt-acts">
                  <button type="button" className="hr-link" onClick={() => setEdit(t)}>
                    수정
                  </button>
                  <i>·</i>
                  <button type="button" className="hr-link is-red" onClick={() => askRemove(t)}>
                    삭제
                  </button>
                </span>
              </td>
            </tr>
          ))}
        </Table>
      </section>
      <p className="at-muted is-11">※ 청구에 쓰인 종류는 삭제하면 비활성화됩니다. 비활성 종류는 새 경비 청구에서 고를 수 없습니다.</p>

      {edit && <TypeModal item={edit} nextOrder={(all.at(-1)?.sortOrder ?? 0) + 1} onClose={() => setEdit(null)} onSaved={refresh} />}
    </div>
  );
}

/* ───── 경비 종류 추가 · 수정 창 ───── */
function TypeModal({ item, nextOrder, onClose, onSaved }) {
  const notify = useNotify();
  const [form] = Form.useForm();
  const isNew = !item.id;

  const save = useMutation({
    mutationFn: (v) => {
      const body = { name: v.name.trim(), receiptRequired: v.receiptRequired, sortOrder: v.sortOrder, ...(isNew ? {} : { isActive: v.isActive }) };
      return isNew ? createType(body) : updateType(item.id, body);
    },
    onSuccess: (r) => {
      notify.success(isNew ? `${r.name}을(를) 추가했습니다` : `${r.name}을(를) 저장했습니다`);
      onSaved();
      onClose();
    },
    onError: (err) => {
      if (err?.code === 'DUPLICATE_NAME') return form.setFields([{ name: 'name', errors: ['이미 같은 이름이 있습니다.'] }]);
      if (err?.fields) return form.setFields(Object.entries(err.fields).map(([k, m]) => ({ name: k, errors: [m] })));
      notify.error(err);
    },
  });

  return (
    <Modal open title={isNew ? '경비 종류 추가' : '경비 종류 수정'} footer={null} onCancel={onClose} width={480} className="hr-modal lt-modal" destroyOnHidden centered>
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{ name: item.name ?? '', receiptRequired: item.receiptRequired ?? true, sortOrder: item.sortOrder ?? nextOrder, isActive: item.isActive ?? true }}
        onFinish={(v) => save.mutate(v)}
      >
        <Form.Item name="name" label={<span className="lt-label">이름*<em>최대 50자</em></span>} rules={[{ required: true, whitespace: true, message: '이름을 입력하세요' }]}>
          <Input maxLength={50} placeholder="예: 교통비" />
        </Form.Item>
        <Form.Item name="receiptRequired" label="영수증 필수" valuePropName="checked" className="lt-inline">
          <Switch />
        </Form.Item>
        <div className="lt-row is-last">
          <Form.Item name="sortOrder" label="정렬 순서">
            <InputNumber className="full-width" min={0} precision={0} />
          </Form.Item>
          {!isNew && (
            <Form.Item name="isActive" label="사용" valuePropName="checked" className="lt-inline">
              <Switch />
            </Form.Item>
          )}
        </div>
        <div className="lt-foot">
          <p className="at-muted">청구에 쓰인 종류는 삭제하면 비활성화됩니다.</p>
          <div>
            <button type="button" className="lt-btn is-line" onClick={onClose}>
              취소
            </button>
            <button type="submit" className="lt-btn" disabled={save.isPending}>
              저장
            </button>
          </div>
        </div>
      </Form>
    </Modal>
  );
}
