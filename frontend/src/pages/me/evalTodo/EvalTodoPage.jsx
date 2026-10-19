// 내 업무 › 평가하기(F-EVAL-04) — 내가 평가자인 평가를 입력 · 임시저장 · 제출한다
// Figma에 평가 입력 화면이 없어 평가 관리(72-7648 · 473-13124) · 평가 템플릿(72-7357) 화면의 카드 · 배지 · 버튼 모양을 따랐다
// 재오픈된 평가는 기간이 끝났어도 다시 고쳐 제출할 수 있다(서버 editable 이 판단)
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { App, Input, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { evalFormKey, getEvalForm, getMyEvalTodo, myEvalTodoKey, saveEvalAnswers, submitEvaluation } from '../../../api/evaluations';
import { meKey } from '../../../api/auth';
import { useNotify } from '../../../components/common/useNotify';
import { PageHead } from '../../hr/parts';
import './eo.css';

/** 응답 척도 — 질문마다 1–5점(BR-EVAL-004), 수준 이름은 기준표와 같다 */
const SCALE = [
  [1, '매우 미흡'],
  [2, '미흡'],
  [3, '보통'],
  [4, '우수'],
  [5, '매우 우수'],
];
const LEVELS = [
  [4.5, '매우 우수'],
  [3.5, '우수'],
  [2.5, '보통'],
  [1.5, '미흡'],
  [0, '매우 미흡'],
];
const levelOf = (avg) => (avg == null ? '–' : LEVELS.find(([min]) => avg >= min)[1]);
const round1 = (n) => Math.round(n * 10) / 10;
const num = (n) => (n == null ? '–' : Number(n).toLocaleString('ko-KR', { maximumFractionDigits: 1 }));
const done = (s) => s === 'SUBMITTED' || s === 'CONFIRMED';
const STATUS = {
  NOT_STARTED: ['작성 전', 'is-gray'],
  IN_PROGRESS: ['작성 중', 'is-warn'],
  REOPENED: ['재오픈', 'is-alert'],
  SUBMITTED: ['제출 완료', 'is-ok'],
  CONFIRMED: ['확정', 'is-info'],
};
function StatusBadge({ status }) {
  const [label, tone] = STATUS[status] ?? [status, 'is-gray'];
  return <span className={`hr-badge ${tone}`}>{label}</span>;
}
/** 마감까지 — 오늘 기준 D-n */
function dday(end) {
  const d = dayjs(end).startOf('day').diff(dayjs().startOf('day'), 'day');
  return d > 0 ? `D-${d}` : d === 0 ? 'D-day' : '마감';
}

/** 화면에서 바로 계산한 항목 점수 · 총점 — 서버와 같은 규칙(항목 평균 ÷ 5 × 배점, 모두 답해야 나온다) */
function scoreOf(criteria, scores) {
  const items = criteria.map((c) => {
    const vals = c.questions.map((q) => scores[q.id]);
    if (vals.some((v) => v == null)) return { avg: null, converted: null };
    const avg = vals.reduce((a, v) => a + v, 0) / vals.length;
    return { avg, converted: round1((avg / 5) * c.weight) };
  });
  const total = items.every((i) => i.converted != null) ? round1(items.reduce((a, i) => a + i.converted, 0)) : null;
  return { items, total };
}

export default function EvalTodoPage() {
  const { modal } = App.useApp();
  const [sp, setSp] = useSearchParams();
  const todo = useQuery({ queryKey: myEvalTodoKey, queryFn: getMyEvalTodo });
  const list = useMemo(() => todo.data ?? [], [todo.data]);
  // 기간별로 묶고, 그 안은 할 일(작성 전 · 작성 중 · 재오픈) 먼저
  const groups = useMemo(() => {
    const map = new Map();
    for (const t of list) {
      if (!map.has(t.cycleId)) map.set(t.cycleId, { cycleId: t.cycleId, cycleName: t.cycleName, endDate: t.cycleEndDate, items: [] });
      map.get(t.cycleId).items.push(t);
    }
    return [...map.values()].map((g) => ({ ...g, items: [...g.items].sort((a, b) => Number(done(a.status)) - Number(done(b.status))) }));
  }, [list]);
  const open = list.filter((t) => !done(t.status)).length;
  const picked = list.find((t) => String(t.evaluationId) === sp.get('id')) ?? list.find((t) => !done(t.status)) ?? list[0];
  const [dirty, setDirty] = useState(false);

  const go = (id) => {
    const move = () => {
      setDirty(false);
      setSp({ id: String(id) });
    };
    if (!dirty) return move();
    modal.confirm({ title: '저장하지 않은 내용이 있습니다', content: '이동하면 입력한 점수와 의견이 사라집니다. 이동할까요?', okText: '이동', cancelText: '계속 입력', onOk: move });
  };

  return (
    <div className="hr eo">
      <PageHead title="평가하기" desc="내가 평가자인 평가를 입력하고 제출합니다. 제출한 평가는 인사팀이 확정하면 본인에게 공개됩니다." />

      {todo.isLoading ? (
        <div className="page-center" style={{ minHeight: '20rem' }}>
          <Spin />
        </div>
      ) : !list.length ? (
        <section className="eo-card eo-empty">
          <strong>지금 입력할 평가가 없습니다</strong>
          <span>인사팀이 평가 기간을 시작하면 내가 평가할 직원이 여기에 나옵니다.</span>
        </section>
      ) : (
        <div className="eo-layout">
          <aside className="eo-list" aria-label="평가 대상 목록">
            <div className="eo-list-head">
              <strong>{`평가 대상 ${list.length}명`}</strong>
              <span>{open ? `작성할 평가 ${open}건` : '모두 제출했습니다'}</span>
            </div>
            {groups.map((g) => (
              <div key={g.cycleId} className="eo-group">
                <div className="eo-group-head">
                  <span>{g.cycleName}</span>
                  <em>{`${g.endDate.replaceAll('-', '.')} 마감 · ${dday(g.endDate)}`}</em>
                </div>
                {g.items.map((t) => (
                  <button key={t.evaluationId} type="button" className={`eo-item${picked?.evaluationId === t.evaluationId ? ' is-on' : ''}`} onClick={() => go(t.evaluationId)}>
                    <span className="eo-item-main">
                      <strong>{t.targetName}</strong>
                      <span>{`${t.orgUnitName} · ${t.templateName}`}</span>
                    </span>
                    <StatusBadge status={t.status} />
                  </button>
                ))}
              </div>
            ))}
          </aside>
          {picked && <FormLoader key={picked.evaluationId} id={picked.evaluationId} onDirty={setDirty} />}
        </div>
      )}
    </div>
  );
}

function FormLoader({ id, onDirty }) {
  const form = useQuery({ queryKey: evalFormKey(id), queryFn: () => getEvalForm(id) });
  if (form.isLoading || !form.data) {
    return (
      <section className="eo-card page-center" style={{ minHeight: '20rem' }}>
        <Spin />
      </section>
    );
  }
  return <EvalForm key={form.dataUpdatedAt} e={form.data} onDirty={onDirty} />;
}

/* ───── 평가 입력 ───── */
function EvalForm({ e, onDirty }) {
  const { modal, message } = App.useApp();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const initScores = useMemo(() => Object.fromEntries(e.criteria.flatMap((c) => c.questions.filter((q) => q.score != null).map((q) => [q.id, q.score]))), [e]);
  const [scores, setScores] = useState(initScores);
  const [comment, setComment] = useState(e.overallComment ?? '');
  const editable = e.editable;
  const dirty = JSON.stringify(scores) !== JSON.stringify(initScores) || comment !== (e.overallComment ?? '');
  useEffect(() => onDirty(dirty), [dirty, onDirty]);

  const { items, total } = scoreOf(e.criteria, scores);
  const questionCount = e.criteria.reduce((a, c) => a + c.questions.length, 0);
  const answered = e.criteria.reduce((a, c) => a + c.questions.filter((q) => scores[q.id] != null).length, 0);
  const avg = total == null ? null : round1(total / 20);

  const body = () => ({ answers: Object.entries(scores).map(([questionId, score]) => ({ questionId: Number(questionId), score })), overallComment: comment.trim() || null });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: evalFormKey(e.id) });
    queryClient.invalidateQueries({ queryKey: myEvalTodoKey });
    queryClient.invalidateQueries({ queryKey: meKey });
  };
  const save = useMutation({
    mutationFn: () => saveEvalAnswers(e.id, body()),
    onSuccess: () => {
      refresh();
      notify.success('임시저장했습니다');
    },
    onError: (err) => notify.error(err),
  });
  // 제출은 지금 화면의 답을 먼저 저장하고 제출한다
  const submit = useMutation({
    mutationFn: async () => {
      await saveEvalAnswers(e.id, body());
      return submitEvaluation(e.id);
    },
    onSuccess: () => {
      refresh();
      notify.success(`${e.targetName}님의 평가를 제출했습니다`);
    },
    onError: (err) => {
      refresh();
      notify.error(err);
    },
  });
  const askSubmit = () => {
    if (answered < questionCount) return message.warning(`답하지 않은 질문이 ${questionCount - answered}개 있습니다`);
    if (!comment.trim()) return message.warning('종합 의견을 입력하세요');
    modal.confirm({
      title: `${e.targetName}님의 평가를 제출할까요?`,
      content: '제출하면 인사팀이 확정하거나 다시 열기 전까지 고칠 수 없습니다.',
      okText: '제출',
      cancelText: '취소',
      onOk: () => submit.mutateAsync(),
    });
  };
  const busy = save.isPending || submit.isPending;

  return (
    <section className="eo-card eo-form">
      <div className="eo-form-head">
        <div>
          <h2>{`${e.targetName}님 평가`}</h2>
          <p>{`${e.orgUnitName} · ${e.cycleName} · ${e.templateName}`}</p>
        </div>
        <StatusBadge status={e.status} />
      </div>

      {e.status === 'REOPENED' && (
        <div className="eo-notice is-alert">
          <strong>인사팀이 평가를 다시 열었습니다</strong>
          <span>{e.reopenReason ? `사유: ${e.reopenReason}` : '내용을 고쳐 다시 제출해 주세요.'}</span>
        </div>
      )}
      {!editable && done(e.status) && (
        <div className="eo-notice">
          <span>{e.status === 'CONFIRMED' ? `확정된 평가입니다(${e.confirmedAt?.slice(0, 10).replaceAll('-', '.')}). 본인에게 공개되었습니다.` : `제출한 평가입니다(${e.submittedAt?.slice(0, 10).replaceAll('-', '.')}). 인사팀 확정을 기다리고 있습니다.`}</span>
        </div>
      )}
      {!editable && !done(e.status) && (
        <div className="eo-notice is-gray">
          <span>평가 기간이 끝나 입력할 수 없습니다. 고쳐야 하면 인사팀에 문의하세요.</span>
        </div>
      )}

      <div className="eo-guide">
        <span>질문마다 1–5점을 고르세요. 항목 점수는 질문 평균이고, 환산 점수는 항목 점수 ÷ 5 × 배점입니다.</span>
        <span className="eo-progress">{`응답 ${answered} / ${questionCount}`}</span>
      </div>

      {e.criteria.map((c, ci) => (
        <div key={c.id} className="eo-crit">
          <div className="eo-crit-head">
            <div>
              <span className="eo-weight">{`${c.weight}점`}</span>
              <strong>{c.name}</strong>
              <em>{c.category}</em>
            </div>
            <span className="eo-crit-score">{items[ci].converted == null ? '모든 질문에 답하면 점수가 나옵니다' : `평균 ${num(round1(items[ci].avg))} · ${num(items[ci].converted)} / ${c.weight}점`}</span>
          </div>
          {c.questions.map((q, qi) => (
            <div key={q.id} className="eo-q">
              <div className="eo-q-text">
                <span>{qi + 1}</span>
                <p>{q.content}</p>
              </div>
              <div className="eo-scale" role="radiogroup" aria-label={q.content}>
                {SCALE.map(([v, label]) => (
                  <button
                    key={v}
                    type="button"
                    role="radio"
                    aria-checked={scores[q.id] === v}
                    className={scores[q.id] === v ? 'is-on' : undefined}
                    disabled={!editable}
                    onClick={() => setScores((s) => ({ ...s, [q.id]: v }))}
                  >
                    <strong>{v}</strong>
                    <span>{label}</span>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      ))}

      <div className="eo-bottom">
        <label className="eo-comment">
          <span>종합 의견 <em>제출할 때 꼭 써야 합니다 · 확정되면 본인과 조회 권한자에게 공개됩니다</em></span>
          <Input.TextArea value={comment} disabled={!editable} maxLength={2000} showCount autoSize={{ minRows: 4, maxRows: 10 }} placeholder="잘한 점과 보완할 점을 구체적으로 적어 주세요" onChange={(ev) => setComment(ev.target.value)} />
        </label>
        <div className="eo-total">
          <span>총점</span>
          <strong>{total == null ? '–' : `${num(total)}`}<small> / 100점</small></strong>
          <em>{avg == null ? '모든 질문에 답하면 계산됩니다' : `종합 수준 ${num(avg)} · ${levelOf(avg)}`}</em>
        </div>
      </div>

      {editable && (
        <div className="eo-actions">
          <button type="button" className="eo-btn is-line" disabled={busy || !dirty} onClick={() => save.mutate()}>
            임시저장
          </button>
          <button type="button" className="eo-btn" disabled={busy} onClick={askSubmit}>
            제출
          </button>
        </div>
      )}
    </section>
  );
}
