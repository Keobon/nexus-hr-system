// AI 휴가 초안 확인 카드 — 종류 · 날짜(요일 · 반차) · 사유 · 일수 · 사용 후 잔여 · 승인선. [올리기]를 눌러야 실제로 신청된다.
// 뼈대(헤더 띠 · 행 · 승인선 · 버튼 · 안내문)는 DraftCardShell — 여기서는 휴가 행과 오류 문구만 만든다
import { labelOf } from '../../constants/labels';
import { formatDate, formatDays } from '../../utils/format';
import DraftCardShell, { problemMessages } from './DraftCardShell';

/** "2026.11.25 (수) · 종일" · "… · 오후 반차" · 기간이면 "시작 ~ 끝 · 종일" */
function period(draft) {
  if (!draft.startDate) return '-';
  const days =
    !draft.endDate || draft.startDate === draft.endDate
      ? formatDate(draft.startDate)
      : `${formatDate(draft.startDate)} ~ ${formatDate(draft.endDate)}`;
  return `${days} · ${draft.halfDay ? labelOf('halfDay', draft.halfDay) : '종일'}`;
}

export default function LeaveDraftCard({ data, ...actions }) {
  const { draft = {}, preview, status } = data;
  const usage = !preview ? (
    '-'
  ) : (
    <>
      {formatDays(preview.days)}
      {' · '}
      {preview.balance ? (
        <>
          잔여 <em className="as-remain">{formatDays(preview.balance.remainingAfter)}</em>
        </>
      ) : (
        '잔여에서 빼지 않는 휴가'
      )}
    </>
  );
  // 잔여 부족은 미리보기 숫자를 넣어 휴가 신청 화면과 같은 문장으로
  const problems = problemMessages(data, {
    details: (code) =>
      code === 'LEAVE_INSUFFICIENT_BALANCE' && preview?.balance
        ? { remaining: preview.balance.remaining, requested: preview.days }
        : undefined,
  });

  return (
    <DraftCardShell
      title="휴가 결재 초안"
      rows={[
        ['휴가 종류', draft.leaveTypeName ?? '-'],
        ['날짜', period(draft)],
        ['사유', draft.reason || '-'],
        ['일수 · 사용 후 잔여', usage],
      ]}
      steps={preview ? preview.steps : null}
      invalid={status === 'INVALID'}
      problems={problems}
      {...actions}
    />
  );
}
