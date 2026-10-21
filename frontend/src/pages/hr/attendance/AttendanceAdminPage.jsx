// 인사 관리 › 근태 현황(F-ATT-03 · 04 · F-OT · F-TRIP 조회) — Figma 484-26574(오늘 근태) · 484-26008(근태 조회) · 484-26894(연장근무)
// · 484-27187(출장) · 484-27503(출장 경비) · 482-11916 · 484-27817 · 484-28109(정정 대상)
// 정정 대상 탭은 ATTENDANCE_MANAGE 만. 탭은 주소(?tab=)에 둔다
import { useSearchParams } from 'react-router-dom';
import { useMe } from '../../../auth/useMe';
import { PageHead } from '../parts';
import TodayTab from './TodayTab';
import MonthTab from './MonthTab';
import OvertimeTab from './OvertimeTab';
import TripTab from './TripTab';
import ExpenseTab from './ExpenseTab';
import CorrectionsTab from './CorrectionsTab';
import './at.css';

const TABS = [
  ['today', '오늘 근태', '오늘의 출근 현황과 직원별 근무 상태를 확인합니다.', TodayTab],
  ['month', '근태 조회', '직원별 월간 근태 집계와 일별 근무 기록을 확인합니다.', MonthTab],
  ['overtime', '연장근무', '연장근무 신청과 실제 인정 시간을 확인합니다.', OvertimeTab],
  ['trips', '출장', '국내·해외 출장 신청과 결과 보고 현황을 확인합니다.', TripTab],
  ['expenses', '출장 경비', '출장별 경비와 정산 반영 월을 확인합니다.', ExpenseTab],
  ['corrections', '정정 대상', '미기록 내역을 확인하고, 필요한 근태 정보를 직접 정정합니다.', CorrectionsTab],
];

export default function AttendanceAdminPage() {
  const { me, can } = useMe();
  const [sp, setSp] = useSearchParams();
  const tabs = TABS.filter(([k]) => k !== 'corrections' || can('ATTENDANCE_MANAGE'));
  const current = tabs.find(([k]) => k === sp.get('tab')) ?? tabs[0];
  const [key, , desc, Tab] = current;
  const pending = me?.todos?.attendanceCorrections;

  return (
    <div className="hr at">
      <PageHead title="근태 현황" desc={desc} />
      <nav className="at-tabs" role="tablist" aria-label="근태 현황 탭">
        {tabs.map(([k, label]) => (
          <button key={k} type="button" role="tab" aria-selected={key === k} className={key === k ? 'is-on' : undefined} onClick={() => setSp(k === 'today' ? {} : { tab: k })}>
            {label}
            {k === 'corrections' && pending ? <em>{pending}</em> : null}
          </button>
        ))}
      </nav>
      <Tab key={key} />
    </div>
  );
}
