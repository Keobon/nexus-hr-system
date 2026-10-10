import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Form, Input, Select, Typography } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { login, meKey } from '../../api/auth';
import { setToken } from '../../auth/token';
import { nextPath } from '../../auth/nextPath';
import { errorMessage } from '../../constants/errors';

// 개발 서버에서만 보이는 데모 계정(seed_demo.sql). 비밀번호는 모두 Passw0rd!
const DEMO_ACCOUNTS = [
  { value: 'taehyun.lim@nexuslabs.example', label: '임태현 — 신청자(직원)' },
  { value: 'seoyeon.yoon@nexuslabs.example', label: '윤서연 — 1단계 승인(팀장)' },
  { value: 'haneul.kang@nexuslabs.example', label: '강하늘 — 2단계 승인(본부장)' },
  { value: 'sua.jung@nexuslabs.example', label: '정수아 — 인사 담당' },
  { value: 'admin@nexuslabs.example', label: '이지원 — 최고 관리자' },
];

/** 로그인(F-AUTH-01, 프론트 가이드 3.1) */
export default function LoginPage() {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [error, setError] = useState(location.state?.reason ?? null);
  const [loading, setLoading] = useState(false);

  const onFinish = async ({ email, password }) => {
    setLoading(true);
    setError(null);
    try {
      const { token, next } = await login(email, password);
      setToken(token);
      queryClient.removeQueries({ queryKey: meKey });
      navigate(nextPath(next), { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-center">
      <Card className="auth-card">
        <Typography.Title level={3}>로그인</Typography.Title>
        {error && <Alert type="error" message={error} showIcon className="mb-16" />}
        {import.meta.env.DEV && (
          <Select
            className="mb-16 full-width"
            placeholder="데모 계정 고르기(개발용)"
            options={DEMO_ACCOUNTS}
            onChange={(email) => form.setFieldsValue({ email, password: 'Passw0rd!' })}
          />
        )}
        <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item name="email" label="이메일" rules={[{ required: true, message: '이메일을 입력하세요' }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="비밀번호" rules={[{ required: true, message: '비밀번호를 입력하세요' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            로그인
          </Button>
        </Form>
        <div className="auth-footer">
          처음이신가요? <Link to="/signup">회사 등록</Link>
        </div>
      </Card>
    </div>
  );
}
