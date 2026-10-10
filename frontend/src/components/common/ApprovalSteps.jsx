import { Divider, Empty, Space, Tag, Timeline, Typography } from 'antd';
import {
  CheckCircleFilled,
  ClockCircleFilled,
  CloseCircleFilled,
  MinusCircleOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { LABELS } from '../../constants/labels';
import { formatDateTime, formatMinutes } from '../../utils/format';

// 단계 상태별 점 모양 · 색(기능명세서 15.2, 프론트 가이드 부록 A)
const DOT = {
  APPROVED: { color: 'green', icon: <CheckCircleFilled /> },
  REJECTED: { color: 'red', icon: <CloseCircleFilled /> },
  PENDING: { color: 'blue', icon: <ClockCircleFilled /> },
  WAITING: { color: 'gray', icon: null },
  SKIPPED: { color: 'gray', icon: <MinusCircleOutlined /> },
  CANCELLED: { color: 'gray', icon: <StopOutlined /> },
};

/**
 * 휴가 신청 미리보기의 steps({ stepOrder, approverName, skipped })도 같은 모양으로 맞춘다.
 * 미리보기는 아직 단계가 없으므로 생략 단계만 SKIPPED, 나머지는 WAITING.
 */
function normalize(step) {
  if (step.status) return step;
  return { ...step, round: 1, status: step.skipped ? 'SKIPPED' : 'WAITING' };
}

function groupByRound(steps) {
  const rounds = new Map();
  steps.forEach((s) => {
    const r = s.round ?? 1;
    if (!rounds.has(r)) rounds.set(r, []);
    rounds.get(r).push(s);
  });
  return [...rounds.entries()].sort(([a], [b]) => a - b);
}

function StepContent({ step, showMinutes }) {
  const skipped = step.status === 'SKIPPED';
  const label = step.isMyTurn ? null : LABELS.approvalStepStatus[step.status];
  return (
    <div className="approval-step">
      <Space size={6} wrap>
        <Typography.Text type="secondary">{step.stepOrder}단계</Typography.Text>
        <Typography.Text strong={!skipped} delete={skipped}>
          {step.approverName ?? '승인자 미지정'}
        </Typography.Text>
        {step.isMyTurn ? <Tag color="blue">내 차례</Tag> : label && <Tag color={label.color}>{label.label}</Tag>}
      </Space>
      {(step.actedAt || (showMinutes && step.approvedMinutes !== null && step.approvedMinutes !== undefined)) && (
        <div className="approval-step-meta">
          {step.actedAt && <span>{formatDateTime(step.actedAt)}</span>}
          {showMinutes && step.approvedMinutes !== null && step.approvedMinutes !== undefined && (
            <span>인정 {formatMinutes(step.approvedMinutes)}</span>
          )}
        </div>
      )}
      {step.comment && <div className="approval-step-comment">{step.comment}</div>}
    </div>
  );
}

/**
 * 승인 진행 표시(프론트 가이드 2.5 공용 · F-APPR-04) — 신청 상세의 approvalSteps 를 단계 타임라인으로.
 * 휴가 · 연장근무 · 출장 · 출장 경비 상세와 승인함, 휴가 신청 미리보기가 같이 쓴다.
 *
 * - steps: approvalSteps(API 9장) 또는 휴가 미리보기 steps(API 8장)
 * - showMinutes: 연장근무면 true — 단계마다 인정 시간(approvedMinutes)을 보여 준다
 * - 회차(round)가 2 이상이면 휴가 취소 요청 단계다(API 8장) — "취소 요청" 구분선으로 나눈다
 * - 단계가 하나도 없으면 즉시 승인(모든 단계 생략, 기능명세서 9.1-4)
 */
export default function ApprovalSteps({ steps, showMinutes = false, emptyText = '승인 단계 없이 바로 승인되었습니다' }) {
  if (!steps?.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  }

  const rounds = groupByRound(steps.map(normalize));
  return (
    <div className="approval-steps">
      {rounds.map(([round, roundSteps], i) => (
        <div key={round}>
          {rounds.length > 1 && (
            <Divider titlePlacement="start" plain className="approval-round">
              {i === 0 ? '신청' : rounds.length > 2 ? `취소 요청 ${i}차` : '취소 요청'}
            </Divider>
          )}
          <Timeline
            items={roundSteps
              .slice()
              .sort((a, b) => a.stepOrder - b.stepOrder)
              .map((step) => ({
                key: step.stepId ?? `${round}-${step.stepOrder}`,
                color: DOT[step.status]?.color ?? 'gray',
                icon: DOT[step.status]?.icon ?? undefined,
                content: <StepContent step={step} showMinutes={showMinutes} />,
              }))}
          />
        </div>
      ))}
    </div>
  );
}
