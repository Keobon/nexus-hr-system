// 인사발령 등록(F-ASSIGN-01) — Figma HR_assignment_create + MODAL_confirm
// 화면은 바꿀 값 하나만 고르고, 보낼 때는 나머지 두 칸을 지금 값으로 채운다(API 는 변경 후 값 셋을 모두 받는다).
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Form, Input, Select } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createAssignment, currentAssignmentsKey, getCurrentAssignments } from '../../../api/assignments';
import { jobGradesApi, jobTitlesApi } from '../../../api/orgSettings';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { labelOf, optionsOf } from '../../../constants/labels';
import { ConfirmModal, PageHead, useOrgOptions } from '../parts';
import { FIELD_OF, valueOptions } from './shared';

export default function AssignmentCreatePage() {
  const navigate = useNavigate();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [sp] = useSearchParams();
  const [form] = Form.useForm();
  const [pending, setPending] = useState(null);
  const org = useOrgOptions();
  const people = useQuery({ queryKey: currentAssignmentsKey({ size: 100 }), queryFn: () => getCurrentAssignments({ size: 100 }) });
  const grades = useQuery({ queryKey: jobGradesApi.key(true), queryFn: () => jobGradesApi.list(true) });
  const titles = useQuery({ queryKey: jobTitlesApi.key(true), queryFn: () => jobTitlesApi.list(true) });

  const employeeId = Form.useWatch('employeeId', form);
  const type = Form.useWatch('assignmentType', form) ?? 'TRANSFER';
  const cur = (people.data?.content ?? []).find((p) => p.employeeId === employeeId);
  const f = FIELD_OF[type];

  const save = useMutation({
    mutationFn: createAssignment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assignments'] });
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['org-units'] });
      notify.success('발령을 확정했습니다');
      navigate('/assignments');
    },
  });

  const onFinish = (v) => {
    const to = { toOrgUnitId: cur.orgUnitId, toJobGradeId: cur.jobGradeId, toJobTitleId: cur.jobTitleId ?? null };
    to[f.req] = v.toValue === 0 ? null : v.toValue;
    setPending({ employeeId: v.employeeId, assignmentType: v.assignmentType, ...to, reason: v.reason.trim() });
  };

  const confirm = async () => {
    try {
      await save.mutateAsync(pending);
    } catch (err) {
      if (applyFieldErrors(form, err)) { setPending(null); return; }
      throw err;
    }
  };

  return (
    <div className="hr">
      <PageHead title="인사발령 등록" desc="대상 직원의 부서·직급·직책 변경을 발령으로 등록합니다." />
      <Form form={form} layout="vertical" requiredMark={false} onFinish={onFinish} className="hr-card hr-form hr-form-narrow"
        initialValues={{ employeeId: sp.get('employeeId') ? Number(sp.get('employeeId')) : undefined, assignmentType: 'TRANSFER' }}>
        <Form.Item name="employeeId" label="대상 직원 *" rules={[{ required: true, message: '대상 직원을 선택하세요' }]}>
          <Select className="hr-select" style={{ minWidth: '12rem' }} popupMatchSelectWidth={false} showSearch optionFilterProp="label"
            placeholder="직원을 선택하세요" onChange={() => form.setFieldValue('toValue', undefined)}
            options={(people.data?.content ?? []).map((p) => ({ value: p.employeeId, label: `${p.name} (${p.orgUnitName})` }))} />
        </Form.Item>
        <div className="hr-readonly">
          <span className="hr-label">현재 소속 (읽기 전용)</span>
          <div className="values">
            <span>{`부서: ${cur?.orgUnitName ?? '-'}`}</span>
            <span>{`직급: ${cur?.jobGradeName ?? '-'}`}</span>
            <span>{`직책: ${cur ? cur.jobTitleName ?? '없음' : '-'}`}</span>
          </div>
        </div>
        <Form.Item name="assignmentType" label="발령 유형 *" rules={[{ required: true }]}>
          <Select className="hr-select" popupMatchSelectWidth={false} options={optionsOf('assignmentType')}
            onChange={() => form.setFieldValue('toValue', undefined)} />
        </Form.Item>
        <Form.Item name="toValue" label="변경 후 값 *" rules={[{ required: true, message: `바뀔 ${f.label}을(를) 선택하세요` }]}>
          <Select className="hr-select" style={{ minWidth: '10rem' }} popupMatchSelectWidth={false} showSearch optionFilterProp="label"
            placeholder="선택하세요" disabled={!cur}
            options={cur ? valueOptions(type, { orgOptions: org.options, grades: grades.data, titles: titles.data }, cur[f.cur] ?? null) : []} />
        </Form.Item>
        <Form.Item name="reason" label="사유 *" rules={[{ required: true, whitespace: true, message: '발령 사유를 입력하세요' }]}>
          <Input.TextArea rows={4} maxLength={255} placeholder="발령 사유를 입력하세요" />
        </Form.Item>
        <p className="hr-notice">발효일은 등록일(오늘)로 자동 설정되며 직접 입력할 수 없습니다. 급여 정보는 발령 등록에서 변경되지 않습니다.</p>
        <div>
          <button type="submit" className="hr-btn is-lg" disabled={save.isPending}>발령 확정</button>
        </div>
      </Form>

      <ConfirmModal open={!!pending} onClose={() => setPending(null)} onConfirm={confirm}
        desc={cur ? `${cur.name}님의 ${labelOf('assignmentType', type)} 발령을 오늘 날짜로 확정합니다. 이 작업은 되돌릴 수 없습니다. 계속하시겠습니까?` : undefined} />
    </div>
  );
}
