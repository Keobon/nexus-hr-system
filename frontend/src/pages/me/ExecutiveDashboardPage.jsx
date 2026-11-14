// 경영진 대시보드(/executive) — 경영진은 홈이 곧 이 화면이고, 인사 담당 등 회사 대시보드(DASHBOARD_COMPANY) 권한자가 메뉴로 연다.
// 화면은 ExecutiveHome 그대로(조회 전용), 값은 GET /dashboard/home 의 company
import { Result, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { dashboardKey, getDashboardHome } from '../../api/dashboard';
import { errorMessage } from '../../constants/errors';
import ExecutiveHome from './ExecutiveHome';

export default function ExecutiveDashboardPage() {
  const home = useQuery({ queryKey: dashboardKey, queryFn: getDashboardHome });
  if (home.isLoading) return <Spin />;
  if (home.error) return <Result status="warning" title="화면을 불러오지 못했습니다" subTitle={errorMessage(home.error)} />;
  if (!home.data.company) return <Result status="403" title="회사 대시보드를 볼 권한이 없습니다" />;
  return <ExecutiveHome company={home.data.company} />;
}
