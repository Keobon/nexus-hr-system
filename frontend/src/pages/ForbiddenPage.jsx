import { Link } from 'react-router-dom';
import { Button, Result } from 'antd';

/** 403 — 권한 없는 화면에 주소로 들어왔을 때(메뉴 구조 M-4) */
export default function ForbiddenPage() {
  return (
    <Result
      status="403"
      title="권한이 없습니다"
      extra={
        <Link to="/">
          <Button type="primary">홈으로</Button>
        </Link>
      }
    />
  );
}
