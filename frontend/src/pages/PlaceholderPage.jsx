import { Result } from 'antd';

/** 아직 만들지 않은 화면. 담당 화면 묶음(역할 분담 3장)을 같이 보여 준다 */
export default function PlaceholderPage({ title, owner }) {
  return <Result status="info" title={title} subTitle={owner ? `준비 중 — ${owner}` : '준비 중'} />;
}
