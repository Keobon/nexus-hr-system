// AI 출장 초안 확인 카드 — 목적지(국내 · 해외) · 기간 · 목적 · 예상 비용 · 승인선. [올리기]를 눌러야 실제로 신청된다.
// 뼈대는 DraftCardShell — 여기서는 출장 행과 오류 문구만 만든다
import { labelOf } from '../../constants/labels';
import { formatDate } from '../../utils/format';
import DraftCardShell, { problemMessages } from './DraftCardShell';

/** "2026.11.27 (금)" → "11.27 (금)" */
const short = (d) => formatDate(d).slice(5);

/** "11.27 (금) ~ 11.28 (토) · 2일" · 하루면 "11.27 (금) · 1일". 일수는 미리보기 값(없으면 날짜만) */
function period({ startDate, endDate }, days) {
  if (!startDate) return '-';
  const range = !endDate || endDate === startDate ? short(startDate) : `${short(startDate)} ~ ${short(endDate)}`;
  return days != null ? `${range} · ${days}일` : range;
}

export default function TripDraftCard({ data, ...actions }) {
  const { draft = {}, preview, status } = data;
  const destination = !draft.destination ? (
    '-'
  ) : (
    <>
      {draft.destination}
      {draft.tripType && <span className="as-mini-chip">{labelOf('tripType', draft.tripType)}</span>}
    </>
  );
  return (
    <DraftCardShell
      title="출장 결재 초안"
      rows={[
        ['목적지', destination],
        ['기간', period(draft, preview?.days)],
        ['목적', draft.purpose || '-'],
        ['예상 비용', draft.estimatedCost != null ? `${Number(draft.estimatedCost).toLocaleString('ko-KR')}원` : <span className="as-muted">—</span>],
      ]}
      steps={preview ? preview.approvalSteps : null}
      invalid={status === 'INVALID'}
      problems={problemMessages(data)}
      {...actions}
    />
  );
}
