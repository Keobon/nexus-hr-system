// 부서 예산 대비 지출 조회 — 급여를 조회만 하는 사람(경영진, PAYROLL_MANAGE 없음)의 급여 메뉴 화면. Figma 71-5459
// GET /statistics/labor-cost?payMonth — 예산이 있는 활성 조직마다 그 조직 + 하위 조직의 총지급 + 회사부담, 집행률(%). 예산 수정은 없다
import { useState } from 'react';
import { Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getLaborCost, getPayrollRuns, laborCostKey, payrollRunsKey } from '../../../api/payrollAdmin';
import { errorMessage } from '../../../constants/errors';
import { PageHead, useOrgOptions } from '../parts';
import { Field, Table } from '../attendance/shared';
import { monthOptions, won, ym } from './shared';
import '../attendance/at.css';

/** 집행률 색 — 90% 미만 파랑 · 90~100% 주황 · 100% 초과 빨강 */
const tone = (r) => (r == null ? 'is-none' : r > 100 ? 'is-over' : r >= 90 ? 'is-warn' : 'is-ok');

export default function BudgetReadView() {
  const runs = useQuery({ queryKey: payrollRunsKey, queryFn: getPayrollRuns });
  // 처음엔 가장 최근에 정산한 달
  const latest = [...(runs.data ?? [])].sort((a, b) => b.payMonth.localeCompare(a.payMonth))[0]?.payMonth;
  const [picked, setPicked] = useState(null);
  const month = picked ?? latest;
  const cost = useQuery({ queryKey: laborCostKey(month), queryFn: () => getLaborCost(month), enabled: !!month });
  const org = useOrgOptions();
  // 조직도 순서로, 하위 조직은 들여 쓴다
  const order = Object.fromEntries(org.flat.map((o, i) => [o.id, [i, o.depth]]));
  const rows = [...(cost.data?.rows ?? [])].sort((a, b) => (order[a.orgUnitId]?.[0] ?? 999) - (order[b.orgUnitId]?.[0] ?? 999));
  const minDepth = Math.min(...rows.map((r) => order[r.orgUnitId]?.[1] ?? 1));

  return (
    <div className="hr at bg">
      <PageHead title="부서 예산 대비 지출 조회" desc="부서별 예산과 해당월 실제 지출·집행률을 조회합니다. 조회 전용 화면입니다." />

      <div className="at-filters bg-box">
        <Field label="조회 월" width="10rem">
          <Select value={month} onChange={setPicked} options={monthOptions(runs.data)} loading={runs.isLoading} />
        </Field>
      </div>

      <section className="at-card">
        <div className="at-card-head">
          <strong>{month ? `${ym(month)} · 예산이 있는 부서 ${rows.length}개` : '조회 월을 고르세요'}</strong>
          {cost.data && !cost.data.settled && <span className="bg-badge">정산 전 — 지출은 정산 후에 잡힙니다</span>}
        </div>
        <Table
          cols={[
            { key: 'org', label: '부서', width: '14rem' },
            { key: 'budget', label: '예산', width: '10rem' },
            { key: 'cost', label: '실제 지출', width: '10rem' },
            { key: 'rate', label: '집행률' },
          ]}
          loading={cost.isLoading || runs.isLoading}
          error={cost.error && errorMessage(cost.error)}
          empty={rows.length === 0 && '예산이 등록된 부서가 없습니다'}
        >
          {rows.map((r) => {
            const depth = (order[r.orgUnitId]?.[1] ?? minDepth) - minDepth;
            const rate = r.executionRate == null ? null : Number(r.executionRate);
            return (
              <tr key={r.orgUnitId}>
                <td className="at-strong" style={{ paddingLeft: `${1 + depth * 1.25}rem` }}>
                  {depth > 0 && <span className="bg-branch">└ </span>}
                  {r.orgUnitName}
                </td>
                <td>{won(r.monthlyBudget)}</td>
                <td>{won(r.laborCost)}</td>
                <td>
                  <div className="bg-rate">
                    <span className="bg-track">
                      <i className={tone(rate)} style={{ width: `${Math.min(100, rate ?? 0)}%` }} />
                    </span>
                    <strong>{rate == null ? '–' : `${rate}%`}</strong>
                  </div>
                </td>
              </tr>
            );
          })}
        </Table>
      </section>
      <p className="at-muted is-11">※ 집행률 = 실제 지출(총지급액 + 회사부담 4대보험) ÷ 부서 예산. 본부는 하위 팀 지출을 포함합니다. 파란색 90% 미만 · 주황색 90~100% · 빨간색 100% 초과. 예산 수정 기능은 제공되지 않습니다.</p>
    </div>
  );
}
