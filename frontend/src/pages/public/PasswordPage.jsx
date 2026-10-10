import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { changePassword, meKey } from '../../api/auth';
import { useMe } from '../../auth/useMe';
import { applyFieldErrors } from '../../components/common/formErrors';
import { useNotify } from '../../components/common/useNotify';
import { errorMessage } from '../../constants/errors';

// 8자 이상, 영문 · 숫자 포함(F-AUTH-03) — 서버 PasswordRule 과 같은 조건
const RULES = [
  { test: (v) => v.length >= 8, text: '8자 이상' },
  { test: (v) => /[A-Za-z]/.test(v), text: '영문 포함' },
  { test: (v) => /\d/.test(v), text: '숫자 포함' },
];

/** 비밀번호 변경(F-AUTH-03). 임시 비밀번호 상태면 사이드바 없이 이 화면만 쓴다(M-5) */
export default function PasswordPage() {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const notify = useNotify();
  const { me } = useMe();
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const newPassword = Form.useWatch('newPassword', form) ?? '';

  const onFinish = async ({ currentPassword, newPassword: next }) => {
    setLoading(true);
    setError(null);
    try {
      await changePassword(currentPassword, next);
      await queryClient.invalidateQueries({ queryKey: meKey });
      notify.success('비밀번호를 바꿨습니다');
      navigate('/', { replace: true });
    } catch (err) {
      if (!applyFieldErrors(form, err)) setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-center">
      <Card className="auth-card">
        <Typography.Title level={3}>비밀번호 변경</Typography.Title>
        {me?.mustChangePassword && (
          <Alert type="info" showIcon className="mb-16" message="임시 비밀번호로 로그인했습니다. 새 비밀번호로 바꿔야 다른 메뉴를 쓸 수 있습니다." />
        )}
        {error && <Alert type="error" message={error} showIcon className="mb-16" />}
        <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item name="currentPassword" label="현재 비밀번호" rules={[{ required: true, message: '현재 비밀번호를 입력하세요' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="새 비밀번호"
            rules={[
              { required: true, message: '새 비밀번호를 입력하세요' },
              { validator: (_, v) => (!v || RULES.every((r) => r.test(v)) ? Promise.resolve() : Promise.reject(new Error('조건을 확인하세요'))) },
            ]}
            extra={
              <div className="password-rules">
                {RULES.map((r) => (
                  <span key={r.text} className={r.test(newPassword) ? 'ok' : ''}>
                    {r.test(newPassword) ? '✓' : '·'} {r.text}
                  </span>
                ))}
              </div>
            }
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirm"
            label="새 비밀번호 확인"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '한 번 더 입력하세요' },
              ({ getFieldValue }) => ({
                validator: (_, v) =>
                  !v || v === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error('새 비밀번호와 다릅니다')),
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            변경
          </Button>
          {!me?.mustChangePassword && (
            <Button type="link" block onClick={() => navigate(-1)} className="mt-8">
              돌아가기
            </Button>
          )}
        </Form>
      </Card>
    </div>
  );
}
