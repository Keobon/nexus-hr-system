// 평가 기간 — Figma 72-7227(목록) · 473-11844(평가 만들기)
// 평가지는 "평가 만들기" 한 곳에서만 고른다(정기: 직원 · 조직장, 고과: 고과 평가지 하나) — 저장하면 대상 규칙까지 넣는다.
// 목록에서는 대상 확인(읽기 전용 미리보기) · 수정(같은 창, 예정만) · 평가 시작 · 삭제(예정만) → 진행 현황 · 마감 → 결과 보기
// Figma 의 "자기평가 제출 기간"은 서버에 자기평가가 없어 뺐다
// 승진 고과: 심사 기간은 회사 공통, 대상은 기간 시작일에 지금 직급에 N년 이상인 직원(서버 계산). 결과는 본인에게 공개하지 않는다
import { useState } from 'react';
import { App, DatePicker, Form, Input, InputNumber, Modal, Segmented, Select, Spin } from 'antd';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  closeEvalCycle,
  createEvalCycle,
  deleteEvalCycle,
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
  updateEvalCycle,
} from '../../../api/evalAdmin';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { ModalFoot } from '../parts';
import { CycleBadge, HALF_MONTHS, cycleParts, dots, yearOptions } from './shared';

/** 대상 규칙 → "리더 평가(조직장) · 일반 평가" */
function typeText(rules, cycle) {
  if (!rules) return '…';
  const used = rules.rules.filter((r) => r.evalTemplateId);
  if (!used.length) return '대상 지정 전';
  const suffix = cycle.kind === 'PROMOTION' ? ` · 직급 ${cycle.minYearsInGrade}년 이상` : '';
  return used.map((r) => `${r.evalTemplateName}${r.condIsOrgLead ? '(조직장)' : r.condJobGradeName ? `(${r.condJobGradeName})` : ''}`).join(' · ') + suffix;
}

export default function CycleTab({ onResults }) {
  const notify = useNotify();
  const { modal } = App.useApp();
  const queryClient = useQueryClient();
  const cycles = useQuery({ queryKey: evalCyclesKey, queryFn: getEvalCycles });
  const list = cycles.data ?? [];
  const [year, setYear] = useState(dayjs().format('YYYY'));
  const [editing, setEditing] = useState(null); // null · 'new' · 평가 기간(수정)
  const [checking, setChecking] = useState(null); // 대상 확인 창

  const rows = list.filter((c) => cycleParts(c).year === year).sort((a, b) => a.startDate.localeCompare(b.startDate));
  const rules = useQueries({ queries: rows.map((c) => ({ queryKey: evalTargetsKey(c.id), queryFn: () => getEvalTargets(c.id) })) });

  const act = (c, kind) =>
    modal.confirm({
      title: kind === 'start' ? `${c.name}을(를) 시작할까요?` : `${c.name}을(를) 마감할까요?`,
      content:
        kind === 'start'
          ? c.kind === 'PROMOTION'
            ? `${c.startDate} 기준 지금 직급에 ${c.minYearsInGrade}년 이상인 직원에게 평가가 만들어지고 평가자(조직장)에게 할 일로 보입니다. 결과는 본인에게 공개되지 않습니다. 시작한 뒤에는 대상을 바꿀 수 없습니다.`
            : '대상 규칙대로 직원마다 평가가 만들어지고 평가자(조직장)에게 할 일로 보입니다. 시작한 뒤에는 대상을 바꿀 수 없습니다.'
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

  const remove = (c) =>
    modal.confirm({
      title: `${c.name}을(를) 삭제할까요?`,
      content: '예정 상태라 아직 만들어진 평가가 없습니다. 평가와 대상 규칙이 함께 지워지고 되돌릴 수 없습니다.',
      okText: '삭제',
      okButtonProps: { danger: true },
      cancelText: '취소',
      onOk: async () => {
        try {
          await deleteEvalCycle(c.id);
          queryClient.invalidateQueries({ queryKey: evalCyclesKey });
          notify.success(`${c.name}을(를) 삭제했습니다`);
        } catch (err) {
          notify.error(err);
        }
      },
    });

  return (
    <>
      <div className="ea-bar is-plain">
        <Select className="ea-select" value={year} onChange={setYear} options={yearOptions(list)} />
        <button type="button" className="hr-btn" onClick={() => setEditing('new')}>
          평가 만들기
        </button>
      </div>

      <section className="hr-table ea-card">
        <div className="ea-card-head">
          <strong>{`총 ${rows.length}개 평가`}</strong>
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
              <span className="ea-c-type">{typeText(rules[i]?.data, c)}</span>
              <span className="ea-c-date">{dots(c.startDate, c.endDate)}</span>
              <span className="ea-c-cnt">{c.status === 'SCHEDULED' ? '–' : `${c.evaluationCount}명`}</span>
              <span className="ea-c-st">
                <CycleBadge status={c.status} />
              </span>
              <span className="ea-c-act">
                {c.status === 'SCHEDULED' && (
                  <>
                    <button type="button" className="hr-link" onClick={() => setChecking(c)}>
                      대상 확인
                    </button>
                    <button type="button" className="hr-link" onClick={() => setEditing(c)}>
                      수정
                    </button>
                    <button type="button" className="hr-link" onClick={() => act(c, 'start')}>
                      평가 시작
                    </button>
                    <button type="button" className="hr-link is-red" onClick={() => remove(c)}>
                      삭제
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

      {editing && (
        <EvalFormModal key={editing === 'new' ? 'new' : editing.id} cycle={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={(c) => setChecking(c)} />
      )}
      {checking && (
        <TargetCheckModal
          cycle={checking}
          onClose={() => setChecking(null)}
          onEdit={() => {
            setEditing(checking);
            setChecking(null);
          }}
        />
      )}
    </>
  );
}

/* ───── 평가 만들기 · 수정(473-11844) — 평가지는 여기서만 고른다. 저장하면 대상 규칙까지 넣는다 ───── */
const leadRuleOf = (rules) => rules.find((r) => r.condIsOrgLead === true);
const restRuleOf = (rules) => rules.find((r) => r.condIsOrgLead == null && !r.condJobGradeId && !r.condJobTitleId && !r.condEmploymentTypeId);
// 직급 · 직책 · 고용형태 조건 규칙(예: 인턴 제외)은 이 창에서 만들지 않고 그대로 둔다
const customRulesOf = (rules) => rules.filter((r) => r.condJobGradeId || r.condJobTitleId || r.condEmploymentTypeId);

/** 정기: 조직장(직원과 다르면) → 조건 규칙 → 나머지 직원. 고과: 고과 평가지 하나 */
function buildRules(kind, v, custom) {
  if (kind === 'PROMOTION') return [{ priority: 1, evalTemplateId: v.promotionTemplateId }];
  const next = [];
  if (v.leadTemplateId !== 'SAME' && v.leadTemplateId !== v.staffTemplateId) {
    next.push({ priority: 1, condIsOrgLead: true, evalTemplateId: v.leadTemplateId === 'NONE' ? null : v.leadTemplateId });
  }
  custom.forEach((r) => next.push({ ...r, priority: next.length + 1 }));
  next.push({ priority: next.length + 1, evalTemplateId: v.staffTemplateId });
  return next;
}

function EvalFormModal({ cycle, onClose, onSaved }) {
  const notify = useNotify();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const templates = useQuery({ queryKey: evalTemplatesKey, queryFn: getEvalTemplates });
  const current = useQuery({ queryKey: evalTargetsKey(cycle?.id), queryFn: () => getEvalTargets(cycle.id), enabled: !!cycle });
  const thisYear = Number(dayjs().format('YYYY'));
  const range = (y, half) => (half === '상반기' ? [dayjs(`${y}-01-01`), dayjs(`${y}-06-30`)] : [dayjs(`${y}-07-01`), dayjs(`${y}-12-31`)]);
  // 연도 · 반기를 바꾸면 날짜를 그 반기로 채운다(직접 고칠 수 있다)
  const fill = (y, half) => {
    const [s, e] = range(y, half);
    form.setFieldsValue({ startDate: s, endDate: e });
  };
  const list = templates.data ?? [];
  const byName = (word) => list.find((t) => t.name.includes(word))?.id;
  const rules = current.data?.rules ?? [];
  const custom = customRulesOf(rules);

  const save = useMutation({
    mutationFn: async (v) => {
      const kind = cycle?.kind ?? v.kind;
      const dates = { startDate: v.startDate.format('YYYY-MM-DD'), endDate: v.endDate.format('YYYY-MM-DD') };
      const name = kind === 'PROMOTION' ? v.name.trim() : `${v.year} ${v.half} 평가`;
      let c;
      if (cycle) {
        const patch = Object.fromEntries(Object.entries({ name, ...dates }).filter(([k, val]) => cycle[k] !== val));
        c = Object.keys(patch).length ? await updateEvalCycle(cycle.id, patch) : cycle;
      } else {
        c = await createEvalCycle(kind === 'PROMOTION' ? { name, kind, minYearsInGrade: v.minYearsInGrade, ...dates } : { name, ...dates });
      }
      await putEvalTargets(c.id, { rules: buildRules(kind, v, custom) });
      return c;
    },
    onSuccess: (c) => {
      queryClient.invalidateQueries({ queryKey: evalCyclesKey });
      queryClient.invalidateQueries({ queryKey: ['eval-cycles', c.id] });
      // 날짜가 겹쳐도 저장은 되고 warning PERIOD_OVERLAP 만 온다 — 공통 경고 문구(휴가·출장)가 아닌 평가 문구로
      notify.success(cycle ? `${c.name}을(를) 저장했습니다` : `${c.name}을(를) 만들었습니다`);
      if (c.warning === 'PERIOD_OVERLAP') message.warning('같은 종류의 다른 평가와 날짜가 겹칩니다. 날짜를 확인해 주세요', 5);
      onClose();
      onSaved?.(c);
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });

  const [s0, e0] = range(thisYear, dayjs().month() < 6 ? '상반기' : '하반기');
  const watchedKind = Form.useWatch('kind', form);
  const kind = cycle?.kind ?? watchedKind ?? 'REGULAR';
  // 승진 고과는 반기와 상관없다 — 오늘부터 2주를 먼저 채운다(직접 고칠 수 있다)
  const changeKind = (k) => {
    if (k === 'PROMOTION') form.setFieldsValue({ startDate: dayjs(), endDate: dayjs().add(14, 'day') });
    else fill(form.getFieldValue('year'), form.getFieldValue('half'));
  };
  if (templates.isLoading || (cycle && current.isLoading)) {
    return (
      <Modal open title={cycle ? '평가 수정' : '평가 만들기'} footer={null} onCancel={onClose} className="hr-modal ea-modal" centered>
        <Spin />
      </Modal>
    );
  }
  const lead = leadRuleOf(rules);
  const rest = restRuleOf(rules);
  const parts = cycle ? cycleParts(cycle) : null;
  const initial = cycle
    ? {
        kind: cycle.kind,
        name: cycle.name,
        minYearsInGrade: cycle.minYearsInGrade,
        year: Number(parts.year),
        half: parts.half ?? '상반기',
        startDate: dayjs(cycle.startDate),
        endDate: dayjs(cycle.endDate),
        staffTemplateId: rest?.evalTemplateId ?? byName('일반'),
        leadTemplateId: lead ? (lead.evalTemplateId ?? 'NONE') : 'SAME',
        promotionTemplateId: rules.find((r) => r.evalTemplateId)?.evalTemplateId ?? byName('고과'),
      }
    : {
        kind: 'REGULAR',
        year: thisYear,
        half: dayjs().month() < 6 ? '상반기' : '하반기',
        name: `${thisYear} 승진 고과`,
        minYearsInGrade: 3,
        startDate: s0,
        endDate: e0,
        staffTemplateId: byName('일반'),
        leadTemplateId: byName('리더') ?? 'SAME',
        promotionTemplateId: byName('고과'),
      };
  const opts = list.map((t) => ({ value: t.id, label: t.name }));

  return (
    <Modal open title={cycle ? `평가 수정 · ${cycle.name}` : '평가 만들기'} footer={null} onCancel={onClose} width={560} className="hr-modal ea-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">평가 종류 · 기간 · 평가지를 정합니다. 대상자는 저장 뒤 [대상 확인]에서 확인하세요.</p>
      <Form form={form} layout="vertical" requiredMark={false} initialValues={initial} onFinish={(v) => save.mutate(v)}>
        <Form.Item name="kind" label="평가 종류" extra={cycle ? '평가 종류는 만든 뒤 바꿀 수 없습니다.' : undefined}>
          <Segmented
            disabled={!!cycle}
            options={[
              { value: 'REGULAR', label: '정기 평가' },
              { value: 'PROMOTION', label: '승진 고과' },
            ]}
            onChange={changeKind}
          />
        </Form.Item>
        {kind === 'PROMOTION' ? (
          <>
            <Form.Item name="name" label="고과 이름" rules={[{ required: true, whitespace: true, message: '이름을 입력하세요' }]}>
              <Input maxLength={50} />
            </Form.Item>
            <Form.Item
              name="minYearsInGrade"
              label="대상 기준 — 지금 직급 체류 연수"
              extra={cycle ? '체류 연수는 만든 뒤 바꿀 수 없습니다.' : '기간 시작일에 지금 직급에 이만큼 있었던 직원만 대상입니다. 최고 직급은 제외합니다.'}
              rules={[{ required: true, message: '체류 연수를 입력하세요' }]}
            >
              <InputNumber className="full-width" min={1} max={50} suffix="년 이상" disabled={!!cycle} />
            </Form.Item>
            <Form.Item name="promotionTemplateId" label="고과 평가지" extra="대상자 모두 이 평가지로 평가합니다." rules={[{ required: true, message: '고과 평가지를 고르세요' }]}>
              <Select placeholder="평가 템플릿" options={opts} />
            </Form.Item>
          </>
        ) : (
          <>
            <div className="hr-form-row">
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
            </div>
            <div className="hr-form-row">
              <Form.Item name="staffTemplateId" label="직원 평가지" rules={[{ required: true, message: '직원 평가지를 고르세요' }]}>
                <Select placeholder="평가 템플릿" options={opts} />
              </Form.Item>
              <Form.Item name="leadTemplateId" label="조직장 평가지" extra="팀장 · 본부장처럼 조직장으로 지정된 직원">
                <Select options={[{ value: 'SAME', label: '직원과 같게' }, ...opts, { value: 'NONE', label: '평가 제외' }]} />
              </Form.Item>
            </div>
            {custom.length > 0 && (
              <p className="ea-hint">{`조건 규칙은 그대로 둡니다 — ${custom.map((r) => `${r.condEmploymentTypeName ?? r.condJobGradeName ?? r.condJobTitleName} → ${r.evalTemplateName ?? '평가 제외'}`).join(' · ')}`}</p>
            )}
          </>
        )}
        <div className="hr-form-row">
          <Form.Item name="startDate" label="평가 시작일" rules={[{ required: true, message: '시작일을 고르세요' }]}>
            <DatePicker className="full-width" format="YYYY.MM.DD" />
          </Form.Item>
          <Form.Item
            name="endDate"
            label="평가 종료일"
            dependencies={['startDate']}
            rules={[
              { required: true, message: '종료일을 고르세요' },
              ({ getFieldValue }) => ({ validator: (_, v) => (!v || !getFieldValue('startDate') || !v.isBefore(getFieldValue('startDate'), 'day') ? Promise.resolve() : Promise.reject(new Error('시작일보다 빠를 수 없습니다'))) }),
            ]}
          >
            <DatePicker className="full-width" format="YYYY.MM.DD" />
          </Form.Item>
        </div>
        <ModalFoot onCancel={onClose} busy={save.isPending} okText={cycle ? '저장' : '만들기'} />
      </Form>
    </Modal>
  );
}

/* ───── 대상 확인 — 읽기 전용. 정해진 평가지와 지금 시작하면 만들어질 평가 · 빠지는 사람 ───── */
const EXCLUDE_REASON = {
  ON_LEAVE: '휴직',
  NO_EVALUATOR: '평가자 없음(최상위 조직장)',
  NO_RULE: '평가지 미지정',
  RULE_EXCLUDED: '평가 제외 규칙',
};

function TargetCheckModal({ cycle, onClose, onEdit }) {
  const current = useQuery({ queryKey: evalTargetsKey(cycle.id), queryFn: () => getEvalTargets(cycle.id) });
  const preview = useQuery({ queryKey: evalTargetsPreviewKey(cycle.id), queryFn: () => getEvalTargetsPreview(cycle.id) });
  const targets = preview.data?.targets ?? [];
  const excluded = preview.data?.excluded ?? [];
  const reason = (x) => (x.reason === 'NOT_ELIGIBLE' ? `지금 직급 ${cycle.minYearsInGrade}년 미만 또는 최고 직급` : (EXCLUDE_REASON[x.reason] ?? x.reason));

  return (
    <Modal open title={`대상 확인 · ${cycle.name}`} footer={null} onCancel={onClose} width={620} className="hr-modal ea-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">
        {cycle.kind === 'PROMOTION'
          ? `${cycle.startDate} 기준 지금 직급에 ${cycle.minYearsInGrade}년 이상인 재직 직원이 대상입니다(최고 직급 제외). 평가자는 대상자의 조직장으로 자동 지정됩니다.`
          : '재직 직원이 대상이고, 평가자는 대상자의 조직장으로 자동 지정됩니다.'}
      </p>
      {current.isLoading || preview.isLoading ? (
        <Spin />
      ) : (
        <>
          <div className="ea-field">
            <label>평가지</label>
            <span>{typeText(current.data, cycle)}</span>
          </div>
          <div className="ea-preview">
            <strong>{`대상 ${targets.length}명`}</strong>
            {targets.map((t) => (
              <span key={t.employeeId}>{`${t.name} · ${t.orgUnitName} — ${t.evalTemplateName} · 평가자 ${t.evaluatorName}`}</span>
            ))}
          </div>
          {excluded.length > 0 && (
            <div className="ea-preview">
              <strong>{`제외 ${excluded.length}명`}</strong>
              {excluded.map((x) => (
                <span key={x.employeeId}>{`${x.name} · ${x.orgUnitName} — ${reason(x)}`}</span>
              ))}
            </div>
          )}
          {!targets.length && <p className="ea-hint">대상자가 없으면 평가를 시작할 수 없습니다. [수정]에서 평가지를 확인하세요.</p>}
        </>
      )}
      <div className="hr-modal-foot">
        <button type="button" className="hr-btn is-secondary" onClick={onClose}>
          닫기
        </button>
        {cycle.status === 'SCHEDULED' && (
          <button type="button" className="hr-btn" onClick={onEdit}>
            수정
          </button>
        )}
      </div>
    </Modal>
  );
}
