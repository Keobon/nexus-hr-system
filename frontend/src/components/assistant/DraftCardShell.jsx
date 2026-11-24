// AI 결재 초안 확인 카드의 공통 뼈대 — 휴가 · 연장근무 · 출장 카드가 같이 쓴다(확정 디자인: 라벤더 톤).
// 헤더 띠(별 · 제목 · 상태 칩) · 라벨–값 행 · 승인선 pill 칩 · INVALID 이유 · 버튼 3개 · 카드 아래 안내문.
// 업무별 카드는 제목 · 행 · 승인선 · 오류 문구만 넘긴다. 직원이 쓴 글은 React 기본 이스케이프로만(dangerouslySetInnerHTML 쓰지 않음)
import { Fragment } from 'react';
import { errorMessage } from '../../constants/errors';
import { ArrowIcon, StarIcon } from './icons';

/**
 * INVALID 이유 — 오류 코드를 다른 화면과 같은 문장으로(errorMessage). overrides 로 이 카드에서만 문구를 바꾼다,
 * details(code) 로 문장에 숫자를 넣는다. 서버가 되물은 문장(question)도 같이
 */
export function problemMessages({ errors = [], question }, { overrides = {}, details } = {}) {
  const messages = errors.map((code) => overrides[code] ?? errorMessage({ code, details: details?.(code) }));
  if (question && !messages.includes(question)) messages.push(question);
  return messages.length ? messages : ['이대로는 신청할 수 없습니다'];
}

/**
 * 승인선 — 미리보기 단계({ stepOrder, approverName, skipped })를 "1 · 서예린" pill 로. 첫 단계(생략 제외)가 지금 받을 사람(진한 칩),
 * 나머지는 대기, 생략 단계는 취소선
 */
function StepChips({ steps = [] }) {
  if (!steps.length) return <span className="as-card-none">승인 단계 없이 바로 승인됩니다</span>;
  const first = steps.find((s) => !s.skipped);
  return (
    <span className="as-chips">
      {steps.map((s, i) => (
        <Fragment key={s.stepOrder ?? i}>
          {i > 0 && <ArrowIcon className="as-chip-arrow" />}
          <span className={`as-chip${s === first ? ' is-on' : ''}${s.skipped ? ' is-skip' : ''}`}>
            {`${s.stepOrder} · ${s.approverName ?? '승인자 미지정'}`}
          </span>
        </Fragment>
      ))}
    </span>
  );
}

/**
 * title: 카드 제목 · rows: [[라벨, 값]] · steps: 승인선(미리보기가 없으면 null → 행을 그리지 않음)
 * invalid: INVALID 면 빨간 칩 · [올리기] 비활성 · problems(문장 목록) 표시
 */
export default function DraftCardShell({ title, rows, steps, invalid, problems = [], active, busy, onSubmit, onEdit, onDiscard }) {
  return (
    <>
      <div className={`as-card${active ? '' : ' is-done'}`}>
        <div className="as-card-head">
          <StarIcon className="as-card-star" />
          <p className="as-card-title">{title}</p>
          <span className={`as-state${invalid ? ' is-bad' : ' is-ok'}`}>{invalid ? '신청 불가' : '신청 가능'}</span>
        </div>
        <div className="as-card-body">
          <dl className="as-card-kv">
            {rows.map(([k, v]) => (
              <div key={k}>
                <dt>{k}</dt>
                <dd>{v}</dd>
              </div>
            ))}
            {steps && (
              <div className="is-steps">
                <dt>승인선</dt>
                <dd>
                  <StepChips steps={steps} />
                </dd>
              </div>
            )}
          </dl>
          {invalid && (
            <div className="as-card-alert" role="alert">
              <strong>이대로는 올릴 수 없어요</strong>
              <ul className="as-card-errors">
                {problems.map((m) => (
                  <li key={m}>{m}</li>
                ))}
              </ul>
            </div>
          )}
          {active && (
            <div className="as-card-actions">
              <button type="button" className="as-btn is-primary" onClick={onSubmit} disabled={invalid || busy} aria-busy={busy}>
                올리기
              </button>
              <button type="button" className="as-btn is-ghost" onClick={onEdit} disabled={busy}>
                수정할래요
              </button>
              <button type="button" className="as-btn is-danger" onClick={onDiscard} disabled={busy}>
                그만두기
              </button>
            </div>
          )}
        </div>
      </div>
      {active && <p className="as-card-hint">[올리기]를 누르기 전에는 신청되지 않아요 · 초안은 30분간 유지돼요</p>}
    </>
  );
}
