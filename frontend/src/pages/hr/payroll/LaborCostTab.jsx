// 인건비 현황 — Figma 464-15910. 서버는 월 단위만(GET /statistics/labor-cost) → 귀속연월로 조회(분기 · 연 칩은 뺐다)
// 합계 · 전월 대비 · 6개월 추이는 정산 목록(totals), 조직별 인원 · 지급 · 회사 부담은 그달 명세서를 조직 트리로 더한다(상위는 하위 소계)
import { useMemo, useState } from 'react';
import { Select, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { fetchAllPages } from '../../../api/employees';
import { getLaborCost, getPayrollRuns, getPayslips, laborCostKey, payrollRunsKey, payslipsKey } from '../../../api/payrollAdmin';
import { errorMessage } from '../../../constants/errors';
import { useOrgOptions } from '../parts';
import { monthOptions, won, ym } from './shared';

const total = (t) => (t ? t.grossPay + t.companyBurdenTotal : 0);

export default function LaborCostTab({ month, setMonth }) {
  const org = useOrgOptions();
  const [orgUnitId, setOrgUnitId] = useState();
  const runs = useQuery({ queryKey: payrollRunsKey, queryFn: getPayrollRuns });
  const cost = useQuery({ queryKey: laborCostKey(month), queryFn: () => getLaborCost(month) });
  const slips = useQuery({ queryKey: payslipsKey({ payMonth: month, all: true }), queryFn: () => fetchAllPages(getPayslips, { payMonth: month }) });

  const sorted = [...(runs.data ?? [])].sort((a, b) => a.payMonth.localeCompare(b.payMonth));
  const run = sorted.find((r) => r.payMonth === month);
  const prev = [...sorted].reverse().find((r) => r.payMonth < month);
  const diff = run && prev ? total(run.totals) - total(prev.totals) : null;
  const trend = sorted.filter((r) => r.payMonth <= month).slice(-6);
  const maxTrend = Math.max(1, ...trend.map((r) => total(r.totals)));

  // 조직 트리에 명세서를 더한다 — 이름으로 잇고(정산 당시 소속), 상위 조직은 하위까지 합친다
  const tree = useMemo(() => {
    const flat = org.flat.filter((o) => o.depth > 0);
    const byName = Object.fromEntries(flat.map((o) => [o.name, o]));
    const own = {};
    (slips.data ?? []).forEach((s) => {
      const o = byName[s.orgUnitName];
      if (!o) return;
      const a = (own[o.id] ??= { headcount: 0, gross: 0, burden: 0 });
      a.headcount += 1;
      a.gross += s.grossPay;
      a.burden += s.companyBurdenTotal ?? 0;
    });
    const budget = Object.fromEntries((cost.data?.rows ?? []).map((r) => [r.orgUnitId, r]));
    const under = (o) => flat.filter((x) => x.path.length > o.path.length && x.path.slice(0, o.path.length).join('›') === o.path.join('›'));
    return flat.map((o) => {
      const ids = [o, ...under(o)].map((x) => x.id);
      const sum = ids.reduce((a, id) => ({ headcount: a.headcount + (own[id]?.headcount ?? 0), gross: a.gross + (own[id]?.gross ?? 0), burden: a.burden + (own[id]?.burden ?? 0) }), { headcount: 0, gross: 0, burden: 0 });
      return { ...o, ...sum, hasKids: under(o).length > 0, budget: budget[o.id]?.monthlyBudget, rate: budget[o.id]?.executionRate };
    });
  }, [org.flat, slips.data, cost.data]);
  const focus = orgUnitId ? org.byId(orgUnitId) : null;
  const rows = tree.filter((o) => (o.headcount > 0 || o.budget) && (!focus || o.path.slice(0, focus.path.length).join('›') === focus.path.join('›')));
  // 합계는 맨 위 줄(전체면 본부 · 조직을 골랐으면 그 조직)만 더한다 — 하위는 이미 소계에 들어 있다
  const topRows = rows.filter((o) => o.depth === (focus ? focus.depth : 1));
  const sumRows = (k) => topRows.reduce((a, o) => a + (o[k] ?? 0), 0);

  return (
    <>
      <div className="pr-bar">
        <div className="hr-field">
          <label>귀속연월</label>
          <Select className="pr-select" value={month} onChange={setMonth} options={monthOptions(runs.data)} />
        </div>
        <div className="hr-field">
          <label>조직</label>
          <Select className="pr-select" allowClear placeholder="전체 조직" value={orgUnitId} onChange={setOrgUnitId} options={org.options} popupMatchSelectWidth={false} />
        </div>
        <span className="pr-muted pr-self-end">하위 조직 포함</span>
      </div>

      {(runs.isLoading || cost.isLoading) && <Spin />}
      {cost.error && <p className="hr-notice is-error">{errorMessage(cost.error)}</p>}
      {!runs.isLoading && !run && (
        <div className="pr-empty">
          <strong>{`${ym(month)}은 아직 정산되지 않았습니다.`}</strong>
          <span>급여를 확정하면 인건비가 집계됩니다.</span>
        </div>
      )}
      {run && (
        <>
          <div className="pr-stats">
            <div>
              <span>총 인건비</span>
              <strong>{won(total(run.totals))}</strong>
              <em>{`${ym(month)} · 전체 ${run.totals.headcount}명`}</em>
            </div>
            <div>
              <span>지급 총액</span>
              <strong>{won(run.totals.grossPay)}</strong>
              <em>기본급 및 수당·상여 합계</em>
            </div>
            <div>
              <span>회사 부담</span>
              <strong>{won(run.totals.companyBurdenTotal)}</strong>
              <em>4대보험 회사분</em>
            </div>
            <div>
              <span>전월 대비</span>
              <strong className={diff > 0 ? 'pr-red' : diff < 0 ? 'pr-blue' : undefined}>
                {diff == null ? '–' : `${diff > 0 ? '↑ +' : diff < 0 ? '↓ ' : ''}${Math.round((diff / total(prev.totals)) * 1000) / 10}%`}
              </strong>
              <em>{diff == null ? '이전 정산 없음' : `${ym(prev.payMonth)}보다 ${won(Math.abs(diff))} ${diff >= 0 ? '증가' : '감소'}`}</em>
            </div>
          </div>

          <section className="hr-table pr-card">
            <div className="pr-card-head">
              <strong>조직별 인건비</strong>
              <span className="pr-muted">상위 조직은 하위 조직 소계 · 합계는 중복 제외</span>
            </div>
            <div className="hr-tr is-head">
              <span className="pr-c-orgtree">조직</span>
              <span className="pr-c-cnt">인원</span>
              <span className="pr-c-num">지급 총액</span>
              <span className="pr-c-num">회사 부담</span>
              <span className="pr-c-num">인건비 합계</span>
              <span className="pr-c-num">월 예산</span>
              <span className="pr-c-rate">집행률</span>
            </div>
            {rows.map((o) => {
              const over = o.rate != null && Number(o.rate) > 100;
              return (
                <div key={o.id} className={`hr-tr pr-row${o.hasKids ? ' is-sub' : ''}`}>
                  <span className="pr-c-orgtree" style={{ paddingLeft: `${(o.depth - (focus ? focus.depth : 1)) * 1.25}rem` }}>
                    {o.depth > (focus ? focus.depth : 1) && '└ '}
                    <strong>{o.name}</strong>
                    {o.hasKids && <em> 소계</em>}
                  </span>
                  <span className="pr-c-cnt">{`${o.headcount}명`}</span>
                  <span className="pr-c-num">{won(o.gross)}</span>
                  <span className="pr-c-num">{won(o.burden)}</span>
                  <span className="pr-c-num hr-strong">{won(o.gross + o.burden)}</span>
                  <span className="pr-c-num">{o.budget != null ? won(o.budget) : '–'}</span>
                  <span className="pr-c-rate">
                    {o.rate != null ? (
                      <span className={`pr-rate${over ? ' is-over' : ''}`}>
                        <span>{`${o.rate}%`}</span>
                        <i style={{ width: `${Math.min(100, Number(o.rate))}%` }} />
                        {over && <b>초과</b>}
                      </span>
                    ) : (
                      '–'
                    )}
                  </span>
                </div>
              );
            })}
            <div className="hr-tr pr-row is-sum">
              <span className="pr-c-orgtree">{focus ? `${focus.name} 합계` : '합계'}</span>
              <span className="pr-c-cnt">{`${focus ? sumRows('headcount') : run.totals.headcount}명`}</span>
              <span className="pr-c-num">{won(focus ? sumRows('gross') : run.totals.grossPay)}</span>
              <span className="pr-c-num">{won(focus ? sumRows('burden') : run.totals.companyBurdenTotal)}</span>
              <span className="pr-c-num">{won(focus ? sumRows('gross') + sumRows('burden') : total(run.totals))}</span>
              <span className="pr-c-num">{won(topRows.reduce((a, o) => a + (o.budget ?? 0), 0))}</span>
              <span className="pr-c-rate" />
            </div>
          </section>

          <section className="hr-card pr-trend">
            <div className="pr-card-head is-plain">
              <strong>{`최근 ${trend.length}개월 인건비 추이`}</strong>
              <span className="pr-muted">■ 총 인건비 · 단위: 원</span>
            </div>
            <div className="pr-bars">
              {trend.map((r) => (
                <div key={r.payMonth} className={r.payMonth === month ? 'is-on' : undefined}>
                  <span>{total(r.totals).toLocaleString('ko-KR')}</span>
                  <i style={{ height: `${(total(r.totals) / maxTrend) * 100}%` }} />
                  <em>{`${Number(r.payMonth.slice(5))}월`}</em>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
      <p className="pr-note">ⓘ 예산은 회사 설정 › 조직에서 관리합니다.</p>
    </>
  );
}
