// 인사 관리 › 휴가 현황(F-LEAVE-02 · 05 · 06) — Figma 70-3465 · 487-17240(휴가 내역 · 상세) · 487-16827(잔여 현황) · 487-17038(휴가 부여)
// 휴가 부여 탭은 LEAVE_MANAGE 만. 표 · 조회 조건 · 알약은 근태 현황과 같은 부품(attendance/shared)
import { useSearchParams } from 'react-router-dom';
import { useMe } from '../../../auth/useMe';
import { PageHead } from '../parts';
import RequestsTab from './RequestsTab';
import BalancesTab from './BalancesTab';
import GrantsTab from './GrantsTab';
import '../attendance/at.css';
import './lv-admin.css';

const TABS = [
  ['requests', '휴가 내역', '신청된 휴가의 상태와 처리 단계를 한눈에 확인하고, 승인 대기 내역을 빠르게 관리합니다.', RequestsTab],
  ['balances', '잔여 현황', '직원별 휴가 부여·사용 내역과 남은 일수를 확인합니다.', BalancesTab],
  ['grants', '휴가 부여', '연도별 휴가를 부여하고, 직원별 일수를 조정합니다.', GrantsTab],
];

export default function LeaveAdminPage() {
  const { can } = useMe();
  const [sp, setSp] = useSearchParams();
  const tabs = TABS.filter(([k]) => k !== 'grants' || can('LEAVE_MANAGE'));
  const [key, , desc, Tab] = tabs.find(([k]) => k === sp.get('tab')) ?? tabs[0];

  return (
    <div className="hr at lv-admin">
      <PageHead title="휴가 현황" desc={desc} />
      <nav className="at-tabs" role="tablist" aria-label="휴가 현황 탭">
        {tabs.map(([k, label]) => (
          <button key={k} type="button" role="tab" aria-selected={key === k} className={key === k ? 'is-on' : undefined} onClick={() => setSp(k === 'requests' ? {} : { tab: k })}>
            {label}
          </button>
        ))}
      </nav>
      <Tab key={key} />
    </div>
  );
}
