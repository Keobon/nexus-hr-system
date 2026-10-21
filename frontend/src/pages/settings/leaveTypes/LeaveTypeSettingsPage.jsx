// 회사 설정 › 휴가 종류(F-LEAVE-01, LEAVE_MANAGE) — Figma 70-4145(목록) · 489-17446(추가) · 489-17677 · 489-17912(수정 · 검증) · 489-18148(비활성 보기)
// 쓰인 종류는 삭제하면 서버가 비활성화만 한다(DEACTIVATED). 근속 가산 4개는 모두 입력하거나 모두 비운다
import { useState } from 'react';
import { App, Form, Input, InputNumber, Modal, Switch } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../../api/client';
import { allLeaveTypesKey, getAllLeaveTypes } from '../../../api/leaves';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { PageHead } from '../../hr/parts';
import { Pill, Table } from '../../hr/attendance/shared';
import '../../hr/attendance/at.css';
import './lt.css';

const createLeaveType = (body) => api.post('/leave-types', body);
const updateLeaveType = (id, patch) => api.patch(`/leave-types/${id}`, patch);
const deleteLeaveType = (id) => api.delete(`/leave-types/${id}`);
const SENIORITY = ['seniorityStartYears', 'seniorityIntervalYears', 'seniorityAddDays', 'seniorityMaxDays'];

/** "3년부터 2년마다 +1일 (최대 25일)" */
const seniorityText = (t) => (t.seniorityStartYears ? `${t.seniorityStartYears}년부터 ${t.seniorityIntervalYears}년마다 +${t.seniorityAddDays}일 (최대 ${t.seniorityMaxDays}일)` : '–');

export default function LeaveTypeSettingsPage() {
  const { modal, message } = App.useApp();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [showInactive, setShowInactive] = useState(false);
  const [edit, setEdit] = useState(null);
  const list = useQuery({ queryKey: allLeaveTypesKey, queryFn: getAllLeaveTypes });
  const all = [...(list.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const rows = showInactive ? all : all.filter((t) => t.isActive);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['leave-types'] });

  const remove = useMutation({
    mutationFn: (t) => deleteLeaveType(t.id),
    onSuccess: (r, t) => {
      refresh();
      if (r?.result === 'DEACTIVATED') {
        message.info('사용 중이라 비활성화했습니다.');
        setShowInactive(true);
      } else message.success(`${t.name}을(를) 삭제했습니다`);
    },
    onError: (err) => notify.error(err),
  });
  const askRemove = (t) =>
    modal.confirm({
      title: `${t.name}을(를) 삭제할까요?`,
      content: '신청 · 부여에 쓰인 휴가 종류는 지워지지 않고 비활성화됩니다.',
      okText: '삭제',
      okButtonProps: { danger: true },
      cancelText: '취소',
      onOk: () => remove.mutateAsync(t),
    });

  return (
    <div className="hr at lt">
      <PageHead title="휴가 종류" desc="휴가 종류와 부여 규칙을 관리합니다." />
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
            { key: 'name', label: '이름', width: '8rem' },
            { key: 'days', label: '연간 부여일수', width: '8rem' },
            { key: 'deduct', label: '잔여 차감', width: '8rem' },
            { key: 'paid', label: '유급', width: '5rem' },
            { key: 'prorate', label: '입사 첫해 비례', width: '8rem' },
            { key: 'sen', label: '근속 가산' },
            { key: 'order', label: '정렬', width: '4.5rem' },
            { key: 'st', label: '상태', width: '5.5rem' },
            { key: 'act', label: '관리', width: '7rem' },
          ]}
          loading={list.isLoading}
          error={list.error && errorMessage(list.error)}
          empty={rows.length === 0 && '등록된 휴가 종류가 없습니다'}
        >
          {rows.map((t) => (
            <tr key={t.id} className={t.isActive ? undefined : 'is-inactive'}>
              <td className="at-strong">{t.name}</td>
              <td>{`${t.annualDays}일`}</td>
              <td>
                <Pill tone={t.deductsBalance ? 'info' : 'gray'}>{t.deductsBalance ? '차감' : '차감 안 함'}</Pill>
              </td>
              <td>{t.paid ? '유급' : '무급'}</td>
              <td>{t.prorateFirstYear ? '예' : '아니오'}</td>
              <td>{seniorityText(t)}</td>
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
      <p className="at-muted is-11">※ 사용 중인 휴가 유형은 삭제할 수 없으며 비활성화만 가능합니다.</p>

      {edit && <TypeModal item={edit} nextOrder={(all.at(-1)?.sortOrder ?? 0) + 1} onClose={() => setEdit(null)} onSaved={refresh} />}
    </div>
  );
}

/* ───── 추가 · 수정 창(489-17446 · 489-17677) ───── */
function TypeModal({ item, nextOrder, onClose, onSaved }) {
  const notify = useNotify();
  const [form] = Form.useForm();
  const isNew = !item.id;
  const [open, setOpen] = useState(isNew || !!item.seniorityStartYears);
  const v = Form.useWatch([], form) ?? {};
  // 예: 근속 5년 → 15 + 2 = 17일
  const example = (() => {
    const { annualDays: base, seniorityStartYears: s, seniorityIntervalYears: i, seniorityAddDays: a, seniorityMaxDays: m } = v;
    if ([base, s, i, a, m].some((x) => x == null)) return null;
    // 서버 LeaveCalculator 와 같은 규칙 — 시작 연수부터 +가산, 그 뒤 간격마다 +가산, 최대 일수까지
    const years = s + i;
    const add = (Math.floor((years - s) / i) + 1) * a;
    const total = Math.min(m, base + add);
    return `예: 근속 ${years}년 → ${base} + ${total - base} = ${total}일`;
  })();

  const save = useMutation({
    mutationFn: (f) => {
      const body = {
        name: f.name.trim(),
        annualDays: f.annualDays,
        deductsBalance: f.deductsBalance,
        paid: f.paid,
        prorateFirstYear: f.prorateFirstYear,
        ...Object.fromEntries(SENIORITY.map((k) => [k, f[k] ?? null])),
        sortOrder: f.sortOrder,
        ...(isNew ? {} : { isActive: f.isActive }),
      };
      return isNew ? createLeaveType(body) : updateLeaveType(item.id, body);
    },
    onSuccess: (r) => {
      notify.success(isNew ? `${r.name}을(를) 추가했습니다` : `${r.name}을(를) 저장했습니다`);
      onSaved();
      onClose();
    },
    onError: (err) => {
      // 서버 문장 검증(근속 가산 · 최대 일수)은 해당 칸 아래에 보여 준다
      const msg = errorMessage(err);
      if (err?.code === 'DUPLICATE_NAME') return form.setFields([{ name: 'name', errors: [msg] }]);
      if (/최대 일수/.test(msg)) return form.setFields([{ name: 'seniorityMaxDays', errors: [msg] }]);
      if (/근속 가산/.test(msg)) return form.setFields([{ name: 'seniorityStartYears', errors: [msg] }]);
      if (err?.fields) return form.setFields(Object.entries(err.fields).map(([k, m]) => ({ name: k, errors: [m] })));
      notify.error(err);
    },
  });

  // 4개 모두 있거나 모두 없거나, 최대 일수 ≥ 연간 부여일수(서버 규칙을 먼저)
  const seniorityRule = ({ getFieldValue }) => ({
    validator: () => {
      const vals = SENIORITY.map((k) => getFieldValue(k));
      const filled = vals.filter((x) => x != null).length;
      if (filled !== 0 && filled !== 4) return Promise.reject(new Error('4개 값을 모두 입력하거나 모두 비워 두세요'));
      return Promise.resolve();
    },
  });
  const maxRule = ({ getFieldValue }) => ({
    validator: (_, m) => (m == null || getFieldValue('annualDays') == null || m >= getFieldValue('annualDays') ? Promise.resolve() : Promise.reject(new Error('최대 일수는 연간 부여일수 이상이어야 합니다.'))),
  });

  return (
    <Modal open title={isNew ? '휴가 종류 추가' : '휴가 종류 수정'} footer={null} onCancel={onClose} width={600} className="hr-modal lt-modal" destroyOnHidden centered>
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{
          name: item.name ?? '',
          annualDays: item.annualDays ?? 0,
          deductsBalance: item.deductsBalance ?? true,
          paid: item.paid ?? true,
          prorateFirstYear: item.prorateFirstYear ?? false,
          ...Object.fromEntries(SENIORITY.map((k) => [k, item[k] ?? null])),
          sortOrder: item.sortOrder ?? nextOrder,
          isActive: item.isActive ?? true,
        }}
        onFinish={(f) => save.mutate(f)}
      >
        <div className="lt-row">
          <Form.Item name="name" label={<span className="lt-label">이름*<em>최대 50자</em></span>} rules={[{ required: true, whitespace: true, message: '이름을 입력하세요' }]}>
            <Input maxLength={50} placeholder="이름을 입력하세요" />
          </Form.Item>
          <Form.Item name="annualDays" label={<span className="lt-label">연간 부여일수*<em>0–365</em></span>} extra="0이면 부여 없이 쓰는 휴가입니다" rules={[{ required: true, message: '연간 부여일수를 입력하세요' }]}>
            <InputNumber className="full-width" min={0} max={365} precision={0} />
          </Form.Item>
        </div>

        <div className="lt-switches">
          <Form.Item name="deductsBalance" label="잔여 차감*" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="paid" label="유급" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="prorateFirstYear" label="입사 첫해 비례 부여" valuePropName="checked">
            <Switch />
          </Form.Item>
        </div>

        <div className="lt-sen">
          <button type="button" className="lt-sen-head" onClick={() => setOpen((o) => !o)}>
            <span className={open ? 'is-open' : undefined}>›</span>근속 가산 (선택)
          </button>
          {open && (
            <>
              <p className="at-muted">4개 값을 모두 입력하거나 모두 비워 두세요.</p>
              <div className="lt-row">
                <Form.Item name="seniorityStartYears" label="시작 근속연수" dependencies={SENIORITY} rules={[seniorityRule]}>
                  <InputNumber className="full-width" min={1} max={50} precision={0} placeholder="예: 3" />
                </Form.Item>
                <Form.Item name="seniorityIntervalYears" label="가산 간격(년)">
                  <InputNumber className="full-width" min={1} max={50} precision={0} placeholder="예: 2" />
                </Form.Item>
                <Form.Item name="seniorityAddDays" label="가산 일수">
                  <InputNumber className="full-width" min={1} max={365} precision={0} placeholder="예: 1" />
                </Form.Item>
                <Form.Item name="seniorityMaxDays" label="최대 일수" dependencies={['annualDays']} rules={[maxRule]}>
                  <InputNumber className="full-width" min={0} max={365} precision={0} placeholder="예: 25" />
                </Form.Item>
              </div>
              {example && <p className="lt-example">{example}</p>}
            </>
          )}
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
          <p className="at-muted">부여일수를 바꾸면 다음 부여부터 적용됩니다. 이미 부여된 내역은 바뀌지 않습니다.</p>
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
