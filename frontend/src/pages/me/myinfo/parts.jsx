import { useRef } from 'react';
import { App } from 'antd';
import { MAX_FILE_BYTES } from '../../../api/files';
import iconInfo from '../../../assets/figma/icon-info.svg';
import iconInfoSm from '../../../assets/figma/icon-info-sm.svg';
import iconPaperclip from '../../../assets/figma/icon-paperclip.svg';
import iconUpload from '../../../assets/figma/icon-upload.svg';

// 화면 공통 조각 — Figma 내 정보 프레임 5개가 같이 쓰는 카드 · 줄 · 배지 · 안내 · 증빙 첨부

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

/**
 * 증빙 서류 고르기(점선 영역). 증빙 승인 기능이 백엔드에 생기기 전이라 서버에 올리지 않고 고른 파일만 들고 있는다.
 * (직원 서류 업로드 purpose 는 EMPLOYEE_MANAGE 전용이라 직원 본인은 지금 올릴 수 없다 — API 14장)
 * variant: inline(개명) · center(통장 사본, 업로드 아이콘) · tall(학력 · 경력 · 자격증, 클립 아이콘)
 */
export function EvidencePicker({ value, onChange, variant = 'inline', title, hint }) {
  const inputRef = useRef(null);
  const { message } = App.useApp();

  const pick = (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!ALLOWED.includes(file.type)) {
      message.error('jpg, png, pdf만 올릴 수 있습니다');
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      message.error('10MB 이하 파일만 올릴 수 있습니다');
      return;
    }
    onChange(file);
  };

  const cls = { inline: 'mi-drop', center: 'mi-drop is-gray is-center', tall: 'mi-drop is-gray is-tall' }[variant];

  return (
    <>
      <input ref={inputRef} type="file" accept=".jpg,.jpeg,.png,.pdf" hidden onChange={pick} />
      <button type="button" className={cls} onClick={() => inputRef.current?.click()}>
        {variant === 'inline' && <span>{`증빙 서류 첨부  ·  ${value ? value.name : '첨부된 서류 없음'}`}</span>}
        {variant === 'center' && (
          <>
            <img src={iconUpload} alt="" />
            <span>{value ? value.name : title}</span>
          </>
        )}
        {variant === 'tall' && (
          <>
            <img src={iconPaperclip} alt="" />
            <span>
              <strong>{value ? value.name : title}</strong>
              <small>{hint}</small>
            </span>
          </>
        )}
      </button>
    </>
  );
}

// 백엔드에 없는 기능 안내 — 여러 화면이 같이 쓴다
export { usePendingFeature } from '../../../components/common/usePendingFeature';
