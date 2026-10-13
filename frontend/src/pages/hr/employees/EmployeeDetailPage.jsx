// 직원 상세(F-EMP-06) · 관리자 수정(F-EMP-03) · 재직상태 변경(F-EMP-04)
// Figma HR_employee_detail · MODAL_employee_edit · MODAL_employment_status · MODAL_confirm
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { DatePicker, Form, Input, Modal, Result, Select, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  changeEmploymentStatus,
  employeeKey,
  employmentStatusHistoryKey,
  getEmployee,
  getEmploymentStatusHistory,
  updateEmployee,
} from '../../../api/employees';
import { useMe } from '../../../auth/useMe';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf, optionsOf } from '../../../constants/labels';
import { ConfirmModal, EmpStatusBadge, ModalFoot, PageHead, useOrgOptions } from '../parts';

const KV = ({ k, children }) => (
  <div className="hr-kv">
    <span className="k">{k}</span>
    <span className="v">{children}</span>
  </div>
);
const has = (v) => v !== undefined;

export default function EmployeeDetailPage() {
  const { id } = useParams();
  const { can } = useMe();
  const org = useOrgOptions();
  const detail = useQuery({ queryKey: employeeKey(id), queryFn: () => getEmployee(id) });
  // 재직상태 이력은 EMPLOYEE_READ 전사 범위만 — 못 보면 카드를 숨긴다
  const history = useQuery({ queryKey: employmentStatusHistoryKey(id), queryFn: () => getEmploymentStatusHistory(id), retry: false });
  const [editOpen, setEditOpen] = useState(false);
  const [statusTarget, setStatusTarget] = useState(null);

  if (detail.isLoading) return <Spin />;
  if (detail.error) return <Result status="warning" title={errorMessage(detail.error)} />;
  const e = detail.data;
  const manage = can('EMPLOYEE_MANAGE');
  const resigned = e.status === 'RESIGNED';
  const unit = has(e.orgUnitId) ? org.byId(e.orgUnitId) : null;
  const division = unit?.parent && unit.parent.depth > 0 ? unit.parent.name : null;

  // 이력은 시간순 — 변경 전은 바로 앞 줄의 상태, 첫 줄은 신규 등록
  const rows = (history.data ?? []).map((h, i, all) => ({ ...h, before: i === 0 ? null : all[i - 1].status })).reverse();

  return (
    <div className="hr">
      <PageHead title="직원 상세" desc="기본·소속 정보와 재직상태 이력을 확인하고 재직상태를 관리합니다.">
        {manage && (
          <>
            <button type="button" className="hr-btn is-secondary" disabled={resigned} onClick={() => setEditOpen(true)}>기본정보 수정</button>
            {e.status === 'ON_LEAVE' ? (
              <button type="button" className="hr-btn is-secondary" onClick={() => setStatusTarget('ACTIVE')}>복직 처리</button>
            ) : (
              <button type="button" className="hr-btn is-secondary" disabled={resigned} onClick={() => setStatusTarget('ON_LEAVE')}>휴직 처리</button>
            )}
            <button type="button" className="hr-btn is-danger" disabled={resigned} onClick={() => setStatusTarget('RESIGNED')}>퇴직 처리</button>
          </>
        )}
      </PageHead>

      <div className="hr-row2">
        <div className="hr-card hr-info">
          <p className="hr-card-title">기본정보</p>
          <KV k="이름">{e.name}</KV>
          <KV k="사원번호">{e.employeeNo}</KV>
          <KV k="이메일">{e.email ?? '-'}</KV>
          <KV k="연락처">{e.phone || '-'}</KV>
          {has(e.dependentsCount) && <KV k="부양가족수">{`${e.dependentsCount}명`}</KV>}
        </div>
        <div className="hr-card hr-info">
          <p className="hr-card-title">소속정보</p>
          {division && <KV k="본부">{division}</KV>}
          <KV k="부서">{e.orgUnitName ?? '-'}</KV>
          <KV k="직급">{e.jobGradeName ?? '-'}</KV>
          <KV k="직책">{e.jobTitleName ?? '-'}</KV>
          {has(e.hireDate) && <KV k="입사일">{e.hireDate}</KV>}
          <KV k="재직상태"><EmpStatusBadge status={e.status} /></KV>
          <p className="hr-note">※ 부서·직급·직책 변경은 인사발령 화면에서 처리합니다.</p>
        </div>
      </div>

      {history.data && (
        <div className="hr-card hr-info" style={{ gap: '1rem' }}>
          <p className="hr-card-title">재직상태 이력</p>
          <div className="hr-table is-sm">
            <div className="hr-tr is-head">
              <span style={{ width: '10rem' }}>발효일</span>
              <span style={{ width: '10rem' }}>변경 전</span>
              <span style={{ width: '10rem' }}>변경 후</span>
              <span>사유</span>
            </div>
            {rows.map((h) => (
              <div key={h.id} className="hr-tr">
                <span className="hr-strong" style={{ width: '10rem' }}>{h.effectiveDate}</span>
                <span style={{ width: '10rem' }}>{h.before ? labelOf('employmentStatus', h.before) : '신규 등록'}</span>
                <span style={{ width: '10rem' }}><EmpStatusBadge status={h.status} /></span>
                <span>{h.reason || '-'}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {editOpen && <EditModal employee={e} onClose={() => setEditOpen(false)} />}
      {statusTarget && <StatusModal employee={e} target={statusTarget} onClose={() => setStatusTarget(null)} />}
    </div>
  );
}

/** MODAL_employee_edit — 이름 · 연락처 · 이메일. 부양가족수는 가족 정보에서 세는 값이라 여기서 못 바꾼다(BR-EMP-007) */
function EditModal({ employee: e, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const save = useMutation({
    mutationFn: (body) => updateEmployee(e.id, body),
    onSuccess: (data) => {
      queryClient.setQueryData(employeeKey(e.id), data);
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      notify.success('기본정보를 저장했습니다');
      onClose();
    },
    onError: (err) => { if (!applyFieldErrors(form, err)) notify.error(err); },
  });
  return (
    <Modal open title="직원 기본정보 수정" footer={null} onCancel={onClose} width={440} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{[e.name, e.orgUnitName, e.jobGradeName].filter(Boolean).join(' · ')}</p>
      <Form form={form} layout="vertical" requiredMark={false} initialValues={{ name: e.name, phone: e.phone, email: e.email }}
        onFinish={(v) => save.mutate({ name: v.name.trim(), phone: v.phone?.trim() || null, email: v.email.trim() })}>
        <Form.Item name="name" label="이름" rules={[{ required: true, message: '이름을 입력하세요' }]}><Input maxLength={50} /></Form.Item>
        <Form.Item name="phone" label="연락처"><Input placeholder="010-0000-0000" maxLength={20} /></Form.Item>
        <Form.Item name="email" label="이메일" rules={[{ required: true, message: '이메일을 입력하세요' }, { type: 'email', message: '이메일 형식이 올바르지 않습니다' }]}>
          <Input maxLength={100} />
        </Form.Item>
        {has(e.dependentsCount) && (
          <Form.Item label="부양가족수" extra="가족 정보에서 자동으로 계산됩니다">
            <Input value={`${e.dependentsCount}명`} disabled />
          </Form.Item>
        )}
        <p className="hr-notice">부서·직급·직책은 인사발령에서 변경합니다.</p>
        <ModalFoot onCancel={onClose} busy={save.isPending} />
      </Form>
    </Modal>
  );
}

/** MODAL_employment_status — 퇴직은 MODAL_confirm 을 한 번 더 */
function StatusModal({ employee: e, target, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [pending, setPending] = useState(null);
  const status = Form.useWatch('status', form) ?? target;

  const save = useMutation({
    mutationFn: (body) => changeEmploymentStatus(e.id, body),
    onSuccess: (data, body) => {
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['org-units'] });
      notify.success(`${e.name} → ${labelOf('employmentStatus', body.status)}`, data);
      onClose();
    },
  });

  const toBody = (v) => ({ status: v.status, effectiveDate: v.effectiveDate.format('YYYY-MM-DD'), reason: v.reason.trim() });
  const submit = async (v) => {
    if (v.status === 'RESIGNED') { setPending(toBody(v)); return; }
    try { await save.mutateAsync(toBody(v)); } catch (err) { if (!applyFieldErrors(form, err)) notify.error(err); }
  };

  return (
    <>
      <Modal open={!pending} title="재직상태 변경" footer={null} onCancel={onClose} width={440} className="hr-modal" destroyOnHidden centered>
        <p className="hr-modal-sub">{[e.name, e.orgUnitName, e.jobGradeName].filter(Boolean).join(' · ')}</p>
        <Form form={form} layout="vertical" requiredMark={false} onFinish={submit}
          initialValues={{ status: target, effectiveDate: dayjs(), reason: '' }}>
          <Form.Item label="현재 상태"><EmpStatusBadge status={e.status} /></Form.Item>
          <Form.Item name="status" label="변경할 상태" rules={[{ required: true, message: '상태를 선택하세요' }]}>
            <Select options={optionsOf('employmentStatus').filter((o) => o.value !== e.status)} />
          </Form.Item>
          <Form.Item name="reason" label="사유" rules={[{ required: true, whitespace: true, message: '사유를 입력하세요' }]}>
            <Input.TextArea rows={3} maxLength={255} placeholder="예: 육아휴직 신청" />
          </Form.Item>
          <Form.Item name="effectiveDate" label="발효일" rules={[{ required: true, message: '발효일을 선택하세요' }]}
            extra={status === 'RESIGNED' ? '퇴직은 마지막 근무일 다음 날을 고릅니다.' : undefined}>
            <DatePicker className="full-width" format="YYYY-MM-DD" disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
          </Form.Item>
          <ModalFoot onCancel={onClose} okText="확인" busy={save.isPending} danger={status === 'RESIGNED'} />
        </Form>
      </Modal>
      <ConfirmModal open={!!pending} danger okText="퇴직 처리"
        desc="퇴직 처리하면 계정이 비활성화되고 조직장에서 해제되며, 대기 중인 신청과 승인 단계도 함께 정리됩니다. 이 작업은 되돌릴 수 없습니다. 계속하시겠습니까?"
        onClose={() => setPending(null)}
        onConfirm={() => save.mutateAsync(pending)} />
    </>
  );
}
