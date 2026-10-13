// 직원 등록(F-EMP-01) — Figma HR_employee_create
// 고용형태는 Figma 에 없지만 API 필수값이라 넣었다. 임시 비밀번호는 응답에서 한 번만 받으므로 등록 직후 창으로 보여 준다(F-AUTH-04).
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DatePicker, Form, Input, Modal, Select } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createEmployee } from '../../../api/employees';
import { employmentTypesApi, jobGradesApi, jobTitlesApi } from '../../../api/orgSettings';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { labelOf } from '../../../constants/labels';
import { PageHead, useOrgOptions } from '../parts';

const opts = (list) => (list ?? []).map((x) => ({ value: x.id, label: x.name }));

export default function EmployeeCreatePage() {
  const navigate = useNavigate();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [created, setCreated] = useState(null);
  const org = useOrgOptions();
  const grades = useQuery({ queryKey: jobGradesApi.key(true), queryFn: () => jobGradesApi.list(true) });
  const titles = useQuery({ queryKey: jobTitlesApi.key(true), queryFn: () => jobTitlesApi.list(true) });
  const types = useQuery({ queryKey: employmentTypesApi.key(true), queryFn: () => employmentTypesApi.list(true) });

  const save = useMutation({
    mutationFn: createEmployee,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['assignments'] });
      setCreated(data);
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });

  const onFinish = (v) =>
    save.mutate({
      name: v.name.trim(),
      email: v.email.trim(),
      orgUnitId: v.orgUnitId,
      jobGradeId: v.jobGradeId,
      jobTitleId: v.jobTitleId ?? null,
      employmentTypeId: v.employmentTypeId,
      hireDate: v.hireDate.format('YYYY-MM-DD'),
    });

  const req = (message) => [{ required: true, message }];

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
          <div />
        </div>
      </Form>

      <p className="hr-notice">저장하면 직원 계정이 자동으로 생성되며, 재직상태는 '재직중'으로 등록됩니다.</p>
      <div className="hr-form-actions">
        <button type="button" className="hr-btn is-secondary" style={{ padding: '0 1.5rem' }} onClick={() => navigate('/employees')}>취소</button>
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
            {created.leaveGrants?.length > 0 && (
              <p className="hr-hint" style={{ marginTop: '0.75rem' }}>
                {`입사 휴가 자동 부여 · ${created.leaveGrants.map((g) => `${g.leaveTypeName} ${g.days}일(${labelOf('leaveGrantType', g.grantType)})`).join(', ')}`}
              </p>
            )}
            <div className="hr-modal-foot">
              <button type="button" className="hr-btn" onClick={() => { notify.success(`${created.name} 직원을 등록했습니다`); navigate(`/employees/${created.id}`); }}>
                확인
              </button>
            </div>
          </>
        )}
      </Modal>
    </div>
  );
}
