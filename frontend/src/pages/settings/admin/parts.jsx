// 최고 관리자 화면들이 같이 쓰는 작은 조각 — 밑줄 탭(Figma Admin_cpinfo 탭 줄) · 탭 주소(?tab=) · 날짜 문자열
import { useSearchParams } from 'react-router-dom';
import dayjs from 'dayjs';
import './admin.css';

/** 밑줄 탭 — tabs: [{ value, label, count? }] */
export function AdTabs({ tabs, value, onChange }) {
  return (
    <div className="ad-tabs" role="tablist">
      {tabs.map((t) => (
        <button key={t.value} type="button" role="tab" aria-selected={value === t.value}
          className={value === t.value ? 'is-on' : ''} onClick={() => onChange(t.value)}>
          {t.label}
          {t.count ? <span className="ad-count">{t.count}</span> : null}
        </button>
      ))}
    </div>
  );
}

/** 탭을 주소(?tab=)에 둔다 — 첫 탭은 주소에서 뺀다. 마법사 안에서는 fixed 로 고정 */
export function useTab(values, fixed) {
  const [sp, setSp] = useSearchParams();
  const fromUrl = values.includes(sp.get('tab')) ? sp.get('tab') : values[0];
  const tab = fixed ?? fromUrl;
  const setTab = (v) => {
    const next = new URLSearchParams(sp);
    if (v === values[0]) next.delete('tab');
    else next.set('tab', v);
    setSp(next, { replace: true });
  };
  return [tab, setTab];
}

export const today = () => dayjs().format('YYYY-MM-DD');
/** DatePicker 값(dayjs) → 'YYYY-MM-DD' | null */
export const ymd = (d) => (d ? dayjs(d).format('YYYY-MM-DD') : null);
/** 'YYYY-MM-DD' | null → DatePicker 값 */
export const toDay = (s) => (s ? dayjs(s) : null);
