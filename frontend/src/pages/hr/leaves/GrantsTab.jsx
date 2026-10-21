// 휴가 부여(Figma 487-17038, LEAVE_MANAGE) — 연도 일괄 부여 · 개별 조정 · 직원별 부여 내역
import { useMemo, useState } from 'react';
import { App, Form, Input, InputNumber, Select } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { adjustLeaveGrant, getLeaveGrants, getLeaveTypes, grantAnnualLeave, leaveGrantsKey, leaveTypesKey } from '../../../api/leaves';
import { fetchAllPages, getEmployees } from '../../../api/employees';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { Field, Pill, Table } from '../attendance/shared';
import { yearChoices } from './BalancesTab';

const KIND_TONE = { REGULAR: 'info', HIRE: 'ok', ADJUSTMENT: 'gray' };

function useEmployeeOptions() {
  const q = useQuery({ queryKey: ['employees', 'all-options'], queryFn: () => fetchAllPages(getEmployees, {}), staleTime: 5 * 60 * 1000 });
  return useMemo(() => (q.data ?? []).map((e) => ({ value: e.id, label: `${e.name} · ${e.orgUnitName ?? ''} · ${e.employeeNo}` })), [q.data]);
}

export default function GrantsTab() {
  const employees = useEmployeeOptions();
  const [viewEmp, setViewEmp] = useState(null);
  const [viewYear, setViewYear] = useState(dayjs().year());
  return (
    <>
      <AnnualCard />
      <AdjustCard employees={employees} onSaved={(empId, year) => { setViewEmp(empId); setViewYear(year); }} />
      <section className="lv-card">
        <h3>부여 내역</h3>
        <div className="at-filters">
          <Field label="직원" width="16rem">
            <Select value={viewEmp} onChange={setViewEmp} placeholder="직원을 고르세요" options={employees} showSearch optionFilterProp="label" />
          </Field>
          <Field label="휴가 연도" width="8rem">
            <Select value={viewYear} onChange={setViewYear} options={yearChoices()} />
          </Field>
        </div>
        <GrantHistory employeeId={viewEmp} year={viewYear} />
      </section>
    </>
  );
}

/* ───── 연도 일괄 부여 ───── */
function AnnualCard() {
  const { modal, message } = App.useApp();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [year, setYear] = useState(dayjs().year());
  const grant = useMutation({
    mutationFn: () => grantAnnualLeave(year),
    onSuccess: (r) => {
      queryClient.invalidateQueries({ queryKey: ['leave-balances'] });
      queryClient.invalidateQueries({ queryKey: ['leave-grants'] });
      message.success(`${r.leaveYear}년 휴가를 부여했습니다 — 생성 ${r.created}건 · 건너뜀 ${r.skipped}건`, 5);
    },
    onError: (err) => notify.error(err),
  });
  const ask = () =>
    modal.confirm({
      title: `${year}년 휴가를 일괄 부여할까요?`,
      content: '재직 · 휴직 직원에게 정기 휴가를 부여합니다. 이미 부여된 직원은 건너뜁니다.',
      okText: '일괄 부여',
      cancelText: '취소',
      onOk: () => grant.mutateAsync(),
    });
  return (
    <section className="lv-card">
      <h3>연도 일괄 부여</h3>
      <div className="at-filters">
        <Field label="휴가 연도" width="8rem">
          <Select value={year} onChange={setYear} options={yearChoices()} />
        </Field>
        <button type="button" className="at-btn is-wide" disabled={grant.isPending} onClick={ask}>
          일괄 부여
        </button>
      </div>
      <p className="at-muted">재직·휴직 직원에게 정기 부여합니다. 이미 부여된 직원은 건너뜁니다.</p>
    </section>
  );
}

/* ───── 개별 조정 ───── */
function AdjustCard({ employees, onSaved }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const types = useQuery({ queryKey: leaveTypesKey, queryFn: getLeaveTypes });
  const save = useMutation({
    mutationFn: (v) => adjustLeaveGrant({ employeeId: v.employeeId, leaveTypeId: v.leaveTypeId, leaveYear: v.leaveYear, days: v.days, reason: v.reason.trim() }),
    onSuccess: (r, v) => {
      queryClient.invalidateQueries({ queryKey: ['leave-balances'] });
      queryClient.invalidateQueries({ queryKey: ['leave-grants'] });
      notify.success(`${r.leaveTypeName} ${r.days > 0 ? `+${r.days}` : r.days}일을 조정했습니다`);
      form.setFieldsValue({ days: null, reason: '' });
      onSaved(v.employeeId, v.leaveYear);
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });
  return (
    <section className="lv-card">
      <h3>개별 조정</h3>
      <Form form={form} layout="vertical" requiredMark={false} className="lv-form" initialValues={{ leaveYear: dayjs().year() }} onFinish={(v) => save.mutate(v)}>
        <div className="lv-form-row">
          <Form.Item name="employeeId" label="직원" rules={[{ required: true, message: '직원을 고르세요' }]}>
            <Select placeholder="직원을 고르세요" options={employees} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="leaveTypeId" label="휴가 종류" rules={[{ required: true, message: '휴가 종류를 고르세요' }]}>
            <Select placeholder="휴가 종류" options={(types.data ?? []).map((t) => ({ value: t.id, label: t.name }))} />
          </Form.Item>
          <Form.Item name="leaveYear" label="휴가 연도">
            <Select options={yearChoices()} />
          </Form.Item>
          <Form.Item name="days" label="일수 (+/-)" rules={[{ required: true, message: '일수를 입력하세요' }, { validator: (_, v) => (v === 0 ? Promise.reject(new Error('0일은 조정할 수 없습니다')) : Promise.resolve()) }]}>
            <InputNumber className="full-width" min={-365} max={365} precision={0} placeholder="예: -1, 2" />
          </Form.Item>
        </div>
        <Form.Item name="reason" label="사유*" rules={[{ required: true, whitespace: true, message: '사유를 입력하세요' }]}>
          <Input.TextArea maxLength={255} autoSize={{ minRows: 2, maxRows: 4 }} placeholder="예: 중복 산정된 입사 월차 1일 정정" />
        </Form.Item>
        <div className="lv-form-foot">
          <span className="at-muted">잔여가 음수가 되는 조정은 할 수 없습니다.</span>
          <button type="submit" className="at-btn is-wide" disabled={save.isPending}>
            조정 저장
          </button>
        </div>
      </Form>
    </section>
  );
}

function GrantHistory({ employeeId, year }) {
  const q = useQuery({ queryKey: leaveGrantsKey(employeeId, year), queryFn: () => getLeaveGrants(employeeId, year), enabled: !!employeeId });
  const rows = q.data ?? [];
  return (
    <div className="lv-history">
      <Table
        cols={[
          { key: 'type', label: '휴가 종류', width: '7rem' },
          { key: 'year', label: '연도', width: '5rem' },
          { key: 'kind', label: '구분', width: '5.5rem' },
          { key: 'days', label: '일수', width: '4.5rem', align: 'right' },
          { key: 'reason', label: '사유' },
          { key: 'by', label: '등록자', width: '6rem' },
          { key: 'at', label: '일시', width: '9rem' },
        ]}
        loading={!!employeeId && q.isLoading}
        error={q.error && errorMessage(q.error)}
        empty={!employeeId ? '직원을 고르면 부여 내역이 보입니다' : rows.length === 0 && `${year}년 부여 내역이 없습니다`}
      >
        {rows.map((g) => (
          <tr key={g.id}>
            <td>{g.leaveTypeName}</td>
            <td>{g.leaveYear}</td>
            <td>
              <Pill tone={KIND_TONE[g.grantType]}>{labelOf('leaveGrantType', g.grantType)}</Pill>
            </td>
            <td className="is-right">{g.days > 0 ? `+${g.days}` : g.days}</td>
            <td className="at-ellipsis" title={g.reason}>{g.reason || '–'}</td>
            <td>{g.createdByName ?? '자동'}</td>
            <td>{dayjs(g.createdAt).format('YYYY-MM-DD HH:mm')}</td>
          </tr>
        ))}
      </Table>
    </div>
  );
}
