// AI 휴가 초안 확인 카드 — 종류 · 날짜(요일) · 반차 · 사유 · 일수 · 사용 후 잔여 · 승인선. [올리기]를 눌러야 실제로 신청된다.
// 사유 등 직원이 쓴 글은 React 기본 이스케이프로만 보여 준다(dangerouslySetInnerHTML 쓰지 않음).
// 확정 디자인(라벤더 톤): 헤더 띠 + 상태 칩, 라벨–값 행, 승인선은 pill 칩, 버튼은 AntD 기본 대신 직접 그린다
import { Fragment } from 'react';
import { ArrowRightOutlined, StarFilled } from '@ant-design/icons';
import { errorMessage } from '../../constants/errors';
import { labelOf } from '../../constants/labels';
import { formatDate, formatDays } from '../../utils/format';

function period(draft) {
  if (!draft.startDate) return '-';
  if (!draft.endDate || draft.startDate === draft.endDate) return formatDate(draft.startDate);
  return `${formatDate(draft.startDate)} ~ ${formatDate(draft.endDate)}`;
}

/** INVALID 이유 — 서버 오류 코드를 휴가 신청 화면과 같은 문장으로. 잔여 부족은 미리보기 숫자를 넣는다 */
function problems(data) {
  const { errors = [], preview, question } = data;
  const messages = errors.map((code) =>
    errorMessage({
      code,
      details:
        code === 'LEAVE_INSUFFICIENT_BALANCE' && preview?.balance
          ? { remaining: preview.balance.remaining, requested: preview.days }
          : undefined,
    }),
  );
  if (question && !messages.includes(question)) messages.push(question);
  return messages.length ? messages : ['이대로는 신청할 수 없습니다'];
}

/**
 * 승인선 — 미리보기 steps({ stepOrder, approverName, skipped })를 "1 · 서예린" pill 로. 첫 단계(생략 제외)가 먼저 받는 사람(진한 칩),
 * 나머지는 대기, 생략 단계는 취소선
 */
function StepChips({ steps = [] }) {
  if (!steps.length) return <span className="as-card-none">승인 단계 없이 바로 승인됩니다</span>;
  const first = steps.find((s) => !s.skipped);
  return (
    <span className="as-chips">
      {steps.map((s, i) => (
        <Fragment key={s.stepOrder ?? i}>
          {i > 0 && <ArrowRightOutlined className="as-chip-arrow" aria-hidden="true" />}
          <span className={`as-chip${s === first ? ' is-on' : ''}${s.skipped ? ' is-skip' : ''}`}>
            {`${s.stepOrder} · ${s.approverName ?? '승인자 미지정'}`}
          </span>
        </Fragment>
      ))}
    </span>
  );
}

export default function LeaveDraftCard({ data, active, busy, onSubmit, onEdit, onDiscard }) {
  const { draft = {}, preview, status } = data;
  const invalid = status === 'INVALID';
  const rows = [
    ['휴가 종류', draft.leaveTypeName ?? '-'],
    ['날짜', period(draft)],
    ['반차', draft.halfDay ? labelOf('halfDay', draft.halfDay) : '종일'],
    ['사유', draft.reason || '-'],
    ['일수', preview ? formatDays(preview.days) : '-'],
    [
      '사용 후 잔여',
      !preview ? '-' : preview.balance ? formatDays(preview.balance.remainingAfter) : '잔여에서 빼지 않는 휴가',
    ],
  ];

  return (
    <>
      <div className={`as-card${active ? '' : ' is-done'}`}>
        <div className="as-card-head">
          <StarFilled className="as-card-star" aria-hidden="true" />
          <p className="as-card-title">휴가 결재 초안</p>
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
            {preview && (
              <div className="is-steps">
                <dt>승인선</dt>
                <dd>
                  <StepChips steps={preview.steps} />
                </dd>
              </div>
            )}
          </dl>
          {invalid && (
            <div className="as-card-alert" role="alert">
              <strong>이대로는 올릴 수 없어요</strong>
              <ul className="as-card-errors">
                {problems(data).map((m) => (
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
