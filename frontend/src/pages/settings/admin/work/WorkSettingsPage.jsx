// 회사 설정 › 근무시간·휴일(F-COMP-04 · 05) — Figma Admin_Atwork(221:5487) · Admin_Atwork_pop-up(236:5914) · Admin_Dayoff(236:5718)
// 근무시간은 이력형(수정 없음, 새 적용 시작일로 한 줄 더), 휴일은 연도별 목록형
// 근무 정정 탭(F-ATT-04)은 ATTENDANCE_MANAGE 가 있을 때만 — 직원을 눌러 날짜별 출퇴근 기록을 정정 · 입력
import { useMe } from '../../../../auth/useMe';
import { PageHead } from '../../../hr/parts';
import { AdTabs, useTab } from '../parts';
import ScheduleTab from './ScheduleTab';
import HolidayTab from './HolidayTab';
import CorrectionTab from './CorrectionTab';

const BASE_TABS = [
  { value: 'schedule', label: '근무시간' },
  { value: 'holidays', label: '휴일' },
];

/** embedded — 마법사 안에서는 탭 없이 근무시간 · 휴일을 이어서 보여 준다 */
export default function WorkSettingsPage({ embedded = false }) {
  const { me, can } = useMe();
  const TABS = can('ATTENDANCE_MANAGE') ? [...BASE_TABS, { value: 'corrections', label: '근무 정정', count: me?.todos?.attendanceCorrections }] : BASE_TABS;
  const [tab, setTab] = useTab(TABS.map((t) => t.value));
  if (embedded) {
    return (
      <div className="hr">
        <ScheduleTab />
        <HolidayTab />
      </div>
    );
  }
  return (
    <div className="hr">
      <PageHead title="근무시간 · 휴일" desc="출퇴근 시각 · 휴게 · 근무 요일과 회사 휴일을 정합니다. 근태 · 휴가 일수 계산에 쓰입니다." />
      <AdTabs tabs={TABS} value={tab} onChange={setTab} />
      {tab === 'schedule' && <ScheduleTab />}
      {tab === 'holidays' && <HolidayTab />}
      {tab === 'corrections' && <CorrectionTab />}
    </div>
  );
}
