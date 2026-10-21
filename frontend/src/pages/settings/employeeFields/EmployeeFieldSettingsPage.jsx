// 회사 설정 › 직원 정보 항목(F-EMP-07, ORG_MANAGE) — 설정 목록 A형(휴가 종류와 같은 모양): 비활성 보기 · 추가 · 수정/삭제 · 추가/수정 창
// 값이 입력된 항목은 타입을 못 바꾸고 삭제하면 비활성화된다 — 목록 응답의 hasValues 로 미리 타입 칸을 잠그고 삭제 확인 문구를 바꾼다.
// 그 사이 값이 생겨 저장 때 ITEM_IN_USE 가 와도 타입 칸을 잠그고 안내한다
import { useState } from 'react';
import { App, Checkbox, Form, Input, InputNumber, Modal, Select, Switch } from 'antd';
import { CheckOutlined, MinusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../../api/client';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf, optionsOf } from '../../../constants/labels';
import { PageHead } from '../../hr/parts';
import { Pill, Table } from '../../hr/attendance/shared';
import '../../hr/attendance/at.css';
import '../leaveTypes/lt.css';
import './ef.css';

const fieldsKey = ['employee-fields', 'all'];
const getFields = () => api.get('/employee-fields');
const createField = (body) => api.post('/employee-fields', body);
const updateField = (id, patch) => api.patch(`/employee-fields/${id}`, patch);
const deleteField = (id) => api.delete(`/employee-fields/${id}`);

function YesNo({ value }) {
  return value ? <CheckOutlined className="ef-yes" aria-label="예" /> : <MinusOutlined className="ef-no" aria-label="아니오" />;
}

export default function EmployeeFieldSettingsPage() {
  const { modal, message } = App.useApp();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [showInactive, setShowInactive] = useState(false);
  const [edit, setEdit] = useState(null);
  const list = useQuery({ queryKey: fieldsKey, queryFn: getFields });
  const all = [...(list.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const rows = showInactive ? all : all.filter((f) => f.isActive);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['employee-fields'] });

  const remove = useMutation({
    mutationFn: (f) => deleteField(f.id),
    onSuccess: (r, f) => {
      refresh();
      if (r?.result === 'DEACTIVATED') {
        message.info('사용 중이라 비활성화했습니다');
        setShowInactive(true);
      } else message.success(`${f.name} 항목을 삭제했습니다`);
    },
    onError: (err) => notify.error(err),
  });
  const askRemove = (f) =>
    modal.confirm({
      title: f.hasValues ? `${f.name} 항목을 비활성화할까요?` : `${f.name} 항목을 삭제할까요?`,
      content: f.hasValues
        ? '직원에게 입력된 값이 있어 지워지지 않고 비활성화됩니다. 값은 남고 입력 화면에서만 숨겨집니다.'
        : '입력된 값이 없어 항목이 완전히 삭제됩니다.',
      okText: f.hasValues ? '비활성화' : '삭제',
      okButtonProps: { danger: true },
      cancelText: '취소',
      onOk: () => remove.mutateAsync(f),
    });

  return (
    <div className="hr at lt">
      <PageHead title="직원 정보 항목" desc="회사에 필요한 직원 정보를 항목으로 추가합니다." />
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
            { key: 'name', label: '항목 이름', width: '10rem' },
            { key: 'type', label: '타입', width: '7rem' },
            { key: 'opts', label: '선택지' },
            { key: 'req', label: '필수', width: '5rem' },
            { key: 'multi', label: '여러 건 허용', width: '7rem' },
            { key: 'self', label: '본인 수정', width: '6rem' },
            { key: 'order', label: '정렬', width: '4.5rem' },
            { key: 'st', label: '상태', width: '5.5rem' },
            { key: 'act', label: '관리', width: '7rem' },
          ]}
          loading={list.isLoading}
          error={list.error && errorMessage(list.error)}
          empty={rows.length === 0 && '등록된 직원 정보 항목이 없습니다'}
        >
          {rows.map((f) => (
            <tr key={f.id} className={f.isActive ? undefined : 'is-inactive'}>
              <td className="at-strong">{f.name}</td>
              <td>{labelOf('fieldType', f.fieldType)}</td>
              <td>
                {f.fieldType === 'SELECT' && f.options?.length ? (
                  <span className="ef-chips">
                    {f.options.map((o) => (
                      <span key={o}>{o}</span>
                    ))}
                  </span>
                ) : (
                  '–'
                )}
              </td>
              <td><YesNo value={f.isRequired} /></td>
              <td><YesNo value={f.isMultiple} /></td>
              <td><YesNo value={f.isSelfEditable} /></td>
              <td>{f.sortOrder}</td>
              <td>
                <Pill tone={f.isActive ? 'ok' : 'gray'}>{f.isActive ? '사용' : '비활성'}</Pill>
              </td>
              <td>
                <span className="lt-acts">
                  <button type="button" className="hr-link" onClick={() => setEdit(f)}>
                    수정
                  </button>
                  <i>·</i>
                  <button type="button" className="hr-link is-red" onClick={() => askRemove(f)}>
                    삭제
                  </button>
                </span>
              </td>
            </tr>
          ))}
        </Table>
      </section>
      <p className="at-muted is-11">※ 직원에게 값이 입력된 항목은 삭제할 수 없으며 비활성화만 가능합니다. 본인 수정이 켜진 항목은 직원이 내정보에서 직접 고칠 수 있습니다.</p>

      {edit && <FieldModal item={edit} nextOrder={(all.at(-1)?.sortOrder ?? 0) + 1} onClose={() => setEdit(null)} onSaved={refresh} />}
    </div>
  );
}

/* ───── 항목 추가 · 수정 창 ───── */
function FieldModal({ item, nextOrder, onClose, onSaved }) {
  const notify = useNotify();
  const [form] = Form.useForm();
  const isNew = !item.id;
  const [typeLocked, setTypeLocked] = useState(!!item.hasValues);
  const fieldType = Form.useWatch('fieldType', form) ?? item.fieldType ?? 'TEXT';

  const save = useMutation({
    mutationFn: (v) => {
      const body = {
        name: v.name.trim(),
        fieldType: v.fieldType,
        options: v.fieldType === 'SELECT' ? (v.options ?? []).map((o) => o.trim()).filter(Boolean) : null,
        isRequired: !!v.isRequired,
        isMultiple: !!v.isMultiple,
        isSelfEditable: !!v.isSelfEditable,
        sortOrder: v.sortOrder,
        ...(isNew ? {} : { isActive: v.isActive }),
      };
      return isNew ? createField(body) : updateField(item.id, body);
    },
    onSuccess: (r) => {
      notify.success(isNew ? `${r.name} 항목을 추가했습니다` : `${r.name} 항목을 저장했습니다`);
      onSaved();
      onClose();
    },
    onError: (err) => {
      const msg = err?.message ?? errorMessage(err); // 서버 문장으로 어떤 ITEM_IN_USE 인지 가른다
      if (err?.code === 'DUPLICATE_NAME') return form.setFields([{ name: 'name', errors: ['이미 같은 이름이 있습니다.'] }]);
      if (err?.code === 'ITEM_IN_USE' && /타입/.test(msg)) {
        setTypeLocked(true);
        return form.setFields([{ name: 'fieldType', value: item.fieldType, errors: [] }]);
      }
      if (err?.code === 'ITEM_IN_USE' && /여러 건/.test(msg)) return form.setFields([{ name: 'isMultiple', value: true, errors: [msg] }]);
      if (err?.fields) return form.setFields(Object.entries(err.fields).map(([k, m]) => ({ name: k, errors: [m] })));
      notify.error(err);
    },
  });

  return (
    <Modal open title={isNew ? '항목 추가' : '항목 수정'} footer={null} onCancel={onClose} width={520} className="hr-modal lt-modal" destroyOnHidden centered>
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{
          name: item.name ?? '',
          fieldType: item.fieldType ?? 'TEXT',
          options: item.options ?? [],
          isRequired: item.isRequired ?? false,
          isMultiple: item.isMultiple ?? false,
          isSelfEditable: item.isSelfEditable ?? false,
          sortOrder: item.sortOrder ?? nextOrder,
          isActive: item.isActive ?? true,
        }}
        onFinish={(v) => save.mutate(v)}
      >
        <Form.Item name="name" label={<span className="lt-label">항목 이름*<em>최대 50자</em></span>} rules={[{ required: true, whitespace: true, message: '항목 이름을 입력하세요' }]}>
          <Input maxLength={50} placeholder="예: 기술 스택" />
        </Form.Item>
        <Form.Item
          name="fieldType"
          label="타입*"
          extra={typeLocked ? <span className="ef-locked">값이 입력된 항목은 타입을 바꿀 수 없습니다.</span> : undefined}
          rules={[{ required: true, message: '타입을 고르세요' }]}
        >
          <Select options={optionsOf('fieldType')} disabled={typeLocked} />
        </Form.Item>
        {fieldType === 'SELECT' && (
          <Form.Item
            name="options"
            label="선택지*"
            extra="입력하고 Enter 를 누르면 추가됩니다."
            rules={[{ validator: (_, v) => ((v ?? []).filter((o) => o.trim()).length ? Promise.resolve() : Promise.reject(new Error('선택지를 하나 이상 입력하세요'))) }]}
          >
            <Select mode="tags" open={false} suffixIcon={null} tokenSeparators={[',']} placeholder="예: A, B, O, AB" maxTagTextLength={100} />
          </Form.Item>
        )}
        <div className="ef-checks">
          <Form.Item name="isRequired" valuePropName="checked">
            <Checkbox>필수</Checkbox>
          </Form.Item>
          <Form.Item name="isMultiple" valuePropName="checked">
            <Checkbox>여러 건 허용</Checkbox>
          </Form.Item>
          <Form.Item name="isSelfEditable" valuePropName="checked">
            <Checkbox>본인 수정 가능</Checkbox>
          </Form.Item>
        </div>
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
