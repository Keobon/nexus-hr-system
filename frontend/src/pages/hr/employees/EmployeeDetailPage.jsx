// 직원 상세(F-EMP-06) · 관리자 수정(F-EMP-03) · 재직상태 변경(F-EMP-04)
// Figma HR_employee_detail · MODAL_employee_edit · MODAL_employment_status · MODAL_confirm
// 고치는 곳(10/21 정리): 이름 · 이메일 · 연락처 · 주소 · 비상 연락처 · 고용형태 → [기본정보 수정] · 부서 · 직급 · 직책 → [인사발령 등록](발령 이력)
// · 재직상태 → [재직상태 변경] · 부양가족수는 가족 정보에서 자동 · 입사일 · 사원번호는 바꿀 수 없다
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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
import { employeeSalariesKey, getEmployeeSalaries } from '../../../api/payroll';
import { employmentTypesApi } from '../../../api/orgSettings';
import { useMe } from '../../../auth/useMe';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf, optionsOf } from '../../../constants/labels';
import { formatMoney } from '../../../utils/format';
import { ConfirmModal, EmpStatusBadge, ModalFoot, PageHead, useOrgOptions } from '../parts';

const KV = ({ k, children }) => (
  <div className="hr-kv">
    <span className="k">{k}</span>
    <span className="v">{children}</span>
  </div>
);
const has = (v) => v !== undefined;

/** 급여 — 지금 급여(방식 · 연봉 · 월 기본급 · 적용 시작일) + 고정 수당 · 공제 + 최근 변경 3건 */
function SalaryCard({ s }) {
  const cur = s.current;
  const items = s.payItems ?? [];
  return (
    <div className="hr-card hr-info" style={{ gap: '1rem' }}>
      <p className="hr-card-title">급여</p>
      {!s.payrollEligible && <p className="hr-note">급여 대상이 아닌 직원입니다.</p>}
      {s.payrollEligible && !cur && <p className="hr-note">등록된 급여가 없습니다. 급여 화면에서 등록하세요.</p>}
      {cur && (
        <div className="hr-row2">
          <div className="hr-info">
            <KV k="급여 방식">{labelOf('salaryType', cur.salaryType)}</KV>
            <KV k="연봉">{cur.annualSalary != null ? formatMoney(cur.annualSalary) : '-'}</KV>
            <KV k="월 기본급">{formatMoney(cur.monthlyBase)}</KV>
            <KV k="적용 시작일">{cur.effectiveFrom}</KV>
          </div>
          <div className="hr-info">
            {items.length === 0 && <KV k="고정 수당 · 공제">-</KV>}
            {items.map((p) => (
              <KV key={p.payItemId} k={`${p.name} · ${p.itemKind === 'DEDUCTION' ? '공제' : '수당'}`}>
                {p.currentAmount != null ? formatMoney(p.currentAmount) : '자동 계산'}
              </KV>
            ))}
          </div>
        </div>
      )}
      {(s.salaries ?? []).length > 0 && (
        <div className="hr-table is-sm">
          <div className="hr-tr is-head">
            <span style={{ width: '9rem' }}>적용 시작일</span>
            <span style={{ width: '7rem' }}>방식</span>
            <span style={{ width: '10rem' }}>연봉</span>
            <span style={{ width: '10rem' }}>월 기본급</span>
            <span>사유</span>
          </div>
          {s.salaries.slice(0, 3).map((r) => (
            <div key={r.id} className="hr-tr">
              <span className="hr-strong" style={{ width: '9rem' }}>{r.effectiveFrom}</span>
              <span style={{ width: '7rem' }}>{labelOf('salaryType', r.salaryType)}</span>
              <span style={{ width: '10rem' }}>{r.annualSalary != null ? formatMoney(r.annualSalary) : '-'}</span>
              <span style={{ width: '10rem' }}>{formatMoney(r.monthlyBase)}</span>
              <span>{r.reason || '-'}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function EmployeeDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { can } = useMe();
  const org = useOrgOptions();
  const detail = useQuery({ queryKey: employeeKey(id), queryFn: () => getEmployee(id) });
  // 재직상태 이력은 EMPLOYEE_READ 전사 범위만 — 못 보면 카드를 숨긴다
  const history = useQuery({ queryKey: employmentStatusHistoryKey(id), queryFn: () => getEmploymentStatusHistory(id), retry: false });
  // 연봉 · 급여는 급여 조회 권한(PAYROLL_READ)이 있을 때만 — 목록의 "연봉" 칸을 여기로 옮겼다(10/18 설계)
  const salaries = useQuery({ queryKey: employeeSalariesKey(id), queryFn: () => getEmployeeSalaries(id), enabled: can('PAYROLL_READ'), retry: false });
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
          <button type="button" className="hr-btn is-secondary" disabled={resigned} onClick={() => setEditOpen(true)}>기본정보 수정</button>
        )}
        {can('ASSIGNMENT_MANAGE') && (
          <button type="button" className="hr-btn is-secondary" disabled={resigned} onClick={() => navigate(`/assignments/new?employeeId=${e.id}`)}>인사발령 등록</button>
        )}
        {manage && (
          <button type="button" className="hr-btn" disabled={resigned} onClick={() => setStatusTarget(e.status === 'ON_LEAVE' ? 'ACTIVE' : 'ON_LEAVE')}>재직상태 변경</button>
        )}
      </PageHead>

      <div className="hr-row2">
        <div className="hr-card hr-info">
          <p className="hr-card-title">기본정보</p>
          <KV k="이름">{e.name}</KV>
          <KV k="사원번호">{e.employeeNo}</KV>
          <KV k="이메일">{e.email ?? '-'}</KV>
          <KV k="연락처">{e.phone || '-'}</KV>
          {e.address !== undefined && <KV k="주소">{e.address || '-'}</KV>}
          {e.emergencyName !== undefined && (
            <KV k="비상 연락처">{[e.emergencyName, e.emergencyRelation && `(${e.emergencyRelation})`, e.emergencyPhone].filter(Boolean).join(' ') || '-'}</KV>
          )}
          {has(e.dependentsCount) && (
            <KV k="부양가족수">
              {`${e.dependentsCount}명`} <em className="hr-hint-inline">가족 정보에 등록하면 자동으로 셉니다</em>
            </KV>
          )}
        </div>
        <div className="hr-card hr-info">
          <p className="hr-card-title">소속정보</p>
          {division && <KV k="본부">{division}</KV>}
          <KV k="부서">{e.orgUnitName ?? '-'}</KV>
          <KV k="직급">{e.jobGradeName ?? '-'}</KV>
          <KV k="직책">{e.jobTitleName ?? '-'}</KV>
          <KV k="고용형태">{e.employmentTypeName ?? '-'}</KV>
          {has(e.hireDate) && <KV k="입사일">{e.hireDate}</KV>}
          <KV k="재직상태"><EmpStatusBadge status={e.status} /></KV>
          <p className="hr-note">※ 부서·직급·직책은 [인사발령 등록]으로 바꾸며 발령 이력에 남습니다. 고용형태는 [기본정보 수정], 재직상태는 [재직상태 변경]에서 바꿉니다. 입사일 · 사원번호는 바꿀 수 없습니다.</p>
        </div>
      </div>

      {salaries.data && <SalaryCard s={salaries.data} />}

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

/** MODAL_employee_edit — 이름 · 이메일 · 연락처 · 주소 · 비상 연락처 · 고용형태. 부양가족수는 가족 정보에서 세는 값이라 여기서 못 바꾼다(BR-EMP-007) */
function EditModal({ employee: e, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const full = has(e.employmentTypeId); // 전사 범위에서만 비상 연락처 · 고용형태가 온다
  const types = useQuery({ queryKey: employmentTypesApi.key(false), queryFn: () => employmentTypesApi.list(false), enabled: full });
  // 사용 중지된 고용형태도 지금 값이면 보여 준다
  const typeOptions = (types.data ?? [])
    .filter((t) => t.isActive || t.id === e.employmentTypeId)
    .map((t) => ({ value: t.id, label: t.isActive ? t.name : `${t.name} (사용 중지)` }));
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
  const t = (v) => v?.trim() || null;
  const phoneRule = [{ pattern: /^[0-9-]{9,20}$/, message: '숫자와 - 만 입력하세요 (예: 010-1234-5678)' }];
  return (
    <Modal open title="직원 기본정보 수정" footer={null} onCancel={onClose} width={560} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{[e.name, e.employeeNo, e.orgUnitName, e.jobGradeName].filter(Boolean).join(' · ')}</p>
      <Form form={form} layout="vertical" requiredMark={false}
        initialValues={{ name: e.name, phone: e.phone, email: e.email, address: e.address, emergencyName: e.emergencyName, emergencyRelation: e.emergencyRelation, emergencyPhone: e.emergencyPhone, employmentTypeId: e.employmentTypeId }}
        onFinish={(v) =>
          save.mutate({
            name: v.name.trim(),
            email: v.email.trim(),
            phone: t(v.phone),
            ...(e.address !== undefined && { address: t(v.address) }),
            ...(full && { emergencyName: t(v.emergencyName), emergencyRelation: t(v.emergencyRelation), emergencyPhone: t(v.emergencyPhone), employmentTypeId: v.employmentTypeId }),
          })
        }>
        <div className="hr-form-row">
          <Form.Item name="name" label="이름" rules={[{ required: true, whitespace: true, message: '이름을 입력하세요' }]}><Input maxLength={50} /></Form.Item>
          <Form.Item name="email" label="이메일" rules={[{ required: true, message: '이메일을 입력하세요' }, { type: 'email', message: '이메일 형식이 올바르지 않습니다' }]}>
            <Input maxLength={100} />
          </Form.Item>
        </div>
        <div className="hr-form-row">
          <Form.Item name="phone" label="연락처" rules={phoneRule}><Input placeholder="010-0000-0000" maxLength={20} /></Form.Item>
          {full ? (
            <Form.Item name="employmentTypeId" label="고용형태" rules={[{ required: true, message: '고용형태를 고르세요' }]}>
              <Select options={typeOptions} loading={types.isLoading} />
            </Form.Item>
          ) : <div />}
        </div>
        {e.address !== undefined && (
          <Form.Item name="address" label="주소">
            <Input maxLength={255} placeholder="예: 서울특별시 강남구 테헤란로 123, 7층" />
          </Form.Item>
        )}
        {full && (
          <div className="hr-form-row">
            <Form.Item name="emergencyName" label="비상 연락처 이름"><Input maxLength={50} placeholder="예: 홍길순" /></Form.Item>
            <Form.Item name="emergencyRelation" label="관계"><Input maxLength={20} placeholder="예: 배우자" /></Form.Item>
            <Form.Item name="emergencyPhone" label="비상 연락처 전화" rules={phoneRule}><Input maxLength={20} placeholder="010-0000-0000" /></Form.Item>
          </div>
        )}
        {has(e.dependentsCount) && (
          <Form.Item label="부양가족수" extra="직접 입력하지 않습니다. 가족 정보에 등록하면 자동으로 셉니다.">
            <Input value={`${e.dependentsCount}명`} disabled />
          </Form.Item>
        )}
        <p className="hr-notice">부서 · 직급 · 직책은 [인사발령 등록]에서, 재직상태는 [재직상태 변경]에서 바꿉니다. 입사일 · 사원번호는 바꿀 수 없습니다.</p>
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
