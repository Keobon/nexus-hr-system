// 회사 설정 › 평가 템플릿(F-EVAL-02) — Figma 72-7357(기준표 · 질문 편집) · 473-12319(기준표 미리보기)
// 평가 관리의 "기준표 · 질문" 탭이 이 메뉴로 왔다. 서버에 없는 평가 유형 · 적용 범위 · 종합 등급 · 자기평가 질문 · 구간별 설명은 뺐다
// (적용 범위는 평가 기간 › 대상 지정에서, 구간은 배점으로 자동 계산)
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { App, Form, Input, InputNumber, Modal, Select, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  copyEvalTemplate,
  createEvalTemplate,
  deleteEvalTemplate,
  evalTemplateKey,
  evalTemplateListKey,
  getEvalTemplate,
  getEvalTemplateList,
  updateEvalTemplate,
} from '../../../api/evalTemplates';
import iconGrip from '../../../assets/figma/icon-grip-vertical.svg';
import iconPlus from '../../../assets/figma/icon-plus.svg';
import { useNotify } from '../../../components/common/useNotify';
import { ModalFoot, PageHead } from '../../hr/parts';
import CriteriaPreview, { SCALE, band, num } from './CriteriaPreview';
import './et.css';

const CATEGORIES = ['성과', '역량', '태도'];
let seq = 0;
const key = () => `n${++seq}`;
const blankQuestion = () => ({ key: key(), content: '' });
const blankCriteria = (weight = 0) => ({ key: key(), category: '성과', name: '', weight, questions: [blankQuestion()] });
const NEW = { name: '', criteria: [blankCriteria(100)] };

/** 서버 상세 → 편집 상태(행마다 key) */
const toDraft = (d) => ({
  name: d.name,
  criteria: d.criteria.map((c) => ({ key: key(), category: c.category, name: c.name, weight: c.weight, questions: c.questions.map((q) => ({ key: key(), content: q.content })) })),
});
/** 편집 상태 → 저장 본문(순서는 화면 순서) */
const toBody = (d) => ({
  name: d.name.trim(),
  criteria: d.criteria.map((c, i) => ({
    category: c.category,
    name: c.name.trim(),
    weight: c.weight,
    sortOrder: i + 1,
    questions: c.questions.map((q, j) => ({ content: q.content.trim(), sortOrder: j + 1 })),
  })),
});
const sameDraft = (a, b) => JSON.stringify(toBody(a)) === JSON.stringify(toBody(b));

/** 저장 전 확인 — 서버 규칙과 같은 문장으로 먼저 알려 준다 */
function problemOf(d) {
  if (!d.name.trim()) return '템플릿명을 입력하세요';
  if (!d.criteria.length) return '항목을 1개 이상 넣으세요';
  const names = new Set();
  for (const c of d.criteria) {
    if (!c.name.trim()) return '항목 이름을 입력하세요';
    if (names.has(c.name.trim())) return `항목 이름이 겹칩니다: ${c.name.trim()}`;
    names.add(c.name.trim());
    if (!c.weight) return `${c.name} 항목의 배점을 입력하세요`;
    if (!c.questions.length) return `${c.name} 항목에 질문을 1개 이상 넣으세요`;
    if (c.questions.some((q) => !q.content.trim())) return `${c.name} 항목에 비어 있는 질문이 있습니다`;
  }
  const sum = d.criteria.reduce((a, c) => a + (c.weight || 0), 0);
  if (sum !== 100) return `항목 배점 합계가 100이어야 합니다(지금 ${sum})`;
  return null;
}

export default function EvalTemplatePage() {
  const { modal } = App.useApp();
  const [sp, setSp] = useSearchParams();
  const list = useQuery({ queryKey: evalTemplateListKey, queryFn: getEvalTemplateList });
  const templates = useMemo(() => list.data ?? [], [list.data]);
  const isNew = sp.get('id') === 'new';
  const picked = isNew ? null : (templates.find((t) => String(t.id) === sp.get('id')) ?? templates.find((t) => t.isActive) ?? templates[0]);
  const [dirty, setDirty] = useState(false);

  // 고친 내용이 있으면 다른 템플릿으로 가기 전에 묻는다
  const go = (id) => {
    const move = () => {
      setDirty(false);
      setSp(id == null ? {} : { id: String(id) });
    };
    if (!dirty) return move();
    modal.confirm({ title: '저장하지 않은 내용이 있습니다', content: '이동하면 고친 내용이 사라집니다. 이동할까요?', okText: '이동', cancelText: '계속 편집', onOk: move });
  };

  return (
    <div className="hr et">
      <PageHead title="평가 템플릿" desc="평가에 쓰는 항목 · 배점 · 질문을 관리합니다.">
        <button type="button" className="hr-btn" onClick={() => go('new')}>
          새 템플릿
        </button>
      </PageHead>

      {list.isLoading ? (
        <div className="page-center" style={{ minHeight: '20rem' }}>
          <Spin />
        </div>
      ) : (
        <div className="et-layout">
          <aside className="et-list" aria-label="평가 템플릿 목록">
            <div className="et-list-head">{`템플릿 ${templates.length}개`}</div>
            {isNew && (
              <button type="button" className="et-list-item is-on">
                <strong>새 템플릿</strong>
                <span>작성 중</span>
              </button>
            )}
            {templates.map((t) => (
              <button key={t.id} type="button" className={`et-list-item${!isNew && picked?.id === t.id ? ' is-on' : ''}`} onClick={() => go(t.id)}>
                <strong>{t.name}</strong>
                <span>
                  {`항목 ${t.criteriaCount} · 질문 ${t.questionCount}`}
                  {!t.isActive ? <em className="hr-badge is-gray">사용 중지</em> : t.inUse && <em className="hr-badge is-info">사용 중</em>}
                </span>
              </button>
            ))}
          </aside>
          {isNew ? (
            <Editor key="new" initial={NEW} onDirty={setDirty} onSaved={(d) => go(d.id)} />
          ) : picked ? (
            <Loaded key={picked.id} id={picked.id} onDirty={setDirty} onMoved={(id) => { setDirty(false); setSp(id == null ? {} : { id: String(id) }); }} />
          ) : (
            <section className="et-card et-empty">
              <strong>평가 템플릿이 없습니다</strong>
              <span>새 템플릿을 만들어 항목 · 배점 · 질문을 넣으세요.</span>
            </section>
          )}
        </div>
      )}
    </div>
  );
}

function Loaded({ id, onDirty, onMoved }) {
  const detail = useQuery({ queryKey: evalTemplateKey(id), queryFn: () => getEvalTemplate(id) });
  if (detail.isLoading || !detail.data) {
    return (
      <section className="et-card page-center" style={{ minHeight: '20rem' }}>
        <Spin />
      </section>
    );
  }
  return <Editor key={detail.dataUpdatedAt} saved={detail.data} initial={toDraft(detail.data)} onDirty={onDirty} onSaved={(d) => onMoved(d.id)} onMoved={onMoved} />;
}

/* ───── 템플릿 편집기(72-7357) ───── */
function Editor({ saved, initial, onDirty, onSaved, onMoved }) {
  const { modal, message } = App.useApp();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState(initial);
  const [preview, setPreview] = useState(false);
  const [copying, setCopying] = useState(false);
  const [drag, setDrag] = useState(null); // { ci, qi } — 끌고 있는 질문
  const locked = !!saved?.inUse;
  const dirty = !sameDraft(draft, initial);
  useEffect(() => onDirty(dirty), [dirty, onDirty]);

  const sum = draft.criteria.reduce((a, c) => a + (c.weight || 0), 0);
  const questionCount = draft.criteria.reduce((a, c) => a + c.questions.length, 0);
  const setCriteria = (ci, patch) => setDraft((d) => ({ ...d, criteria: d.criteria.map((c, i) => (i === ci ? { ...c, ...patch } : c)) }));
  const setQuestions = (ci, fn) => setDraft((d) => ({ ...d, criteria: d.criteria.map((c, i) => (i === ci ? { ...c, questions: fn(c.questions) } : c)) }));
  const removeCriteria = (ci) => setDraft((d) => ({ ...d, criteria: d.criteria.filter((_, i) => i !== ci) }));
  const moveQuestion = (ci, from, to) =>
    setQuestions(ci, (qs) => {
      const next = [...qs];
      const [q] = next.splice(from, 1);
      next.splice(to, 0, q);
      return next;
    });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['eval-templates'] });
  const save = useMutation({
    mutationFn: () => (saved ? updateEvalTemplate(saved.id, toBody(draft)) : createEvalTemplate(toBody(draft))),
    onSuccess: (d) => {
      refresh();
      notify.success(saved ? `${d.name}을(를) 저장했습니다` : `${d.name}을(를) 만들었습니다`);
      onSaved(d);
    },
    onError: (err) => {
      if (err?.code === 'EVAL_TEMPLATE_IN_USE') {
        refresh();
        modal.confirm({ title: '이 템플릿은 고칠 수 없습니다', content: '시작된 평가 기간에 쓰인 템플릿입니다. 복사해서 고치세요.', okText: '복사해서 고치기', cancelText: '닫기', onOk: () => setCopying(true) });
        return;
      }
      notify.error(err);
    },
  });
  const remove = useMutation({
    mutationFn: () => deleteEvalTemplate(saved.id),
    onSuccess: (r) => {
      refresh();
      if (r?.result === 'DEACTIVATED') {
        message.success(`${saved.name}은(는) 이미 쓰인 템플릿이라 사용 중지로 바꿨습니다`);
      } else {
        message.success(`${saved.name}을(를) 삭제했습니다`);
        onMoved(null);
      }
    },
    onError: (err) => notify.error(err),
  });
  const submit = () => {
    const problem = problemOf(draft);
    if (problem) return message.warning(problem);
    save.mutate();
  };
  const askRemove = () =>
    modal.confirm({
      title: `${saved.name}을(를) 삭제할까요?`,
      content: '평가 기간에 쓰인 적이 있는 템플릿은 지워지지 않고 사용 중지로 바뀝니다.',
      okText: '삭제',
      okButtonProps: { danger: true },
      cancelText: '취소',
      onOk: () => remove.mutateAsync(),
    });

  return (
    <section className="et-card et-editor">
      <div className="et-top">
        <div className="et-summary">
          <span className={`et-sum${sum === 100 ? ' is-ok' : ' is-bad'}`}>{sum === 100 ? '✓ 배점 합계 100 / 100점' : `배점 합계 ${sum} / 100점`}</span>
          <span className="et-muted">{`총 ${draft.criteria.length}개 항목 · 질문 ${questionCount}개`}</span>
        </div>
        <div className="et-actions">
          {saved && (
            <>
              {saved.isActive && (
                <button type="button" className="et-btn is-text is-red" onClick={askRemove}>
                  삭제
                </button>
              )}
              <button type="button" className="et-btn is-line" onClick={() => setCopying(true)}>
                복사
              </button>
            </>
          )}
          <button type="button" className="et-btn is-line" onClick={() => setPreview(true)}>
            기준표 미리보기
          </button>
          {!locked && (
            <button type="button" className="et-btn" disabled={save.isPending || (saved && !dirty)} onClick={submit}>
              저장
            </button>
          )}
        </div>
      </div>

      {locked && (
        <div className="et-notice">
          <span>시작된 평가 기간에 쓰인 템플릿이라 고칠 수 없습니다. 복사해서 고치세요.</span>
          <button type="button" className="et-btn" onClick={() => setCopying(true)}>
            복사해서 고치기
          </button>
        </div>
      )}
      {saved && !saved.isActive && (
        <div className="et-notice is-gray">
          <span>사용 중지된 템플릿입니다. 새 평가 기간의 평가 유형으로 고를 수 없습니다.</span>
        </div>
      )}

      <label className="et-field">
        <span>템플릿명</span>
        <Input value={draft.name} maxLength={50} disabled={locked} placeholder="예: 반기 종합평가" onChange={(e) => setDraft((d) => ({ ...d, name: e.target.value }))} />
      </label>

      <div className="et-items">
        {draft.criteria.map((c, ci) => (
          <div key={c.key} className="et-item">
            <div className="et-item-head">
              <div>
                <span className="et-weight">{`${c.weight || 0}점`}</span>
                <strong>{c.name || '새 항목'}</strong>
              </div>
              {!locked && (
                <div className="et-item-acts">
                  <button type="button" className="et-btn is-text" onClick={() => setQuestions(ci, (qs) => [...qs, blankQuestion()])}>
                    + 질문 추가
                  </button>
                  {draft.criteria.length > 1 && (
                    <button type="button" className="et-btn is-text is-red" onClick={() => removeCriteria(ci)}>
                      항목 삭제
                    </button>
                  )}
                </div>
              )}
            </div>
            <div className="et-item-fields">
              <label className="et-field is-cat">
                <span>구분</span>
                <Select value={c.category} disabled={locked} options={CATEGORIES.map((v) => ({ value: v, label: v }))} onChange={(v) => setCriteria(ci, { category: v })} />
              </label>
              <label className="et-field is-grow">
                <span>항목 이름</span>
                <Input value={c.name} maxLength={50} disabled={locked} placeholder="예: 업무 성과" onChange={(e) => setCriteria(ci, { name: e.target.value })} />
              </label>
              <label className="et-field is-weight">
                <span>배점</span>
                <InputNumber value={c.weight} min={1} max={100} precision={0} disabled={locked} onChange={(v) => setCriteria(ci, { weight: v ?? 0 })} />
              </label>
            </div>

            <div className="et-questions">
              {c.questions.map((q, qi) => (
                <div
                  key={q.key}
                  className={`et-q${drag?.ci === ci && drag?.qi === qi ? ' is-drag' : ''}`}
                  onDragOver={(e) => drag?.ci === ci && e.preventDefault()}
                  onDrop={() => {
                    if (drag?.ci === ci && drag.qi !== qi) moveQuestion(ci, drag.qi, qi);
                    setDrag(null);
                  }}
                >
                  <span
                    className="et-grip"
                    draggable={!locked}
                    title={locked ? undefined : '끌어서 순서 바꾸기'}
                    onDragStart={() => setDrag({ ci, qi })}
                    onDragEnd={() => setDrag(null)}
                    aria-hidden
                  >
                    <img src={iconGrip} alt="" width="14" height="14" draggable={false} />
                  </span>
                  <span className="et-q-no">{qi + 1}</span>
                  <Input
                    value={q.content}
                    maxLength={500}
                    disabled={locked}
                    placeholder="질문을 입력하세요"
                    onChange={(e) => setQuestions(ci, (qs) => qs.map((x, j) => (j === qi ? { ...x, content: e.target.value } : x)))}
                  />
                  {!locked && (
                    <button type="button" className="et-btn is-text is-red" disabled={c.questions.length <= 1} title={c.questions.length <= 1 ? '항목마다 질문이 1개 이상 있어야 합니다' : undefined} onClick={() => setQuestions(ci, (qs) => qs.filter((_, j) => j !== qi))}>
                      삭제
                    </button>
                  )}
                </div>
              ))}
              {!locked && (
                <button type="button" className="et-btn is-text et-self-start" onClick={() => setQuestions(ci, (qs) => [...qs, blankQuestion()])}>
                  + 질문 추가
                </button>
              )}
            </div>

            <div className="et-bands">
              <div className="et-bands-head">
                <strong>세부 기준</strong>
                <span className="et-muted">배점으로 자동 계산됩니다</span>
              </div>
              <div className="et-band is-head">
                <span>점수 구간</span>
                <span>질문 평균</span>
                <span>설명</span>
              </div>
              {SCALE.map(([score, min, name, desc], i) => (
                <div key={score} className="et-band">
                  <strong>{`${num(band(c.weight || 0, min))}–${num(i === 0 ? c.weight || 0 : band(c.weight || 0, SCALE[i - 1][1]))}점`}</strong>
                  <span>{i === 0 ? `${min} 이상` : `${min} ~ ${num(SCALE[i - 1][1] - 0.1)}`}</span>
                  <span>{`${name} — ${desc}`}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {!locked && (
        <button type="button" className="et-add" onClick={() => setDraft((d) => ({ ...d, criteria: [...d.criteria, blankCriteria(Math.max(0, 100 - sum))] }))}>
          <img src={iconPlus} alt="" width="16" height="16" />
          항목 추가
        </button>
      )}

      {preview && <CriteriaPreview draft={draft} onClose={() => setPreview(false)} />}
      {copying && <CopyModal source={saved} onClose={() => setCopying(false)} onCopied={(d) => onMoved(d.id)} />}
    </section>
  );
}

/* ───── 복사 — 이름만 받아 서버에서 통째로 복사 ───── */
function CopyModal({ source, onClose, onCopied }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const copy = useMutation({
    mutationFn: (v) => copyEvalTemplate(source.id, v.name.trim()),
    onSuccess: (d) => {
      queryClient.invalidateQueries({ queryKey: ['eval-templates'] });
      notify.success(`${d.name}을(를) 만들었습니다`);
      onClose();
      onCopied(d);
    },
    onError: (err) => notify.error(err),
  });
  return (
    <Modal open title="템플릿 복사" footer={null} onCancel={onClose} width={480} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{`${source.name}의 항목 · 배점 · 질문을 그대로 복사합니다.`}</p>
      <Form form={form} layout="vertical" requiredMark={false} initialValues={{ name: `${source.name} (복사)`.slice(0, 50) }} onFinish={(v) => copy.mutate(v)}>
        <Form.Item name="name" label="새 템플릿명" rules={[{ required: true, whitespace: true, message: '템플릿명을 입력하세요' }]}>
          <Input maxLength={50} autoFocus />
        </Form.Item>
        <ModalFoot onCancel={onClose} okText="복사" busy={copy.isPending} />
      </Form>
    </Modal>
  );
}

