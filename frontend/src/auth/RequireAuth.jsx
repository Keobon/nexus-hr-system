import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Result, Spin } from 'antd';
import { getToken } from './token';
import { useMe } from './useMe';

/**
 * 로그인한 사용자만 들어오는 라우트(프론트 가이드 2.2 시작 흐름).
 * 토큰 없음 → /welcome, 비밀번호 변경 필요 → /password 외에는 막는다(M-5).
 */
export default function RequireAuth() {
  const location = useLocation();
  const { me, isLoading, error } = useMe();

  if (!getToken()) return <Navigate to="/welcome" replace />;
  if (isLoading) {
    return (
      <div className="page-center">
        <Spin size="large" />
      </div>
    );
  }
  if (error || !me) {
    // 401 은 client.js 가 로그인 화면으로 보낸다. 여기는 서버가 안 뜬 경우 등
    return <Result status="warning" title="내 정보를 불러오지 못했습니다" subTitle={error?.message} />;
  }
  if (me.mustChangePassword && location.pathname !== '/password') {
    return <Navigate to="/password" replace />;
  }
  return <Outlet />;
}
