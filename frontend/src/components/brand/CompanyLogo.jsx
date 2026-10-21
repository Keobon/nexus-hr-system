// 로그인한 회사의 로고(F-COMP-02 "화면 상단에 회사명·로고") — 사이드바 · 휴대폰 상단 막대
// 로고를 올렸으면 그 이미지, 없으면 회사명 글자. 회사 정보를 모르면(불러오는 중) 서비스 로고
// 로그인 · 회사 등록 화면은 서비스 로고(BrandLogo) 그대로
import { useFileUrl } from '../common/useFileUrl';
import BrandLogo from './BrandLogo';

export default function CompanyLogo({ company }) {
  const url = useFileUrl(company?.logoFileId);
  if (!company?.name) return <BrandLogo />;
  if (url) {
    return (
      <div className="company-logo">
        <img src={url} alt={company.name} />
      </div>
    );
  }
  return (
    <div className="company-logo is-text" title={company.name}>
      <span>{company.name}</span>
    </div>
  );
}
