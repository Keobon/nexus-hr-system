// 회사 설정 › 회사 정보(F-COMP-02 · 07) — Figma Admin_cpinfo(330:6850) · Admin_cpinfo_sec(326:6668) · Admin_cpdoc(326:6967)
// 탭: 기본 정보 · 변경 이력 · 회사 서류. 탭은 주소(?tab=)에 둔다
import { PageHead } from '../../../hr/parts';
import { AdTabs, useTab } from '../parts';
import BasicTab from './BasicTab';
import HistoryTab from './HistoryTab';
import DocsTab from './DocsTab';

const TABS = [
  { value: 'basic', label: '기본 정보' },
  { value: 'history', label: '변경 이력' },
  { value: 'docs', label: '회사 서류' },
];

export default function CompanyPage() {
  const [tab, setTab] = useTab(TABS.map((t) => t.value));
  return (
    <div className="hr">
      <PageHead title="회사 정보" desc="회사 기본 정보와 서류를 관리합니다. 회사명 · 대표자명 · 사업자등록번호 · 주소를 바꾸면 변경 이력에 남습니다." />
      <AdTabs tabs={TABS} value={tab} onChange={setTab} />
      {tab === 'basic' && <BasicTab />}
      {tab === 'history' && <HistoryTab />}
      {tab === 'docs' && <DocsTab />}
    </div>
  );
}
