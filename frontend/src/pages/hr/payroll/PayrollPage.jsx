// 인사 관리 › 급여(F-PAY-03 ~ 07) — Figma 급여(456-15895 외 20장). 탭: 월 급여 정산 · 직원 급여 · 급여명세서 · 인건비 현황
// 탭 · 귀속 월은 주소(?tab=&month=)에 둔다. 변경 버튼은 PAYROLL_MANAGE 일 때만(조회만 되는 사람은 읽기 화면)
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { useMe } from '../../../auth/useMe';
import { PageHead } from '../parts';
import RunTab from './RunTab';
import SalaryTab from './SalaryTab';
import PayslipsTab from './PayslipsTab';
import LaborCostTab from './LaborCostTab';
import './pr.css';

const TABS = [
  ['run', '월 급여 정산'],
  ['salary', '직원 급여'],
  ['slips', '급여명세서'],
  ['cost', '인건비 현황'],
];

export default function PayrollPage() {
  const { can } = useMe();
  const manage = can('PAYROLL_MANAGE');
  const [sp, setSp] = useSearchParams();
  const tab = TABS.some(([k]) => k === sp.get('tab')) ? sp.get('tab') : 'run';
  const month = /^\d{4}-\d{2}$/.test(sp.get('month') ?? '') ? sp.get('month') : dayjs().format('YYYY-MM');
  const go = (next, extra = {}) => setSp(Object.fromEntries(Object.entries({ tab: next === 'run' ? undefined : next, month: sp.get('month') ?? undefined, ...extra }).filter(([, v]) => v)));
  const setMonth = (m) => go(tab, { month: m });
  const [registering, setRegistering] = useState(false); // 직원 급여 › [급여 등록]

  return (
    <div className="hr pr">
      <PageHead title="급여" desc="월 급여 정산과 직원별 급여를 관리합니다.">
        {tab === 'salary' && manage && (
          <button type="button" className="hr-btn" onClick={() => setRegistering(true)}>
            급여 등록
          </button>
        )}
      </PageHead>

      <nav className="pr-tabs" role="tablist" aria-label="급여 탭">
        {TABS.map(([k, l]) => (
          <button key={k} type="button" role="tab" aria-selected={tab === k} className={tab === k ? 'is-on' : undefined} onClick={() => go(k)}>
            {l}
          </button>
        ))}
      </nav>

      {tab === 'run' && <RunTab month={month} setMonth={setMonth} manage={manage} onUnregistered={() => go('salary', { only: 'unregistered' })} />}
      {tab === 'salary' && <SalaryTab manage={manage} registering={registering} setRegistering={setRegistering} onlyUnregistered={sp.get('only') === 'unregistered'} />}
      {tab === 'slips' && <PayslipsTab month={month} setMonth={setMonth} onRun={() => go('run')} />}
      {tab === 'cost' && <LaborCostTab month={month} setMonth={setMonth} />}
    </div>
  );
}
