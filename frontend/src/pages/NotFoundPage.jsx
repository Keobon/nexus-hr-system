import { Link } from 'react-router-dom';
import { Button, Result } from 'antd';

/** 404 — 없는 주소, 다른 회사 데이터(BR-TEN-001) */
export default function NotFoundPage() {
  return (
    <Result
      status="404"
      title="찾을 수 없습니다"
      extra={
        <Link to="/">
          <Button type="primary">홈으로</Button>
        </Link>
      }
    />
  );
}
