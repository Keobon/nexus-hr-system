// 퇴사 통계 그래프 — 차트 라이브러리가 없어 경영 총괄 대시보드(ExecutiveHome)처럼 SVG 로 그린다. viewBox 고정 + 가로 100%
const W = 720;
const PAD = { top: 16, right: 44, bottom: 34, left: 40 };
export const COLORS = { hires: '#7dd3fc', resignations: '#fa9fb0', rate: '#f59e0b', bar: '#a5a1f5' };

/** 눈금 4칸이 깔끔하게 떨어지는 최댓값 — 한 칸 = 1 · 2 · 2.5 · 5 × 10^k(int 면 1 이상 정수) */
const axisMax = (v, int = false) => {
  const raw = Math.max(v, int ? 4 : 1) / 4;
  const p = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * p).find((s) => s >= raw && (!int || Number.isInteger(s)));
  return step * 4;
};
const tick = (v) => Math.round(v * 10) / 10;

/** '2026-03' → '26.03' · '2026-Q1' → '26 Q1' · '2026' → '2026' */
export function periodLabel(p) {
  if (/^\d{4}-\d{2}$/.test(p)) return `${p.slice(2, 4)}.${p.slice(5)}`;
  if (/^\d{4}-Q\d$/.test(p)) return `${p.slice(2, 4)} ${p.slice(5)}`;
  return p;
}

/**
 * 퇴사율 추이(선, 오른쪽 축 %) + 입사자 · 퇴사자(막대, 왼쪽 축 명). bars=false 면 선만(대시보드 12개월 추이)
 * items: [{ period, hires, resignations, turnoverRate }]
 */
export function TrendChart({ items, bars = true, height = 260, width = W }) {
  const H = height;
  const W = width; // 가로로 넓은 자리는 width 를 키워 글자가 커지지 않게
  const iw = W - PAD.left - PAD.right;
  const ih = H - PAD.top - PAD.bottom;
  const n = items.length;
  const step = iw / Math.max(1, n);
  const maxCount = axisMax(Math.max(0, ...items.flatMap((d) => [d.hires ?? 0, d.resignations ?? 0])), true);
  const maxRate = axisMax(Math.max(0, ...items.map((d) => Number(d.turnoverRate) || 0)));
  const x = (i) => PAD.left + step * i + step / 2;
  const yCount = (v) => PAD.top + ih - (v / maxCount) * ih;
  const yRate = (v) => PAD.top + ih - (v / maxRate) * ih;
  const bw = Math.min(18, step / 3);
  const line = items.map((d, i) => `${i ? 'L' : 'M'}${x(i)},${yRate(Number(d.turnoverRate) || 0)}`).join(' ');
  const every = Math.ceil(n / 12); // 칸이 많으면 라벨을 건너뛴다

  return (
    <svg className="to-chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={bars ? '퇴사율 추이와 입사자 · 퇴사자' : '퇴사율 추이'}>
      {[0, 0.25, 0.5, 0.75, 1].map((t) => {
        const y = PAD.top + ih - t * ih;
        return (
          <g key={t}>
            <line x1={PAD.left} x2={W - PAD.right} y1={y} y2={y} stroke="#eef2f7" />
            {bars && (
              <text x={PAD.left - 6} y={y + 4} textAnchor="end" className="to-axis">
                {tick(maxCount * t)}
              </text>
            )}
            <text x={W - PAD.right + 6} y={y + 4} className="to-axis">{`${tick(maxRate * t)}%`}</text>
          </g>
        );
      })}
      {items.map((d, i) => (
        <g key={d.period}>
          {bars && (
            <>
              <rect x={x(i) - bw - 1} y={yCount(d.hires ?? 0)} width={bw} height={PAD.top + ih - yCount(d.hires ?? 0)} rx="2" fill={COLORS.hires}>
                <title>{`${d.period} 입사 ${d.hires ?? 0}명`}</title>
              </rect>
              <rect x={x(i) + 1} y={yCount(d.resignations ?? 0)} width={bw} height={PAD.top + ih - yCount(d.resignations ?? 0)} rx="2" fill={COLORS.resignations}>
                <title>{`${d.period} 퇴사 ${d.resignations ?? 0}명`}</title>
              </rect>
            </>
          )}
          {i % every === 0 && (
            <text x={x(i)} y={H - 12} textAnchor="middle" className="to-axis">
              {periodLabel(d.period)}
            </text>
          )}
        </g>
      ))}
      {n > 0 && <path d={line} fill="none" stroke={COLORS.rate} strokeWidth="2.5" strokeLinejoin="round" />}
      {items.map((d, i) => (
        <circle key={d.period} cx={x(i)} cy={yRate(Number(d.turnoverRate) || 0)} r="3.5" fill="#fff" stroke={COLORS.rate} strokeWidth="2">
          <title>{`${d.period} 퇴사율 ${d.turnoverRate ?? 0}%`}</title>
        </circle>
      ))}
    </svg>
  );
}

export function TrendLegend({ bars = true }) {
  return (
    <div className="to-legend">
      {bars && (
        <>
          <span><i style={{ background: COLORS.hires }} />입사자</span>
          <span><i style={{ background: COLORS.resignations }} />퇴사자</span>
        </>
      )}
      <span><i className="is-line" style={{ background: COLORS.rate }} />퇴사율(%)</span>
    </div>
  );
}

/** 세로 막대 — items: [{ key, label, value }] */
export function BarChart({ items, unit = '명', height = 220 }) {
  const H = height;
  const W = 560;
  const iw = W - PAD.left - PAD.right;
  const ih = H - PAD.top - PAD.bottom;
  const step = iw / Math.max(1, items.length);
  const max = axisMax(Math.max(0, ...items.map((d) => d.value)), true);
  const bw = Math.min(64, step / 2);
  return (
    <svg className="to-chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="근속기간별 퇴사자">
      {[0, 0.5, 1].map((t) => {
        const y = PAD.top + ih - t * ih;
        return (
          <g key={t}>
            <line x1={PAD.left} x2={W - PAD.right} y1={y} y2={y} stroke="#eef2f7" />
            <text x={PAD.left - 6} y={y + 4} textAnchor="end" className="to-axis">{tick(max * t)}</text>
          </g>
        );
      })}
      {items.map((d, i) => {
        const cx = PAD.left + step * i + step / 2;
        const h = (d.value / max) * ih;
        return (
          <g key={d.key}>
            <rect x={cx - bw / 2} y={PAD.top + ih - h} width={bw} height={h} rx="3" fill={COLORS.bar}>
              <title>{`${d.label} ${d.value}${unit}`}</title>
            </rect>
            <text x={cx} y={PAD.top + ih - h - 6} textAnchor="middle" className="to-value">{`${d.value}${unit}`}</text>
            <text x={cx} y={H - 12} textAnchor="middle" className="to-axis">{d.label}</text>
          </g>
        );
      })}
    </svg>
  );
}

/** 가로 막대(비율) — items: [{ key, label, count, ratio }] */
export function RatioBars({ items }) {
  const max = Math.max(1, ...items.map((d) => Number(d.ratio) || 0));
  return (
    <ul className="to-hbars">
      {items.map((d) => (
        <li key={d.key}>
          <span>{d.label}</span>
          <div className="to-hbar">
            <i style={{ width: `${((Number(d.ratio) || 0) / max) * 100}%` }} />
          </div>
          <b>{`${d.count}명 · ${Number(d.ratio ?? 0).toFixed(1)}%`}</b>
        </li>
      ))}
    </ul>
  );
}
