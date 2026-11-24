// AI 연장근무 초안 확인 카드 — 날짜 · 시간(다음 날) · 총 시간 · 사유 · 승인선. [올리기]를 눌러야 실제로 신청된다.
// 뼈대는 DraftCardShell. 휴가 · 출장일 오류(ATT_ON_LEAVE_OR_TRIP)는 출퇴근 화면 문구 대신 연장근무 맥락 문장으로 이 카드에서만 바꾼다
import { formatDate, formatMinutes } from '../../utils/format';
import DraftCardShell, { problemMessages } from './DraftCardShell';

const OVERRIDES = { ATT_ON_LEAVE_OR_TRIP: '그날은 휴가·출장일이라 연장근무를 신청할 수 없어요' };

/** "19:00 ~ 22:00" — 종료가 시작보다 이르거나 같으면 "(다음 날)" */
function timeRange({ plannedStart, plannedEnd }) {
  if (!plannedStart && !plannedEnd) return '-';
  const nextDay = plannedStart && plannedEnd && plannedEnd <= plannedStart;
  return `${plannedStart ?? '-'} ~ ${plannedEnd ?? '-'}${nextDay ? ' (다음 날)' : ''}`;
}

export default function OvertimeDraftCard({ data, ...actions }) {
  const { draft = {}, preview, status } = data;
  return (
    <DraftCardShell
      title="연장근무 결재 초안"
      rows={[
        ['날짜', draft.workDate ? formatDate(draft.workDate) : '-'],
        ['시간', timeRange(draft)],
        ['총 시간', preview ? formatMinutes(preview.minutes) : '-'],
        ['사유', draft.reason || '-'],
      ]}
      steps={preview ? preview.approvalSteps : null}
      invalid={status === 'INVALID'}
      problems={problemMessages(data, { overrides: OVERRIDES })}
      {...actions}
    />
  );
}
