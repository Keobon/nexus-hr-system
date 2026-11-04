// 인사 관리 › 급여(F-PAY-03 ~ 07) — Figma 급여(456-15895 외 20장). 탭: 월 급여 정산 · 직원 급여 · 급여명세서 · 인건비 현황
// 탭 · 귀속 월은 주소(?tab=&month=)에 둔다. PAYROLL_MANAGE 가 없으면(경영진 조회) 부서 예산 대비 지출 조회 + 급여 · 평가 비교
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { useMe } from '../../../auth/useMe';
import { PageHead } from '../parts';
import RunTab from './RunTab';
import SalaryTab from './SalaryTab';
import PayslipsTab from './PayslipsTab';
import LaborCostTab from './LaborCostTab';
import BudgetReadView from './BudgetReadView';
import CompareTab from './CompareTab';
import './pr.css';

const BASE_TABS = [
  ['run', '월 급여 정산'],
  ['salary', '직원 급여'],
  ['slips', '급여명세서'],
  ['cost', '인건비 현황'],
];
const COMPARE_TAB = ['compare', '급여 · 평가 비교'];

export default function PayrollPage() {
  const { can } = useMe();
  const manage = can('PAYROLL_MANAGE');
  // 비교는 평가 결과까지 잇는다 — 평가 조회 권한이 있을 때만
  const compare = can('EVAL_READ');
  const TABS = [...BASE_TABS, ...(compare ? [COMPARE_TAB] : [])];
  const [sp, setSp] = useSearchParams();
  const tab = TABS.some(([k]) => k === sp.get('tab')) ? sp.get('tab') : 'run';
  const month = /^\d{4}-\d{2}$/.test(sp.get('month') ?? '') ? sp.get('month') : dayjs().format('YYYY-MM');
  const go = (next, extra = {}) => setSp(Object.fromEntries(Object.entries({ tab: next === 'run' ? undefined : next, month: sp.get('month') ?? undefined, ...extra }).filter(([, v]) => v)));
  const setMonth = (m) => go(tab, { month: m });
  const [registering, setRegistering] = useState(false); // 직원 급여 › [급여 등록]
  // 조회만 하는 사람(경영진) — 부서 예산 대비 지출 조회(Figma 71-5459)
  if (!manage) {
    if (!compare) return <BudgetReadView />;
    const readTab = sp.get('tab') === 'compare' ? 'compare' : 'budget';
    return (
      <div className="hr pr">
        <nav className="pr-tabs" role="tablist" aria-label="급여 탭">
          {[['budget', '부서 예산 대비 지출'], COMPARE_TAB].map(([k, l]) => (
            <button key={k} type="button" role="tab" aria-selected={readTab === k} className={readTab === k ? 'is-on' : undefined} onClick={() => setSp(k === 'compare' ? { tab: k } : {})}>
              {l}
            </button>
          ))}
        </nav>
        {readTab === 'compare' ? (
          <>
            <PageHead title="급여 · 평가 비교" desc="전 직원의 급여와 평가를 한 표로 비교합니다. 조회 전용 화면입니다." />
            <CompareTab />
          </>
        ) : (
          <BudgetReadView />
        )}
      </div>
    );
  }

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
      {tab === 'compare' && <CompareTab />}
    </div>
  );
}
