import iconInfo from '../../../assets/figma/icon-info.svg';
import iconInfoSm from '../../../assets/figma/icon-info-sm.svg';

// 화면 공통 조각 — Figma 내 정보 프레임 5개가 같이 쓰는 카드 · 줄 · 배지 · 안내

export const EMPTY = '미등록';

export function Badge({ children, tone }) {
  return <span className={`mi-badge${tone === 'green' ? ' is-green' : ''}`}>{children}</span>;
}

export function InfoRow({ label, children }) {
  return (
    <div className="mi-info-row">
      <span>{label}</span>
      <span>{children ?? EMPTY}</span>
    </div>
  );
}

/** 파란 안내 — variant: card(12px) · page(20px) · gray(테두리) · plain(작은 i 아이콘, 배경 없음) */
export function Note({ children, variant = 'card' }) {
  const cls = { card: 'mi-note', page: 'mi-note is-page', gray: 'mi-note is-page is-gray', plain: 'mi-note is-plain' }[variant];
  return (
    <div className={cls}>
      <img src={variant === 'plain' ? iconInfoSm : iconInfo} alt="" />
      <div>{children}</div>
    </div>
  );
}

const ALLOWED = ['image/jpeg', 'image/png', 'application/pdf'];

// 백엔드에 없는 기능 안내 — 여러 화면이 같이 쓴다
export { usePendingFeature } from '../../../components/common/usePendingFeature';
