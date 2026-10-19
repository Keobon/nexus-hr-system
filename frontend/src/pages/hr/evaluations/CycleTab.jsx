// 평가 기간 — Figma 72-7227(목록) · 473-11844(평가 기간 만들기)
// 서버에는 기간 이름 · 시작일 · 종료일만 있다 → 연도 · 반기로 이름("2026 상반기 평가")을 만들고, 평가 유형은 대상 규칙의 템플릿으로 둔다.
// Figma 의 "자기평가 제출 기간"은 서버에 자기평가가 없어 뺐다. 저장한 뒤 줄에서 대상 지정 → 평가 시작 → 마감
import { useState } from 'react';
import { App, DatePicker, Form, Modal, Segmented, Select, Spin } from 'antd';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  closeEvalCycle,
  createEvalCycle,
  evalCyclesKey,
  evalTargetsKey,
  evalTargetsPreviewKey,
  evalTemplatesKey,
  getEvalCycles,
  getEvalTargets,
  getEvalTargetsPreview,
  getEvalTemplates,
  putEvalTargets,
  startEvalCycle,
} from '../../../api/evalAdmin';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { ModalFoot } from '../parts';
import { CycleBadge, HALF_MONTHS, cycleParts, dots, yearOptions } from './shared';

/** 대상 규칙 → "리더 평가(조직장) · 일반 평가" */
function typeText(rules) {
  if (!rules) return '…';
  const used = rules.rules.filter((r) => r.evalTemplateId);
  if (!used.length) return '대상 지정 전';
  return used.map((r) => `${r.evalTemplateName}${r.condIsOrgLead ? '(조직장)' : r.condJobGradeName ? `(${r.condJobGradeName})` : ''}`).join(' · ');
}

export default function CycleTab({ onResults }) {
  const notify = useNotify();
  const { modal } = App.useApp();
  const queryClient = useQueryClient();
  const cycles = useQuery({ queryKey: evalCyclesKey, queryFn: getEvalCycles });
  const list = cycles.data ?? [];
  const [year, setYear] = useState(dayjs().format('YYYY'));
  const [creating, setCreating] = useState(false);
  const [targeting, setTargeting] = useState(null);
  const rows = list.filter((c) => cycleParts(c).year === year).sort((a, b) => a.startDate.localeCompare(b.startDate));
  const rules = useQueries({ queries: rows.map((c) => ({ queryKey: evalTargetsKey(c.id), queryFn: () => getEvalTargets(c.id) })) });

  const act = (c, kind) =>
    modal.confirm({
      title: kind === 'start' ? `${c.name}을(를) 시작할까요?` : `${c.name}을(를) 마감할까요?`,
      content:
        kind === 'start'
          ? '대상 규칙대로 직원마다 평가가 만들어지고 평가자(조직장)에게 할 일로 보입니다. 시작한 뒤에는 대상을 바꿀 수 없습니다.'
          : '마감하면 더 이상 평가를 입력 · 제출할 수 없습니다(확정 취소로 다시 열 수는 있습니다).',
      okText: kind === 'start' ? '평가 시작' : '마감',
      cancelText: '취소',
      onOk: async () => {
        try {
          await (kind === 'start' ? startEvalCycle(c.id) : closeEvalCycle(c.id));
          queryClient.invalidateQueries({ queryKey: evalCyclesKey });
          notify.success(kind === 'start' ? `${c.name}을(를) 시작했습니다` : `${c.name}을(를) 마감했습니다`);
        } catch (err) {
          notify.error(err);
        }
      },
    });

  return (
    <>
      <div className="ea-bar is-plain">
        <Select className="ea-select" value={year} onChange={setYear} options={yearOptions(list)} />
        <button type="button" className="hr-btn" onClick={() => setCreating(true)}>
          평가 기간 만들기
        </button>
      </div>

      <section className="hr-table ea-card">
        <div className="ea-card-head">
          <strong>{`총 ${rows.length}개 평가 기간`}</strong>
          <span>상태는 현재 상태 기준으로 표시됩니다.</span>
        </div>
        <div className="hr-tr is-head">
          <span className="ea-c-year">평가 연도</span>
          <span className="ea-c-name">평가 기간</span>
          <span className="ea-c-type">평가 유형</span>
          <span className="ea-c-date">평가 기간(날짜)</span>
          <span className="ea-c-cnt">대상</span>
          <span className="ea-c-st">상태</span>
          <span className="ea-c-act">관리</span>
        </div>
        {cycles.isLoading && (
          <div className="hr-tr is-empty">
            <Spin />
          </div>
        )}
        {cycles.error && <div className="hr-tr is-empty">{errorMessage(cycles.error)}</div>}
        {!cycles.isLoading && rows.length === 0 && <div className="hr-tr is-empty">{`${year}년 평가 기간이 없습니다`}</div>}
        {rows.map((c, i) => {
          const { half } = cycleParts(c);
          return (
            <div key={c.id} className="hr-tr ea-row">
              <span className="ea-c-year hr-strong">{`${cycleParts(c).year}년`}</span>
              <span className="ea-c-name hr-strong">{half ? `${half} (${HALF_MONTHS[half]})` : c.name}</span>
              <span className="ea-c-type">{typeText(rules[i]?.data)}</span>
              <span className="ea-c-date">{dots(c.startDate, c.endDate)}</span>
              <span className="ea-c-cnt">{c.status === 'SCHEDULED' ? '–' : `${c.evaluationCount}명`}</span>
              <span className="ea-c-st">
                <CycleBadge status={c.status} />
              </span>
              <span className="ea-c-act">
                {c.status === 'SCHEDULED' && (
                  <>
                    <button type="button" className="hr-link" onClick={() => setTargeting(c)}>
                      대상 지정
                    </button>
                    <button type="button" className="hr-link" onClick={() => act(c, 'start')}>
                      평가 시작
                    </button>
                  </>
                )}
                {c.status === 'IN_PROGRESS' && (
                  <>
                    <button type="button" className="hr-link" onClick={() => onResults(c.id)}>
                      진행 현황
                    </button>
                    <button type="button" className="hr-link is-red" onClick={() => act(c, 'close')}>
                      마감
                    </button>
                  </>
                )}
                {c.status === 'CLOSED' && (
                  <button type="button" className="hr-link" onClick={() => onResults(c.id)}>
                    결과 보기
                  </button>
                )}
              </span>
            </div>
          );
        })}
      </section>

      {creating && <CreateModal onClose={() => setCreating(false)} onCreated={(c) => setTargeting(c)} />}
      {targeting && <TargetModal cycle={targeting} onClose={() => setTargeting(null)} />}
    </>
  );
}

/* ───── 평가 기간 만들기(473-11844) — 저장하면 대상 규칙(전원 → 고른 평가 유형)까지 넣는다 ───── */
function CreateModal({ onClose, onCreated }) {
  const notify = useNotify();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const templates = useQuery({ queryKey: evalTemplatesKey, queryFn: getEvalTemplates });
  const thisYear = Number(dayjs().format('YYYY'));
  const range = (y, half) => (half === '상반기' ? [dayjs(`${y}-01-01`), dayjs(`${y}-06-30`)] : [dayjs(`${y}-07-01`), dayjs(`${y}-12-31`)]);
  // 연도 · 반기를 바꾸면 날짜를 그 반기로 채운다(직접 고칠 수 있다)
  const fill = (y, half) => {
    const [s, e] = range(y, half);
    form.setFieldsValue({ startDate: s, endDate: e });
  };
  const save = useMutation({
    mutationFn: async (v) => {
      const c = await createEvalCycle({ name: `${v.year} ${v.half} 평가`, startDate: v.startDate.format('YYYY-MM-DD'), endDate: v.endDate.format('YYYY-MM-DD') });
      if (v.templateId) await putEvalTargets(c.id, { rules: [{ priority: 1, evalTemplateId: v.templateId }] });
      return c;
    },
    onSuccess: (c) => {
      queryClient.invalidateQueries({ queryKey: evalCyclesKey });
      // 날짜가 겹쳐도 저장은 되고 warning PERIOD_OVERLAP 만 온다 — 공통 경고 문구(휴가·출장)가 아닌 평가 기간 문구로
      notify.success(`${c.name}을(를) 만들었습니다`);
      if (c.warning === 'PERIOD_OVERLAP') message.warning('다른 평가 기간과 날짜가 겹칩니다. 날짜를 확인해 주세요', 5);
      onClose();
      onCreated?.(c);
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });
  const [s0, e0] = range(thisYear, dayjs().month() < 6 ? '상반기' : '하반기');
  return (
    <Modal open title="평가 기간 만들기" footer={null} onCancel={onClose} width={560} className="hr-modal ea-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">평가 연도, 평가 기간, 평가 유형 및 기간을 설정해 주세요.</p>
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{ year: thisYear, half: dayjs().month() < 6 ? '상반기' : '하반기', startDate: s0, endDate: e0 }}
        onFinish={(v) => save.mutate(v)}
      >
        <Form.Item name="year" label="평가 연도">
          <Select options={[thisYear - 1, thisYear, thisYear + 1].map((y) => ({ value: y, label: `${y}년` }))} onChange={(y) => fill(y, form.getFieldValue('half'))} />
        </Form.Item>
        <Form.Item name="half" label="평가 기간">
          <Segmented
            options={[
              { value: '상반기', label: '상반기 (01–06월)' },
              { value: '하반기', label: '하반기 (07–12월)' },
            ]}
            onChange={(h) => fill(form.getFieldValue('year'), h)}
          />
        </Form.Item>
        <Form.Item name="templateId" label="평가 유형" extra="모든 대상자에게 이 템플릿을 씁니다. 조직장만 다르게 하려면 저장 뒤 대상 지정에서 바꾸세요." rules={[{ required: true, message: '평가 유형을 고르세요' }]}>
          <Select loading={templates.isLoading} placeholder="평가 템플릿" options={(templates.data ?? []).map((t) => ({ value: t.id, label: t.name }))} />
        </Form.Item>
        <div className="hr-form-row">
          <Form.Item name="startDate" label="평가 기간 시작일" rules={[{ required: true, message: '시작일을 고르세요' }]}>
            <DatePicker className="full-width" format="YYYY.MM.DD" />
          </Form.Item>
          <Form.Item
            name="endDate"
            label="평가 기간 종료일"
            dependencies={['startDate']}
            rules={[
              { required: true, message: '종료일을 고르세요' },
              ({ getFieldValue }) => ({ validator: (_, v) => (!v || !getFieldValue('startDate') || !v.isBefore(getFieldValue('startDate'), 'day') ? Promise.resolve() : Promise.reject(new Error('시작일보다 빠를 수 없습니다'))) }),
            ]}
          >
            <DatePicker className="full-width" format="YYYY.MM.DD" />
          </Form.Item>
        </div>
        <p className="ea-hint">평가 기간을 저장하면 대상 지정과 평가 시작을 진행할 수 있습니다. 자기평가 제출 기간은 준비 중입니다.</p>
        <ModalFoot onCancel={onClose} busy={save.isPending} okText="만들기" />
      </Form>
    </Modal>
  );
}

/* ───── 대상 지정 — 조직장 · 나머지 직원에게 쓸 평가 유형(제외 가능). 미리보기로 몇 명인지 ───── */
function TargetModal({ cycle, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const templates = useQuery({ queryKey: evalTemplatesKey, queryFn: getEvalTemplates });
  const current = useQuery({ queryKey: evalTargetsKey(cycle.id), queryFn: () => getEvalTargets(cycle.id) });
  const preview = useQuery({ queryKey: evalTargetsPreviewKey(cycle.id), queryFn: () => getEvalTargetsPreview(cycle.id) });
  const rules = current.data?.rules ?? [];
  const leadRule = rules.find((r) => r.condIsOrgLead === true);
  const restRule = rules.find((r) => r.condIsOrgLead == null && !r.condJobGradeId && !r.condJobTitleId && !r.condEmploymentTypeId);
  // 직급 · 직책 · 고용형태 조건 규칙(예: 인턴 제외)은 손대지 않고 조직장 줄과 나머지 줄 사이에 그대로 둔다
  const custom = rules.filter((r) => r.condJobGradeId || r.condJobTitleId || r.condEmploymentTypeId);
  const [lead, setLead] = useState();
  const [rest, setRest] = useState();
  const leadVal = lead ?? (leadRule ? (leadRule.evalTemplateId ?? 'NONE') : 'SAME');
  const restVal = rest ?? (restRule ? (restRule.evalTemplateId ?? 'NONE') : 'NONE');
  const [busy, setBusy] = useState(false);

  const save = async () => {
    const next = [];
    if (leadVal !== 'SAME') next.push({ priority: 1, condIsOrgLead: true, evalTemplateId: leadVal === 'NONE' ? null : leadVal });
    custom.forEach((r) => next.push({ ...r, priority: next.length + 1 }));
    next.push({ priority: next.length + 1, evalTemplateId: restVal === 'NONE' ? null : restVal });
    setBusy(true);
    try {
      await putEvalTargets(cycle.id, { rules: next });
      queryClient.invalidateQueries({ queryKey: ['eval-cycles', cycle.id] });
      notify.success('대상 규칙을 저장했습니다');
      onClose();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };
  const opts = (templates.data ?? []).map((t) => ({ value: t.id, label: t.name }));

  return (
    <Modal open title={`대상 지정 · ${cycle.name}`} footer={null} onCancel={onClose} width={560} className="hr-modal ea-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">대상자마다 쓸 평가 유형을 정합니다. 평가자는 대상자의 조직장으로 자동 지정됩니다.</p>
      {current.isLoading ? (
        <Spin />
      ) : (
        <>
          {custom.length > 0 && (
            <p className="ea-hint">{`조건 규칙은 그대로 둡니다 — ${custom.map((r) => `${r.condEmploymentTypeName ?? r.condJobGradeName ?? r.condJobTitleName} → ${r.evalTemplateName ?? '평가 제외'}`).join(' · ')}`}</p>
          )}
          <div className="ea-field">
            <label>조직장</label>
            <Select value={leadVal} onChange={setLead} options={[{ value: 'SAME', label: '나머지 직원과 같게' }, ...opts, { value: 'NONE', label: '평가 제외' }]} />
          </div>
          <div className="ea-field">
            <label>나머지 직원</label>
            <Select value={restVal} onChange={setRest} options={[...opts, { value: 'NONE', label: '평가 제외' }]} />
          </div>
          <div className="ea-preview">
            <strong>지금 저장된 규칙 기준 미리보기</strong>
            {preview.isLoading && <Spin size="small" />}
            {preview.data && (
              <span>{`대상 ${preview.data.targets.length}명 · 제외 ${preview.data.excluded.length}명${preview.data.targets.length ? ` — ${preview.data.targets.slice(0, 5).map((t) => `${t.name}(${t.evalTemplateName})`).join(', ')}${preview.data.targets.length > 5 ? ' 외' : ''}` : ''}`}</span>
            )}
          </div>
        </>
      )}
      <ModalFoot onCancel={onClose} busy={busy} okType="button" onOk={save} />
    </Modal>
  );
}
