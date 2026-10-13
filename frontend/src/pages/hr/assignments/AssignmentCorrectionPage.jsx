// 정정 발령 등록(F-ASSIGN-04) — Figma HR_assignment_correction
// 원본은 그대로 두고 정정 행을 추가한다. 유형은 원본과 같고, 바뀌는 칸만 고르면 나머지는 원본의 변경 후 값으로 채운다.
import { useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Form, Input, Result, Select, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { assignmentsKey, correctAssignment, getAssignments } from '../../../api/assignments';
import { jobGradesApi, jobTitlesApi } from '../../../api/orgSettings';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { labelOf } from '../../../constants/labels';
import { ConfirmModal, PageHead, useOrgOptions } from '../parts';
import { FIELD_OF, changeText, valueOptions } from './shared';

export default function AssignmentCorrectionPage() {
  const { id } = useParams();
  const { state } = useLocation();
  const navigate = useNavigate();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [pending, setPending] = useState(null);
  const org = useOrgOptions();
  const grades = useQuery({ queryKey: jobGradesApi.key(true), queryFn: () => jobGradesApi.list(true) });
  const titles = useQuery({ queryKey: jobTitlesApi.key(true), queryFn: () => jobTitlesApi.list(true) });

  // 목록에서 넘어오면 그 행을 쓰고, 주소로 바로 들어오면 최근 발령에서 찾는다(단건 조회 API 는 없다)
  const fromState = state?.assignment?.id === Number(id) ? state.assignment : null;
  const lookup = useQuery({
    queryKey: assignmentsKey({ size: 100 }),
    queryFn: () => getAssignments({ size: 100 }),
    enabled: !fromState,
  });
  const a = fromState ?? lookup.data?.content?.find((x) => x.id === Number(id));

  const save = useMutation({
    mutationFn: (body) => correctAssignment(a.id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assignments'] });
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['org-units'] });
      notify.success('정정 발령을 등록했습니다');
      navigate('/assignments');
    },
  });

  if (!fromState && lookup.isLoading) return <Spin />;
  if (!a) return <Result status="warning" title="발령을 찾을 수 없습니다" />;

  const f = FIELD_OF[a.assignmentType];
  const blocked = a.correctedById ? '이미 정정된 발령입니다. 가장 최근 정정본을 정정하세요.' : null;

  const onFinish = (v) => {
    const to = { toOrgUnitId: a.toOrgUnitId, toJobGradeId: a.toJobGradeId, toJobTitleId: a.toJobTitleId ?? null };
    to[f.req] = v.toValue === 0 ? null : v.toValue;
    setPending({ ...to, reason: v.reason.trim() });
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
      <PageHead title="정정 발령 등록" desc="잘못 등록된 발령을 올바른 값으로 정정합니다." />
      <Form form={form} layout="vertical" requiredMark={false} onFinish={onFinish} className="hr-card hr-form hr-form-narrow">
        <div className="hr-readonly" style={{ gap: '0.75rem', padding: '1rem' }}>
          <p className="hr-card-title" style={{ fontSize: '0.875rem' }}>원본 발령 (읽기 전용)</p>
          <Row k="대상자" v={a.employeeName} />
          <Row k="발령 유형" v={labelOf('assignmentType', a.assignmentType)} />
          <Row k="변경 전 → 변경 후" v={changeText(a)} />
          <Row k="발효일" v={a.effectiveDate} />
          <Row k="사유" v={a.reason || '-'} />
          <Row k="등록자" v={a.createdByName} />
        </div>
        {blocked ? (
          <p className="hr-notice is-error">{blocked}</p>
        ) : (
          <>
            <Form.Item name="toValue" label="올바른 변경 값 *" rules={[{ required: true, message: `올바른 ${f.label}을(를) 선택하세요` }]}>
              <Select className="hr-select" style={{ minWidth: '10rem' }} popupMatchSelectWidth={false} showSearch optionFilterProp="label" placeholder="선택하세요"
                options={valueOptions(a.assignmentType, { orgOptions: org.options, grades: grades.data, titles: titles.data }, a[f.toId] ?? null)} />
            </Form.Item>
            <Form.Item name="reason" label="정정 사유 *" rules={[{ required: true, whitespace: true, message: '정정 사유를 입력하세요' }]}>
              <Input.TextArea rows={4} maxLength={255} placeholder="정정 사유를 입력하세요" />
            </Form.Item>
          </>
        )}
        <p className="hr-notice">원본 발령은 수정되지 않으며, 정정 발령이 새 이력으로 추가됩니다.</p>
        <div>
          <button type="submit" className="hr-btn is-lg" disabled={!!blocked || save.isPending}>정정 등록</button>
        </div>
      </Form>
      <ConfirmModal open={!!pending} onClose={() => setPending(null)} onConfirm={confirm} />
    </div>
  );
}

function Row({ k, v }) {
  return (
    <div className="hr-kv2">
      <span className="k">{k}</span>
      <span className="v">{v}</span>
    </div>
  );
}
