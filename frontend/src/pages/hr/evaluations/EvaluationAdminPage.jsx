// 인사 관리 › 평가 관리(F-EVAL-01 · 03 · 05 · 06) — Figma 72-7227(평가 기간) · 473-11844(기간 만들기) · 72-7648 · 473-13124(평가 결과)
// "기준표 · 질문" 탭은 이번 범위에서 뺐다(평가 템플릿은 회사 설정 › 평가 템플릿). 평가 기간 탭은 EVAL_MANAGE 만
import { useSearchParams } from 'react-router-dom';
import { useMe } from '../../../auth/useMe';
import { PageHead } from '../parts';
import CycleTab from './CycleTab';
import ResultTab from './ResultTab';
import EvalReadView from './EvalReadView';
import './ev-admin.css';

export default function EvaluationAdminPage() {
  const { can } = useMe();
  const manage = can('EVAL_MANAGE');
  const [sp, setSp] = useSearchParams();
  const tabs = [...(manage ? [['cycles', '평가 기간']] : []), ['results', '평가 결과']];
  const tab = tabs.some(([k]) => k === sp.get('tab')) ? sp.get('tab') : tabs[0][0];
  const go = (k, extra = {}) => setSp({ tab: k, ...extra });
  // 조회만 하는 사람(경영진 · 조직장) — Figma 72-7789 평가 결과 조회 전용
  if (!manage) return <EvalReadView />;

  return (
    <div className="hr ea">
      <PageHead title="평가 관리" desc="평가 기간을 관리하고 결과를 확정합니다." />
      <nav className="ea-tabs" role="tablist" aria-label="평가 관리 탭">
        {tabs.map(([k, l]) => (
          <button key={k} type="button" role="tab" aria-selected={tab === k} className={tab === k ? 'is-on' : undefined} onClick={() => go(k)}>
            {l}
          </button>
        ))}
      </nav>
      {tab === 'cycles' && <CycleTab onResults={(cycleId) => go('results', { cycle: String(cycleId) })} />}
      {tab === 'results' && <ResultTab manage={manage} initialCycle={sp.get('cycle')} />}
    </div>
  );
}
