import { Link } from 'react-router-dom';
import { Button, Space, Typography } from 'antd';

/** 첫 화면(메뉴 구조 4장) — 로그인 안 한 사용자가 들어오는 곳. 디자인 v2 가 나오면 F-02 에서 채운다 */
export default function WelcomePage() {
  return (
    <div className="page-center welcome">
      <Typography.Title>NEXUS HR</Typography.Title>
      <Typography.Paragraph type="secondary">회사마다 다른 인사 규칙을 그대로 담는 인사관리 서비스</Typography.Paragraph>
      <Space>
        <Link to="/signup">
          <Button type="primary" size="large">회사 등록</Button>
        </Link>
        <Link to="/login">
          <Button size="large">로그인</Button>
        </Link>
      </Space>
    </div>
  );
}
