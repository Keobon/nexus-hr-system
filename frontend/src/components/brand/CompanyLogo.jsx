// 로그인한 회사의 로고(F-COMP-02 "화면 상단에 회사명·로고") — 사이드바 · 휴대폰 상단 막대(모든 역할 같은 자리)
// 회사가 로고를 올렸으면 그 이미지, 아니면 로그인 화면과 같은 서비스 로고(NEXUS labs) — 회사명 글자는 쓰지 않는다(2026-11-16)
import { useFileUrl } from '../common/useFileUrl';
import BrandLogo from './BrandLogo';

export default function CompanyLogo({ company }) {
  const url = useFileUrl(company?.logoFileId);
  if (url) {
    return (
      <div className="company-logo">
        <img src={url} alt={company.name} />
      </div>
    );
  }
  return <BrandLogo />;
}
