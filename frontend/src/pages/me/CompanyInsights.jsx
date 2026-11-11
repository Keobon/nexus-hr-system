// 홈 전사 위젯 — 성과 현황(F-DASH-06) · 직원별 인건비(F-DASH-07). 처음 값은 GET /dashboard/home 의 company.performance · laborCost,
// 다른 평가 기간 · 정산 월을 고르면 /dashboard/company/performance · labor-cost 로 다시 받는다.
// 권한이 없으면 카드를 그리지 않고, 권한은 있는데 키가 없으면(확정 평가 · 정산 내역 없음) 빈 카드 문구를 보인다
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Modal, Pagination, Select } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { companyLaborCostKey, companyPerformanceKey, getCompanyLaborCost, getCompanyPerformance } from '../../api/dashboard';
import { useMe } from '../../auth/useMe';
import { errorMessage } from '../../constants/errors';
import './insights.css';

const LEVELS = [
  ['VERY_GOOD', '매우 우수', '#16a34a'],
  ['GOOD', '우수', '#38bdf8'],
  ['NORMAL', '보통', '#a5a1f5'],
  ['POOR', '미흡', '#fdb24c'],
  ['VERY_POOR', '매우 미흡', '#fa9fb0'],
];
const LEVEL_NAME = Object.fromEntries(LEVELS.map(([k, n]) => [k, n]));
const KIND = { REGULAR: '정기', PROMOTION: '승진' };
const won = (n) => (n == null ? '–' : Number(n).toLocaleString('ko-KR'));
const score = (n) => (n == null ? '–' : Number(n).toFixed(1));
const monthLabel = (m) => (m ? `${m.slice(0, 4)}년 ${Number(m.slice(5, 7))}월` : '–');

/** 권한 — 성과는 DASHBOARD_COMPANY + EVAL_READ(전사), 인건비는 DASHBOARD_COMPANY + PAYROLL_READ */
export function useInsightAccess() {
  const { me, can } = useMe();
  const evalAll = (me?.permissions ?? []).some((p) => p.code === 'EVAL_READ' && p.scope === 'ALL');
  return { performance: can('DASHBOARD_COMPANY') && evalAll, laborCost: can('DASHBOARD_COMPANY') && can('PAYROLL_READ') };
}

/** title 이 있으면 제목을 붙인 구역으로(업무 홈 아래), 없으면 카드만(경영 총괄 대시보드) */
export default function CompanyInsights({ company, title }) {
  const access = useInsightAccess();
  if (!access.performance && !access.laborCost) return null;
  const cards = (
    <div className="ins">
      {access.performance && <PerformanceCard initial={company.performance} />}
      {access.laborCost && <LaborCostCard initial={company.laborCost} />}
    </div>
  );
  if (!title) return cards;
  return (
    <section className="home-ins">
      <h2>{title}</h2>
      {cards}
    </section>
  );
}

/* ───── 성과 현황 ───── */
function PerformanceCard({ initial }) {
  const navigate = useNavigate();
  const [cycleId, setCycleId] = useState(null);
  // 고를 수 있는 기간(availableCycles)은 기간 API 에만 있다 — 홈 값이 있을 때만 부른다
  const query = useQuery({
    queryKey: companyPerformanceKey(cycleId),
    queryFn: () => getCompanyPerformance(cycleId),
    enabled: !!initial,
    placeholderData: keepPreviousData,
    retry: false,
  });
  const data = (cycleId ? query.data : (query.data ?? initial)) ?? initial;
  const cycles = query.data?.availableCycles ?? (initial ? [{ evalCycleId: initial.evalCycleId, cycleName: initial.cycleName, cycleKind: initial.cycleKind }] : []);
  const open = (r) => navigate(`/evaluations?tab=results&cycle=${data.evalCycleId}&evaluation=${r.evaluationId}`);

  return (
    <section className="ex-card ins-card">
      <div className="ex-card-head">
        <h3>성과 현황</h3>
        {data && (
          <Select
            className="ins-select"
            size="small"
            value={data.evalCycleId}
            onChange={(v) => setCycleId(v)}
            options={cycles.map((c) => ({ value: c.evalCycleId, label: `${c.cycleName} (${KIND[c.cycleKind] ?? c.cycleKind})` }))}
            popupMatchSelectWidth={false}
          />
        )}
      </div>
      {!data ? (
        <p className="ex-empty">확정된 평가가 없습니다</p>
      ) : (
        <div className="ins-body">
          {cycleId && query.error && <p className="ins-error">{errorMessage(query.error)}</p>}
          <div className="ins-summary">
            <div>
              <span>평가 기간</span>
              <strong>{data.cycleName}</strong>
              <em>{`${KIND[data.cycleKind] ?? data.cycleKind} 평가`}</em>
            </div>
            <div>
              <span>확정 인원</span>
              <strong>
                {data.confirmedCount}
                <small>명</small>
              </strong>
            </div>
            <div>
              <span>전사 평균 총점</span>
              <strong>
                {score(data.average)}
                <small>점</small>
              </strong>
            </div>
          </div>

          <LevelBar counts={data.levelCounts ?? {}} total={data.confirmedCount} />

          <div className="ins-split">
            <RankList title="상위 5명" rows={data.top ?? []} onOpen={open} />
            <RankList title="개선 필요" rows={data.needsImprovement ?? []} onOpen={open} empty="상위 5명 외에 표시할 인원이 없습니다" />
          </div>

          <div>
            <h4 className="ins-sub">조직별 평균</h4>
            <table className="ins-table">
              <thead>
                <tr>
                  <th>조직</th>
                  <th style={{ width: '6rem' }}>확정 인원</th>
                  <th style={{ width: '8rem' }}>평균 총점</th>
                </tr>
              </thead>
              <tbody>
                {(data.byOrgUnit ?? []).length === 0 ? (
                  <tr>
                    <td colSpan={3} className="ex-empty">표시할 조직이 없습니다</td>
                  </tr>
                ) : (
                  data.byOrgUnit.map((o) => (
                    <tr key={o.orgUnitId}>
                      <td className="is-left">{o.orgUnitName}</td>
                      <td>{`${o.count}명`}</td>
                      <td title={o.average == null ? '3명 미만 조직은 점수를 보이지 않습니다' : undefined}>{o.average == null ? '–' : `${score(o.average)}점`}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
            <p className="ins-note">확정 인원 3명 미만인 조직은 개인 점수가 드러나지 않게 평균을 보이지 않습니다.</p>
          </div>
        </div>
      )}
    </section>
  );
}

function LevelBar({ counts, total }) {
  return (
    <div>
      <h4 className="ins-sub">수준 분포</h4>
      <div className="ins-bar" role="img" aria-label="수준 분포">
        {LEVELS.map(([k, name, color]) =>
          counts[k] ? <span key={k} style={{ flexGrow: counts[k], background: color }} title={`${name} ${counts[k]}명`} /> : null,
        )}
        {!total && <span className="is-empty" />}
      </div>
      <ul className="ins-legend">
        {LEVELS.map(([k, name, color]) => (
          <li key={k}>
            <i style={{ background: color }} />
            {`${name} ${counts[k] ?? 0}명`}
          </li>
        ))}
      </ul>
    </div>
  );
}

function RankList({ title, rows, onOpen, empty = '확정된 평가가 없습니다' }) {
  return (
    <div>
      <h4 className="ins-sub">{title}</h4>
      <table className="ins-table">
        <thead>
          <tr>
            <th style={{ width: '3rem' }}>순위</th>
            <th>이름</th>
            <th>소속 · 직급</th>
            <th style={{ width: '4.5rem' }}>총점</th>
            <th style={{ width: '5rem' }}>수준</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={5} className="ex-empty">{empty}</td>
            </tr>
          ) : (
            rows.map((r) => (
              <tr key={r.evaluationId}>
                <td>{r.rank}</td>
                <td className="is-left">
                  <button type="button" className="ins-name" onClick={() => onOpen(r)}>
                    {r.name}
                  </button>
                  <em>{r.employeeNo}</em>
                </td>
                <td className="is-left">{[r.orgUnitName, r.jobGradeName].filter(Boolean).join(' · ')}</td>
                <td>{score(r.totalScore)}</td>
                <td>
                  <span className={`ins-level is-${r.level}`}>{LEVEL_NAME[r.level] ?? r.level}</span>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

/* ───── 직원별 인건비 ───── */
function LaborCostCard({ initial }) {
  const [payMonth, setPayMonth] = useState(null);
  const [full, setFull] = useState(false);
  // 고를 수 있는 달(availableMonths)과 다른 달 상위 10명 — 기간 API(size 10)
  const query = useQuery({
    queryKey: companyLaborCostKey({ payMonth, page: 0, size: 10 }),
    queryFn: () => getCompanyLaborCost({ payMonth: payMonth ?? undefined, page: 0, size: 10 }),
    enabled: !!initial,
    placeholderData: keepPreviousData,
    retry: false,
  });
  const picked = payMonth && query.data ? { ...query.data, top: query.data.employees?.content ?? [] } : null;
  const data = picked ?? initial;
  const months = query.data?.availableMonths ?? (initial ? [initial.payMonth] : []);
  const maxOrg = Math.max(1, ...(data?.byOrgUnit ?? []).map((o) => o.laborCost));
  const change = data?.changeRate;

  return (
    <section className="ex-card ins-card">
      <div className="ex-card-head">
        <h3>직원별 인건비</h3>
        {data && (
          <div className="ins-head-tools">
            <Select className="ins-select" size="small" value={data.payMonth} onChange={setPayMonth} options={months.map((m) => ({ value: m, label: monthLabel(m) }))} />
            <button type="button" className="ex-link" onClick={() => setFull(true)}>
              전체 보기 →
            </button>
          </div>
        )}
      </div>
      {!data ? (
        <p className="ex-empty">정산 내역이 없습니다</p>
      ) : (
        <div className="ins-body">
          {payMonth && query.error && <p className="ins-error">{errorMessage(query.error)}</p>}
          <div className="ins-summary">
            <div>
              <span>귀속 월</span>
              <strong>{monthLabel(data.payMonth)}</strong>
            </div>
            <div>
              <span>인원</span>
              <strong>
                {data.headcount}
                <small>명</small>
              </strong>
            </div>
            <div>
              <span>인건비 합계</span>
              <strong>
                {won(data.total)}
                <small>원</small>
              </strong>
              <em className={change > 0 ? 'is-up' : change < 0 ? 'is-down' : undefined}>
                {change == null ? '직전 정산 없음' : `직전 정산 대비 ${change > 0 ? '▲' : change < 0 ? '▼' : ''} ${Math.abs(change)}%`}
              </em>
            </div>
          </div>

          <div>
            <h4 className="ins-sub">인건비 상위 10명</h4>
            <LaborTable rows={data.top ?? []} />
            <p className="ins-note">인건비 = 총지급액 + 회사 부담(4대 보험 등). 정산 당시 소속 기준입니다.</p>
          </div>

          <div>
            <h4 className="ins-sub">조직별 합계</h4>
            <ul className="ins-orgs">
              {(data.byOrgUnit ?? []).map((o) => (
                <li key={o.orgUnitId}>
                  <span>{`${o.orgUnitName} (${o.headcount}명)`}</span>
                  <div className="ins-org-bar">
                    <i style={{ width: `${(o.laborCost / maxOrg) * 100}%` }} />
                  </div>
                  <b>{`${won(o.laborCost)}원`}</b>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
      {full && data && <LaborCostModal payMonth={data.payMonth} onClose={() => setFull(false)} />}
    </section>
  );
}

function LaborTable({ rows, offset = 0 }) {
  return (
    <table className="ins-table">
      <thead>
        <tr>
          <th style={{ width: '3rem' }}>순위</th>
          <th>사번</th>
          <th>이름</th>
          <th>소속</th>
          <th>직급</th>
          <th>총지급액</th>
          <th>회사 부담</th>
          <th>인건비</th>
          <th style={{ width: '4.5rem' }}>비중</th>
        </tr>
      </thead>
      <tbody>
        {rows.length === 0 ? (
          <tr>
            <td colSpan={9} className="ex-empty">정산 내역이 없습니다</td>
          </tr>
        ) : (
          rows.map((r, i) => (
            <tr key={r.employeeId}>
              <td>{offset + i + 1}</td>
              <td>{r.employeeNo}</td>
              <td>
                <strong>{r.name}</strong>
              </td>
              <td>{r.orgUnitName}</td>
              <td>{r.jobGradeName ?? '–'}</td>
              <td className="is-num">{won(r.grossPay)}</td>
              <td className="is-num">{won(r.companyBurden)}</td>
              <td className="is-num">
                <strong>{won(r.laborCost)}</strong>
              </td>
              <td>{r.share == null ? '–' : `${Number(r.share).toFixed(1)}%`}</td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}

function LaborCostModal({ payMonth, onClose }) {
  const [page, setPage] = useState(0);
  const size = 20;
  const query = useQuery({
    queryKey: companyLaborCostKey({ payMonth, page, size }),
    queryFn: () => getCompanyLaborCost({ payMonth, page, size }),
    placeholderData: keepPreviousData,
    retry: false,
  });
  const emp = query.data?.employees;
  return (
    <Modal open title={`직원별 인건비 · ${monthLabel(payMonth)}`} onCancel={onClose} footer={null} width="60rem" className="ins-modal">
      {query.error ? (
        <p className="ins-error">{errorMessage(query.error)}</p>
      ) : query.isLoading ? (
        <p className="ex-empty">불러오는 중…</p>
      ) : (
        <>
          <LaborTable rows={emp?.content ?? []} offset={page * size} />
          {(emp?.totalElements ?? 0) > size && (
            <Pagination className="ins-pager" size="small" current={page + 1} pageSize={size} total={emp.totalElements} onChange={(p) => setPage(p - 1)} showSizeChanger={false} />
          )}
        </>
      )}
    </Modal>
  );
}
