// 평가 결과 — Figma 72-7648(확정) · 473-13124(확정 대기). 왼쪽 대상자 표, 오른쪽 고른 사람의 결과 · 확정 / 확정 취소
// 서버: 평가자 한 명 · 등급 없음 · 종합 의견은 평가자만 고친다 → 평가 상태 한 칸, 척도 수준
import { useState } from 'react';
import { App, Input, Modal, Select, Spin } from 'antd';
import { useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  confirmEvaluation,
  evalCyclesKey,
  evalProgressKey,
  evaluationKey,
  getEvalCycles,
  getEvalProgress,
  getEvaluation,
  reopenEvaluation,
} from '../../../api/evalAdmin';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { HALF_MONTHS, EvalStatusChip, avgOf, cycleParts, levelOf, num, resultState, yearOptions } from './shared';

export default function ResultTab({ manage, initialCycle }) {
  // 조회만 하는 사람은 EvalReadView(EvaluationAdminPage 에서 분기)
  return manage ? <ManageResults initialCycle={initialCycle} /> : null;
}

/* ───── 인사 담당(EVAL_MANAGE) — 진행 현황으로 전원 · 확정 / 확정 취소 ───── */
function ManageResults({ initialCycle }) {
  const cycles = useQuery({ queryKey: evalCyclesKey, queryFn: getEvalCycles });
  const started = (cycles.data ?? []).filter((c) => c.status !== 'SCHEDULED').sort((a, b) => b.startDate.localeCompare(a.startDate));
  const first = started.find((c) => String(c.id) === initialCycle) ?? started[0];
  const [draft, setDraft] = useState({});
  const [applied, setApplied] = useState({});
  const year = draft.year ?? (first ? cycleParts(first).year : undefined);
  const inYear = started.filter((c) => cycleParts(c).year === year);
  const cycleId = draft.cycleId ?? (inYear.some((c) => c.id === first?.id) ? first?.id : inYear[0]?.id);
  const shownCycleId = applied.cycleId ?? first?.id;
  const cycle = started.find((c) => c.id === shownCycleId);

  const progress = useQuery({ queryKey: evalProgressKey(shownCycleId), queryFn: () => getEvalProgress(shownCycleId), enabled: !!shownCycleId });
  const items = progress.data?.items ?? [];
  // 점수 칸 · 오른쪽 결과 — 제출 · 확정된 사람은 상세(항목 점수)를 받는다
  const scored = items.filter((i) => i.status === 'SUBMITTED' || i.status === 'CONFIRMED');
  const details = useQueries({ queries: scored.map((i) => ({ queryKey: evaluationKey(i.evaluationId), queryFn: () => getEvaluation(i.evaluationId) })) });
  const detailById = Object.fromEntries(scored.map((i, k) => [i.evaluationId, details[k]?.data]));
  const criteria = details.find((d) => d.data)?.data.criteria.map((c) => c.name) ?? [];

  const types = [...new Set(items.map((i) => i.templateName))];
  const orgs = [...new Set(items.map((i) => i.orgUnitName))];
  const term = (applied.term ?? '').trim().toLowerCase();
  const rows = items.filter((i) => (!applied.type || i.templateName === applied.type) && (!applied.org || i.orgUnitName === applied.org) && (!term || i.targetName.toLowerCase().includes(term)));
  const [pick, setPick] = useState(null);
  const selected = rows.find((r) => r.evaluationId === pick) ?? rows.find((r) => r.status === 'SUBMITTED') ?? rows[0];

  if (cycles.isLoading) return <Spin />;
  if (!started.length) return <div className="ea-empty">시작한 평가 기간이 없습니다. 평가 기간 탭에서 평가를 시작하세요.</div>;

  return (
    <>
      <div className="ea-bar">
        <div className="hr-field">
          <label>평가 연도</label>
          <Select className="ea-select" value={year} onChange={(y) => setDraft((d) => ({ ...d, year: y, cycleId: undefined }))} options={yearOptions(started)} />
        </div>
        <div className="hr-field">
          <label>평가 기간</label>
          <Select
            className="ea-select is-wide"
            value={cycleId}
            onChange={(v) => setDraft((d) => ({ ...d, cycleId: v }))}
            options={inYear.map((c) => {
              const { half } = cycleParts(c);
              return { value: c.id, label: half ? `${half} (${HALF_MONTHS[half]})` : c.name };
            })}
          />
        </div>
        <div className="hr-field">
          <label>평가 유형</label>
          <Select className="ea-select" allowClear placeholder="전체 유형" value={draft.type} onChange={(v) => setDraft((d) => ({ ...d, type: v }))} options={types.map((t) => ({ value: t, label: t }))} />
        </div>
        <div className="hr-field">
          <label>부서</label>
          <Select className="ea-select" allowClear placeholder="전체 부서" value={draft.org} onChange={(v) => setDraft((d) => ({ ...d, org: v }))} options={orgs.map((o) => ({ value: o, label: o }))} />
        </div>
        <div className="hr-field ea-grow">
          <label>이름 검색</label>
          <Input allowClear placeholder="이름" value={draft.term} onChange={(e) => setDraft((d) => ({ ...d, term: e.target.value }))} onPressEnter={() => setApplied({ ...draft, cycleId })} />
        </div>
        <button type="button" className="hr-btn" onClick={() => setApplied({ ...draft, cycleId })}>
          조회
        </button>
      </div>

      <div className="ea-split">
        <section className="hr-table ea-card">
          <div className="ea-card-head">
            <strong>{`총 ${rows.length}명 평가 대상자`}</strong>
            <span>{cycle?.name}</span>
          </div>
          <div className="hr-tr is-head">
            <span className="ea-c-person">이름</span>
            <span className="ea-c-org">부서 · 평가자</span>
            <span className="ea-c-chip">평가</span>
            {criteria.map((c) => (
              <span key={c} className="ea-c-score">
                {c}
              </span>
            ))}
            <span className="ea-c-score">총점</span>
            <span className="ea-c-level">수준</span>
            <span className="ea-c-state">상태</span>
          </div>
          {progress.isLoading && (
            <div className="hr-tr is-empty">
              <Spin />
            </div>
          )}
          {progress.error && <div className="hr-tr is-empty">{errorMessage(progress.error)}</div>}
          {!progress.isLoading && rows.length === 0 && <div className="hr-tr is-empty">조건에 맞는 대상자가 없습니다</div>}
          {rows.map((r) => {
            const d = detailById[r.evaluationId];
            const [label, tone] = resultState(r.status);
            const avg = avgOf(r.totalScore);
            return (
              <div key={r.evaluationId} className={`hr-tr ea-row is-click${selected?.evaluationId === r.evaluationId ? ` is-selected${r.status === 'SUBMITTED' ? ' is-wait' : ''}` : ''}`} onClick={() => setPick(r.evaluationId)}>
                <span className="ea-c-person hr-strong">{r.targetName}</span>
                <span className="ea-c-org">{`${r.orgUnitName} · ${r.evaluatorName}`}</span>
                <span className="ea-c-chip">
                  <EvalStatusChip status={r.status} />
                </span>
                {criteria.map((c) => (
                  <span key={c} className="ea-c-score">
                    {num(d?.criteria.find((x) => x.name === c)?.convertedScore)}
                  </span>
                ))}
                <span className="ea-c-score ea-blue">{num(r.totalScore)}</span>
                <span className="ea-c-level">{r.totalScore == null ? '–' : levelOf(avg)}</span>
                <span className="ea-c-state">
                  <span className={`hr-badge ${tone}`}>{label}</span>
                </span>
              </div>
            );
          })}
        </section>

        <div className="ea-side">
          {selected ? <ResultPanel item={selected} cycle={cycle} detail={detailById[selected.evaluationId]} /> : <div className="hr-card ea-empty">대상자를 고르면 결과가 보입니다.</div>}
        </div>
      </div>
    </>
  );
}

/** 오른쪽 결과 카드 — 종합 수준 · 항목 막대 · 총점 · 종합 피드백 · 확정 / 확정 취소 */
function ResultPanel({ item, cycle, detail, readOnly }) {
  const notify = useNotify();
  const { modal } = App.useApp();
  const queryClient = useQueryClient();
  const [reopening, setReopening] = useState(false);
  const done = item.status === 'SUBMITTED' || item.status === 'CONFIRMED';
  const avg = avgOf(item.totalScore);
  const [label, tone] = resultState(item.status);
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: evalProgressKey(cycle.id) });
    queryClient.invalidateQueries({ queryKey: evaluationKey(item.evaluationId) });
    queryClient.invalidateQueries({ queryKey: ['evaluations', 'results'] });
  };
  const confirm = () =>
    modal.confirm({
      title: `${item.targetName}님의 평가를 확정할까요?`,
      content: '확정하면 본인에게 결과가 공개됩니다. 고칠 일이 생기면 확정 취소(재오픈)로 평가자에게 다시 열 수 있습니다.',
      okText: '확정',
      cancelText: '취소',
      onOk: async () => {
        try {
          await confirmEvaluation(item.evaluationId);
          refresh();
          notify.success(`${item.targetName}님의 평가를 확정했습니다`);
        } catch (err) {
          notify.error(err);
        }
      },
    });

  return (
    <section className="hr-card ea-result">
      <div className="ea-result-head">
        <div>
          <strong>{`${cycle?.name ?? item.cycleName ?? ''} 결과`}</strong>
          <span>{`${item.targetName} · ${item.orgUnitName} · ${item.templateName}`}</span>
        </div>
        <span className={`hr-badge ${tone}`}>{label}</span>
      </div>
      {!done && (
        <p className="ea-wait">{`평가자(${item.evaluatorName})가 아직 제출하지 않았습니다 — ${{ NOT_STARTED: '작성 전', IN_PROGRESS: '작성 중', REOPENED: '재오픈되어 다시 작성 중' }[item.status] ?? ''}`}</p>
      )}
      {item.needsEvaluatorChange && <p className="ea-warn">평가자가 퇴직 등으로 평가할 수 없습니다. 평가자를 바꿔야 합니다.</p>}
      {done && !detail && <Spin />}
      {done && detail && (
        <>
          <div className="ea-score">
            <div className="ea-level">
              <span>종합 수준</span>
              <strong>{num(avg)}</strong>
              <em>{levelOf(avg)}</em>
            </div>
            <div className="ea-bars">
              {detail.criteria.map((c) => (
                <div key={c.criteriaId ?? c.name}>
                  <div className="ea-bar-label">
                    <span>{c.name}</span>
                    <strong>{`${num(c.convertedScore)} / ${c.weight}점`}</strong>
                  </div>
                  <div className="ea-bar-track">
                    <i style={{ width: `${Math.min(100, ((Number(c.convertedScore) || 0) / c.weight) * 100)}%` }} />
                  </div>
                </div>
              ))}
              <div className="ea-total">
                <span>총점</span>
                <strong>{`${num(item.totalScore)} / 100점`}</strong>
              </div>
            </div>
          </div>
          {/* 조회 권한자도 종합 의견을 본다(10/19 결정, 결과 API overallComment) */}
          <div className="ea-feedback">
            <span>종합 피드백</span>
            <p>{detail.overallComment || '작성된 종합 의견이 없습니다.'}</p>
            {!readOnly && <em>{`평가자 ${item.evaluatorName}만 고칠 수 있습니다`}</em>}
          </div>
        </>
      )}
      {!readOnly && (
        <div className="ea-result-foot">
          <span className="ea-muted">{detail?.confirmedAt ? `확정일 ${detail.confirmedAt.slice(0, 10)}` : detail?.submittedAt ? `제출일 ${detail.submittedAt.slice(0, 10)}` : ''}</span>
          {item.status === 'SUBMITTED' && (
            <button type="button" className="hr-btn" onClick={confirm}>
              확정
            </button>
          )}
          {item.status === 'CONFIRMED' && (
            <button type="button" className="hr-btn is-secondary" onClick={() => setReopening(true)}>
              확정 취소
            </button>
          )}
        </div>
      )}
      {reopening && <ReopenModal item={item} onDone={refresh} onClose={() => setReopening(false)} />}
    </section>
  );
}

/** 확정 취소 = 재오픈(사유 필수) — 평가자가 다시 고쳐 제출하면 다시 확정 */
function ReopenModal({ item, onDone, onClose }) {
  const notify = useNotify();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try {
      await reopenEvaluation(item.evaluationId, reason.trim());
      onDone();
      notify.success(`${item.targetName}님의 평가 확정을 취소했습니다(평가자에게 다시 열림)`);
      onClose();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal
      open
      title="확정을 취소할까요?"
      okText="확정 취소"
      cancelText="닫기"
      okButtonProps={{ danger: true, disabled: !reason.trim(), loading: busy }}
      onOk={save}
      onCancel={onClose}
      className="hr-modal"
      centered
    >
      <p className="hr-modal-sub">{`${item.targetName}님에게 공개된 결과가 내려가고, 평가자(${item.evaluatorName})가 다시 고쳐 제출할 수 있게 됩니다.`}</p>
      <Input.TextArea rows={3} maxLength={255} placeholder="사유 (필수) — 예: 성과 점수 재검토 요청" value={reason} onChange={(e) => setReason(e.target.value)} />
    </Modal>
  );
}
