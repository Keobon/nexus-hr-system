// 경영진 대시보드 — 인력 변동(퇴사) 현황(작업 지시서 1-4). 조회 전용, 카드 · 표 · 그래프를 누르면 퇴사 통계 상세(/statistics/turnover)
// 요약 카드 4개(이번 달), 최근 12개월 퇴사율 추이, 부서별 인원 · 퇴사율(최근 12개월)
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { getTurnoverByDept, getTurnoverSummary, getTurnoverTrend, turnoverByDeptKey, turnoverSummaryKey, turnoverTrendKey } from '../../../api/turnover';
import { TrendChart, TrendLegend } from './charts';
import './turnover.css';

const rate = (v) => (v == null ? '–' : Number(v).toFixed(1));

export default function TurnoverOverview() {
  const navigate = useNavigate();
  const month = dayjs().format('YYYY-MM');
  const from = dayjs().subtract(11, 'month').format('YYYY-MM');
  const range = { from, to: month };
  const summary = useQuery({ queryKey: turnoverSummaryKey(month), queryFn: () => getTurnoverSummary(month), retry: false });
  const trend = useQuery({ queryKey: turnoverTrendKey({ unit: 'month', ...range }), queryFn: () => getTurnoverTrend({ unit: 'month', ...range }), retry: false });
  const depts = useQuery({ queryKey: turnoverByDeptKey(range), queryFn: () => getTurnoverByDept(range), retry: false });
  const go = () => navigate(`/statistics/turnover?unit=month&from=${from}&to=${month}`);

  const s = summary.data;
  const diff = s && s.prevTurnoverRate != null ? Math.round((Number(s.turnoverRate) - Number(s.prevTurnoverRate)) * 10) / 10 : null;
  const tiles = [
    { label: '전체 재직 인원', value: s?.headcount, unit: '명', sub: s ? `순증감 ${s.netChange > 0 ? '+' : ''}${s.netChange}명` : '', tone: 'is-green' },
    { label: '이번 달 입사자', value: s?.hires, unit: '명', sub: `${dayjs().month() + 1}월 입사`, tone: 'is-blue' },
    { label: '이번 달 퇴사자', value: s?.resignations, unit: '명', sub: `${dayjs().month() + 1}월 퇴사`, tone: 'is-orange' },
    {
      label: '이번 달 퇴사율',
      value: s ? rate(s.turnoverRate) : undefined,
      unit: '%',
      sub: diff == null ? '전월 자료 없음' : `전월 대비 ${diff > 0 ? '▲' : diff < 0 ? '▼' : ''} ${Math.abs(diff).toFixed(1)}%p`,
      tone: diff > 0 ? 'is-up' : diff < 0 ? 'is-down' : 'is-gray',
    },
  ];
  const items = Array.isArray(trend.data) ? trend.data : [];
  const rows = [...(Array.isArray(depts.data) ? depts.data : [])].sort((a, b) => Number(b.turnoverRate) - Number(a.turnoverRate));

  return (
    <>
      <h2 className="ex-sec">인력 변동 현황</h2>
      <div className="ex-tiles">
        {tiles.map((x) => (
          <div key={x.label} className="ex-tile is-click" onClick={go} role="link" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && go()}>
            <span>{x.label}</span>
            <strong>
              {summary.isLoading ? '…' : (x.value ?? '–')}
              <small>{x.unit}</small>
            </strong>
            <em className={`to-sub ${x.tone}`}>{summary.error ? '데이터 없음' : x.sub}</em>
          </div>
        ))}
      </div>

      <div className="ex-charts">
        <section className="ex-card to-click" onClick={go}>
          <div className="ex-card-head">
            <h3>최근 12개월 퇴사율 추이</h3>
            <span className="ex-chip">{`${from.replace('-', '.')} ~ ${month.replace('-', '.')}`}</span>
          </div>
          <div className="to-pad">
            {trend.isLoading ? (
              <p className="ex-empty">불러오는 중…</p>
            ) : items.length === 0 ? (
              <p className="ex-empty">데이터 없음</p>
            ) : (
              <>
                <TrendChart items={items} bars={false} height={220} />
                <TrendLegend bars={false} />
              </>
            )}
          </div>
        </section>

        <section className="ex-card to-click" onClick={go}>
          <div className="ex-card-head">
            <h3>부서별 인원 및 퇴사율</h3>
            <span className="ex-chip">최근 12개월</span>
          </div>
          <table className="ex-table to-table">
            <thead>
              <tr>
                <th>부서</th>
                <th>평균 재직 인원</th>
                <th>퇴사자</th>
                <th>퇴사율</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={4} className="ex-empty">{depts.isLoading ? '불러오는 중…' : '데이터 없음'}</td>
                </tr>
              ) : (
                rows.map((r) => (
                  <tr key={r.orgUnitId}>
                    <td>{r.orgUnitName}</td>
                    <td>{`${Number(r.avgHeadcount).toLocaleString('ko-KR', { maximumFractionDigits: 1 })}명`}</td>
                    <td>{`${r.resignations}명`}</td>
                    <td><strong>{`${rate(r.turnoverRate)}%`}</strong></td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </section>
      </div>
    </>
  );
}
