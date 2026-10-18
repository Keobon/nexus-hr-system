// 회사 설정 › 급여 항목·변수(F-PAY-01 · 02) — Figma HR 급여 항목·변수(451:8943 · 453:8899 · 71:5113 · 452:9254 · 71:5717 외)
// 탭 4개: 수당·공제 항목 · 4대보험 · 소득세 · 계산 변수. 탭은 주소(?tab=)에 둔다
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PageHead } from '../../hr/parts';
import ItemsTab from './ItemsTab';
import InsuranceTab from './InsuranceTab';
import TaxTab from './TaxTab';
import VariablesTab from './VariablesTab';
import './pay.css';

const TABS = [
  ['items', '수당·공제 항목'],
  ['insurance', '4대보험'],
  ['tax', '소득세'],
  ['variables', '계산 변수'],
];

export default function PaySettingsPage() {
  const [sp, setSp] = useSearchParams();
  const tab = TABS.some(([k]) => k === sp.get('tab')) ? sp.get('tab') : 'items';
  const go = (k) => setSp(k === 'items' ? {} : { tab: k });
  // 항목 추가 · 복사 창 — 버튼은 머리에, 창은 항목 탭 안
  const [itemModal, setItemModal] = useState(null); // { mode: 'create' | 'copy' | 'edit', item? }

  return (
    <div className="hr pay">
      <PageHead title="급여 항목·변수" desc="급여 계산에 쓰는 수당·공제 항목과 계산 기준을 관리합니다.">
        {tab === 'items' && (
          <>
            <button type="button" className="hr-btn is-secondary" onClick={() => setItemModal({ mode: 'copy' })}>
              항목 복사
            </button>
            <button type="button" className="hr-btn" onClick={() => setItemModal({ mode: 'create' })}>
              항목 추가
            </button>
          </>
        )}
      </PageHead>

      <nav className="pay-tabs" role="tablist" aria-label="급여 설정 탭">
        {TABS.map(([k, l]) => (
          <button key={k} type="button" role="tab" aria-selected={tab === k} className={tab === k ? 'is-on' : undefined} onClick={() => go(k)}>
            {l}
          </button>
        ))}
      </nav>

      {tab === 'items' && <ItemsTab modal={itemModal} setModal={setItemModal} onTab={go} />}
      {tab === 'insurance' && <InsuranceTab />}
      {tab === 'tax' && <TaxTab />}
      {tab === 'variables' && <VariablesTab />}
    </div>
  );
}
