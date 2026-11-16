// AI 휴가 초안 확인 카드 — 종류 · 날짜(요일) · 반차 · 사유 · 일수 · 사용 후 잔여 · 승인선. [올리기]를 눌러야 실제로 신청된다.
// 사유 등 직원이 쓴 글은 React 기본 이스케이프로만 보여 준다(dangerouslySetInnerHTML 쓰지 않음).
import { Alert, Button } from 'antd';
import ApprovalSteps from '../common/ApprovalSteps';
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
    <div className={`as-card${active ? '' : ' is-done'}`}>
      <p className="as-card-title">휴가 결재 초안</p>
      <dl className="as-card-kv">
        {rows.map(([k, v]) => (
          <div key={k}>
            <dt>{k}</dt>
            <dd>{v}</dd>
          </div>
        ))}
      </dl>
      {invalid && (
        <Alert
          type="error"
          showIcon
          className="as-card-alert"
          title="이대로는 올릴 수 없어요"
          description={
            <ul className="as-card-errors">
              {problems(data).map((m) => (
                <li key={m}>{m}</li>
              ))}
            </ul>
          }
        />
      )}
      {preview && (
        <div className="as-card-steps">
          <p className="as-card-sub">승인선</p>
          <ApprovalSteps steps={preview.steps} emptyText="승인 단계 없이 바로 승인됩니다" />
        </div>
      )}
      {active && (
        <div className="as-card-actions">
          <Button type="primary" onClick={onSubmit} disabled={invalid || busy} loading={busy}>
            올리기
          </Button>
          <Button onClick={onEdit} disabled={busy}>
            수정할래요
          </Button>
          <Button danger onClick={onDiscard} disabled={busy}>
            그만두기
          </Button>
        </div>
      )}
    </div>
  );
}
