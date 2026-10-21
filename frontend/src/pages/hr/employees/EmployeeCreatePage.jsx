// 직원 등록(F-EMP-01) — Figma HR_employee_create
// 고용형태는 Figma 에 없지만 API 필수값이라 넣었다. 역할은 ROLE_MANAGE 가 있을 때만 고른다(없으면 서버가 기본 역할 "직원"). 임시 비밀번호는 응답에서 한 번만 받으므로 등록 직후 창으로 보여 준다(F-AUTH-04).
// 급여(10/15 시연 피드백)는 PAYROLL_MANAGE 가 있을 때만 — 직원을 만든 뒤 POST /employees/{id}/salaries(적용 시작일 = 입사일). 비우면 급여 › 직원 급여에서 나중에 등록
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DatePicker, Form, Input, InputNumber, Modal, Segmented, Select } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMe } from '../../../auth/useMe';
import { createEmployee } from '../../../api/employees';
import { registerSalary } from '../../../api/payrollAdmin';
import { getRoles, rolesKey } from '../../../api/roles';
import { employmentTypesApi, jobGradesApi, jobTitlesApi } from '../../../api/orgSettings';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { formatMoney } from '../../../utils/format';
import { PageHead, useOrgOptions } from '../parts';

const opts = (list) => (list ?? []).map((x) => ({ value: x.id, label: x.name }));
const money = { formatter: (v) => (v || v === 0 ? `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''), parser: (v) => v.replace(/,/g, '') };

/** onCancel · onDone — 초기 설정 마법사 안에서 쓸 때(목록 · 상세로 가지 않고 마법사에 남는다) */
export default function EmployeeCreatePage({ onCancel, onDone }) {
  const navigate = useNavigate();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [created, setCreated] = useState(null);
  const org = useOrgOptions();
  const grades = useQuery({ queryKey: jobGradesApi.key(true), queryFn: () => jobGradesApi.list(true) });
  const titles = useQuery({ queryKey: jobTitlesApi.key(true), queryFn: () => jobTitlesApi.list(true) });
  const types = useQuery({ queryKey: employmentTypesApi.key(true), queryFn: () => employmentTypesApi.list(true) });
  const { can } = useMe();
  const roleManage = can('ROLE_MANAGE');
  const roles = useQuery({ queryKey: rolesKey, queryFn: getRoles, enabled: roleManage });
  const payrollManage = can('PAYROLL_MANAGE');
  const salaryType = Form.useWatch('salaryType', form) ?? 'ANNUAL';

  const save = useMutation({
    // 직원을 만든 뒤 급여를 넣는다 — 급여만 실패하면 직원은 등록된 채로 완료 창에서 안내한다
    mutationFn: async ({ employee, salary }) => {
      const data = await createEmployee(employee);
      if (!salary) return data;
      try {
        await registerSalary(data.id, salary);
        return { ...data, salary };
      } catch (err) {
        return { ...data, salaryError: errorMessage(err) };
      }
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['assignments'] });
      queryClient.invalidateQueries({ queryKey: ['salaries'] });
      setCreated(data);
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });

  const onFinish = (v) => {
    const amount = v.salaryType === 'MONTHLY' ? v.monthlyBase : v.annualSalary;
    const salary = payrollManage && amount
      ? {
          salaryType: v.salaryType,
          annualSalary: v.salaryType === 'ANNUAL' ? amount : null,
          monthlyBase: v.salaryType === 'MONTHLY' ? amount : null,
          effectiveFrom: v.hireDate.format('YYYY-MM-DD'),
          reason: '입사 계약',
        }
      : null;
    save.mutate({ employee: toEmployee(v), salary });
  };
  const toEmployee = (v) => ({
    name: v.name.trim(),
    email: v.email.trim(),
    orgUnitId: v.orgUnitId,
    jobGradeId: v.jobGradeId,
    jobTitleId: v.jobTitleId ?? null,
    employmentTypeId: v.employmentTypeId,
    roleId: roleManage ? (v.roleId ?? null) : null,
    hireDate: v.hireDate.format('YYYY-MM-DD'),
    phone: v.phone?.trim() || null,
    address: v.address?.trim() || null,
    emergencyName: v.emergencyName?.trim() || null,
    emergencyRelation: v.emergencyRelation?.trim() || null,
    emergencyPhone: v.emergencyPhone?.trim() || null,
  });

  const req = (message) => [{ required: true, message }];
  // 전화번호 — 숫자 · 하이픈만(비워 둘 수 있다)
  const phoneRule = [{ pattern: /^[0-9-]{9,20}$/, message: '숫자와 - 만 입력하세요 (예: 010-1234-5678)' }];

  return (
    <div className="hr">
      <PageHead title="직원 등록" desc="신규 입사자의 기본정보와 소속 정보를 입력합니다. * 표시는 필수 항목입니다." />

      <Form form={form} layout="vertical" requiredMark={false} onFinish={onFinish} className="hr-card hr-form">
        <p className="hr-card-title">직원 기본정보</p>
        <div className="hr-form-row">
          <Form.Item name="name" label="이름 *" rules={req('이름을 입력하세요')}>
            <Input placeholder="이름을 입력하세요" maxLength={50} />
          </Form.Item>
          <Form.Item name="email" label="이메일 *" rules={[...req('이메일을 입력하세요'), { type: 'email', message: '이메일 형식이 올바르지 않습니다' }]}>
            <Input placeholder="name@nexuslabs.co.kr" maxLength={100} />
          </Form.Item>
        </div>
        <div className="hr-form-row">
          <Form.Item name="orgUnitId" label="부서 *" rules={req('부서를 선택하세요')}>
            <Select placeholder="부서를 선택하세요" options={org.options} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="jobGradeId" label="직급 *" rules={req('직급을 선택하세요')}>
            <Select placeholder="직급을 선택하세요" options={opts(grades.data)} />
          </Form.Item>
        </div>
        <div className="hr-form-row">
          <Form.Item name="jobTitleId" label="직책">
            <Select placeholder="직책을 선택하세요 (선택)" allowClear options={opts(titles.data)} />
          </Form.Item>
          <Form.Item name="hireDate" label="입사일 *" rules={req('입사일을 선택하세요')}>
            <DatePicker className="full-width" placeholder="YYYY-MM-DD" format="YYYY-MM-DD" />
          </Form.Item>
        </div>
        <div className="hr-form-row">
          <Form.Item name="employmentTypeId" label="고용형태 *" rules={req('고용형태를 선택하세요')}>
            <Select placeholder="고용형태를 선택하세요" options={opts(types.data)} />
          </Form.Item>
          {roleManage ? (
            <Form.Item name="roleId" label="역할" extra="비워 두면 '직원' 역할로 만들어집니다. 나중에 계정 관리에서 바꿀 수 있습니다.">
              <Select placeholder="직원 (기본)" allowClear loading={roles.isLoading} options={opts(roles.data)} />
            </Form.Item>
          ) : (
            <div />
          )}
        </div>

        {payrollManage && (
          <>
            <p className="hr-card-title" style={{ marginTop: '0.5rem' }}>급여</p>
            <div className="hr-form-row">
              <Form.Item name="salaryType" label="급여 방식" initialValue="ANNUAL">
                <Segmented block options={[{ value: 'ANNUAL', label: '연봉제' }, { value: 'MONTHLY', label: '월급제' }]} />
              </Form.Item>
              {salaryType === 'ANNUAL' ? (
                <Form.Item name="annualSalary" label="연봉" extra="적용 시작일은 입사일입니다. 비워 두면 나중에 급여 › 직원 급여에서 등록합니다.">
                  <InputNumber className="full-width" min={0} step={1000000} suffix="원" placeholder="예: 42,000,000" {...money} />
                </Form.Item>
              ) : (
                <Form.Item name="monthlyBase" label="월 기본급" extra="적용 시작일은 입사일입니다. 비워 두면 나중에 급여 › 직원 급여에서 등록합니다.">
                  <InputNumber className="full-width" min={0} step={100000} suffix="원" placeholder="예: 3,000,000" {...money} />
                </Form.Item>
              )}
            </div>
          </>
        )}

        <p className="hr-card-title" style={{ marginTop: '0.5rem' }}>연락처 · 주소</p>
        <div className="hr-form-row">
          <Form.Item name="phone" label="휴대전화" rules={phoneRule}>
            <Input placeholder="010-0000-0000" maxLength={20} />
          </Form.Item>
          <div />
        </div>
        <Form.Item name="address" label="주소">
          <Input placeholder="예: 서울특별시 강남구 테헤란로 123, 7층" maxLength={255} />
        </Form.Item>
        <div className="hr-form-row">
          <Form.Item name="emergencyName" label="비상 연락처 이름">
            <Input placeholder="예: 홍길순" maxLength={50} />
          </Form.Item>
          <Form.Item name="emergencyRelation" label="관계">
            <Input placeholder="예: 배우자 · 부모" maxLength={20} />
          </Form.Item>
          <Form.Item name="emergencyPhone" label="비상 연락처 전화" rules={phoneRule}>
            <Input placeholder="010-0000-0000" maxLength={20} />
          </Form.Item>
        </div>
      </Form>

      <p className="hr-notice">저장하면 직원 계정이 자동으로 생성되며, 재직상태는 '재직중'으로 등록됩니다.</p>
      <div className="hr-form-actions">
        <button type="button" className="hr-btn is-secondary" style={{ padding: '0 1.5rem' }} onClick={() => (onCancel ? onCancel() : navigate('/employees'))}>취소</button>
        <button type="button" className="hr-btn" style={{ padding: '0 1.5rem' }} disabled={save.isPending} onClick={() => form.submit()}>
          {save.isPending ? '저장 중…' : '저장'}
        </button>
      </div>

      <Modal open={!!created} title="직원이 등록되었습니다" footer={null} closable={false} mask={{ closable: false }} keyboard={false}
        width={440} className="hr-modal" centered>
        {created && (
          <>
            <p className="hr-modal-sub">{`${created.name} · 사원번호 ${created.employeeNo}`}</p>
            <div className="hr-secret">
              <span className="hr-label">임시 비밀번호</span>
              <div className="v">{created.temporaryPassword}</div>
              <button type="button" className="hr-btn is-secondary"
                onClick={() => navigator.clipboard?.writeText(created.temporaryPassword).then(() => notify.success('복사했습니다'))}>
                복사
              </button>
            </div>
            <p className="hr-notice" style={{ marginTop: '0.875rem' }}>
              이 창을 닫으면 임시 비밀번호를 다시 볼 수 없습니다. 직원은 첫 로그인 때 비밀번호를 바꿔야 합니다.
            </p>
            {created.salary && (
              <p className="hr-hint" style={{ marginTop: '0.75rem' }}>
                {`급여 등록 · ${labelOf('salaryType', created.salary.salaryType)} ${formatMoney(created.salary.annualSalary ?? created.salary.monthlyBase)}(${created.salary.effectiveFrom}부터)`}
              </p>
            )}
            {created.salaryError && (
              <p className="hr-note" style={{ marginTop: '0.75rem' }}>
                {`급여는 등록하지 못했습니다(${created.salaryError}). 급여 › 직원 급여에서 등록하세요.`}
              </p>
            )}
            {created.leaveGrants?.length > 0 && (
              <p className="hr-hint" style={{ marginTop: '0.75rem' }}>
                {`입사 휴가 자동 부여 · ${created.leaveGrants.map((g) => `${g.leaveTypeName} ${g.days}일(${labelOf('leaveGrantType', g.grantType)})`).join(', ')}`}
              </p>
            )}
            <div className="hr-modal-foot">
              <button type="button" className="hr-btn" onClick={() => { notify.success(`${created.name} 직원을 등록했습니다`); if (onDone) { setCreated(null); form.resetFields(); onDone(created); } else navigate(`/employees/${created.id}`); }}>
                확인
              </button>
            </div>
          </>
        )}
      </Modal>
    </div>
  );
}
