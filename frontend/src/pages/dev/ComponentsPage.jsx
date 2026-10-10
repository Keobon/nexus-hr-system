import { Card, Col, Form, Row, Typography } from 'antd';
import ApprovalSteps from '../../components/common/ApprovalSteps';
import FileUpload from '../../components/common/FileUpload';

// 개발 서버에서만 열리는 공통 컴포넌트 견본(/dev/components). 데이터는 API 설계서 8·9장 예시 모양
const IN_PROGRESS = [
  { stepId: 1, round: 1, stepOrder: 1, approverId: 10, approverName: '서예린', status: 'APPROVED', approvedMinutes: null, comment: '확인했습니다', actedAt: '2026-09-28T10:00:00+09:00', isMyTurn: false },
  { stepId: 2, round: 1, stepOrder: 2, approverId: 6, approverName: '강하늘', status: 'PENDING', approvedMinutes: null, comment: null, actedAt: null, isMyTurn: true },
];

const OVERTIME = [
  { stepId: 31, round: 1, stepOrder: 1, approverName: '서예린', status: 'APPROVED', approvedMinutes: 150, comment: '30분은 인정하기 어렵습니다', actedAt: '2026-10-06T11:00:00+09:00', isMyTurn: false },
  { stepId: 32, round: 1, stepOrder: 2, approverName: '강하늘', status: 'APPROVED', approvedMinutes: 120, comment: null, actedAt: '2026-10-06T15:20:00+09:00', isMyTurn: false },
];

const REJECTED = [
  { stepId: 41, round: 1, stepOrder: 1, approverName: '서예린', status: 'REJECTED', comment: '프로젝트 마감 주간입니다', actedAt: '2026-10-02T09:30:00+09:00', isMyTurn: false },
  { stepId: 42, round: 1, stepOrder: 2, approverName: '강하늘', status: 'CANCELLED', comment: null, actedAt: null, isMyTurn: false },
];

const WITH_CANCEL = [
  { stepId: 51, round: 1, stepOrder: 1, approverName: '서예린', status: 'APPROVED', comment: null, actedAt: '2026-09-20T10:00:00+09:00', isMyTurn: false },
  { stepId: 52, round: 1, stepOrder: 2, approverName: '강하늘', status: 'APPROVED', comment: null, actedAt: '2026-09-21T14:00:00+09:00', isMyTurn: false },
  { stepId: 53, round: 2, stepOrder: 1, approverName: '강하늘', status: 'PENDING', comment: null, actedAt: null, isMyTurn: false },
];

// 휴가 신청 미리보기 steps(API 8장) — 생략 단계는 취소선
const PREVIEW = [
  { stepOrder: 1, approverId: 8, approverName: '임태현', skipped: true },
  { stepOrder: 2, approverId: 6, approverName: '강하늘', skipped: false },
];

export default function ComponentsPage() {
  const [form] = Form.useForm();
  const values = Form.useWatch([], form);

  return (
    <>
      <Typography.Title level={3}>공통 컴포넌트 견본</Typography.Title>
      <Typography.Paragraph type="secondary">개발 서버에서만 보이는 화면입니다.</Typography.Paragraph>

      <Typography.Title level={4}>ApprovalSteps — 승인 진행 표시</Typography.Title>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={8}><Card title="진행 중 (2단계가 내 차례)"><ApprovalSteps steps={IN_PROGRESS} /></Card></Col>
        <Col xs={24} md={12} xl={8}><Card title="연장근무 — 단계별 인정 시간"><ApprovalSteps steps={OVERTIME} showMinutes /></Card></Col>
        <Col xs={24} md={12} xl={8}><Card title="1단계 반려"><ApprovalSteps steps={REJECTED} /></Card></Col>
        <Col xs={24} md={12} xl={8}><Card title="휴가 취소 요청 (round 2)"><ApprovalSteps steps={WITH_CANCEL} /></Card></Col>
        <Col xs={24} md={12} xl={8}><Card title="휴가 신청 미리보기 (1단계 생략)"><ApprovalSteps steps={PREVIEW} /></Card></Col>
        <Col xs={24} md={12} xl={8}><Card title="즉시 승인 (단계 없음)"><ApprovalSteps steps={[]} /></Card></Col>
      </Row>

      <Typography.Title level={4} className="mt-24">FileUpload — 파일 올리기 (실제 서버에 올라갑니다)</Typography.Title>
      <Card>
        <Form form={form} layout="vertical">
          <Form.Item name="receiptFileId" label="영수증 (RECEIPT — jpg · png · pdf, 10MB)">
            <FileUpload purpose="RECEIPT" />
          </Form.Item>
          <Form.Item name="profileFileId" label="프로필 사진 (PROFILE — jpg · png)">
            <FileUpload purpose="PROFILE" buttonText="사진 선택" />
          </Form.Item>
        </Form>
        <Typography.Text type="secondary">폼 값: {JSON.stringify(values ?? {})}</Typography.Text>
      </Card>
    </>
  );
}
