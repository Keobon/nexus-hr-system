// 퇴사 통계(작업 지시서 2-5) — 경영진 · 인사 담당(DASHBOARD_COMPANY). 조회 전용
// 기간(월 · 분기 · 연 단위, 시작 · 종료 달)은 주소(?unit=&from=&to=)에 두고, 바꾸면 모든 그래프 · 표가 같이 다시 받는다
// 퇴사자 이름은 서버가 관리 권한(EMPLOYEE_MANAGE)이 없으면 가려서 준다 — 화면도 한 번 더 가린다
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { DatePicker, Pagination, Segmented } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  TENURE_BUCKETS,
  getResignations,
  getTurnoverByDept,
  getTurnoverByReason,
  getTurnoverByTenure,
  getTurnoverTrend,
  resignationsKey,
  turnoverByDeptKey,
  turnoverByReasonKey,
  turnoverByTenureKey,
  turnoverTrendKey,
} from '../../../api/turnover';
import { useMe } from '../../../auth/useMe';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { PageHead } from '../parts';
import { BarChart, RatioBars, TrendChart, TrendLegend } from './charts';
import '../../me/exec.css';
import './turnover.css';

const UNITS = [
  { value: 'month', label: '월' },
  { value: 'quarter', label: '분기' },
  { value: 'year', label: '연' },
];
const YM = /^\d{4}-(0[1-9]|1[0-2])$/;
const rate = (v) => (v == null ? '–' : `${Number(v).toFixed(1)}%`);
const list = (d) => (Array.isArray(d) ? d : []);

/** 홍길동 → 홍*동 · 이산 → 이* — 서버가 이미 가렸으면(*) 그대로 */
export function maskName(name) {
  if (!name || name.includes('*')) return name;
  if (name.length === 2) return `${name[0]}*`;
  return name.length < 2 ? name : `${name[0]}${'*'.repeat(name.length - 2)}${name[name.length - 1]}`;
}

/** 근속일 → '2년 3개월' */
function tenure(days) {
  if (days == null) return '–';
  const months = Math.floor(days / 30.4375);
  const y = Math.floor(months / 12);
  const m = months % 12;
  if (!y && !m) return `${days}일`;
  return [y && `${y}년`, m && `${m}개월`].filter(Boolean).join(' ');
}

export default function TurnoverStatsPage() {
  const { can } = useMe();
  const [sp, setSp] = useSearchParams();
  const unit = UNITS.some((u) => u.value === sp.get('unit')) ? sp.get('unit') : 'month';
  const to = YM.test(sp.get('to') ?? '') ? sp.get('to') : dayjs().format('YYYY-MM');
  const from = YM.test(sp.get('from') ?? '') && sp.get('from') <= to ? sp.get('from') : dayjs(`${to}-01`).subtract(11, 'month').format('YYYY-MM');
  const range = { from, to };
  const set = (next) => setSp({ unit, from, to, ...next });
  const [page, setPage] = useState(0);

  const trend = useQuery({ queryKey: turnoverTrendKey({ unit, ...range }), queryFn: () => getTurnoverTrend({ unit, ...range }), retry: false });
  const depts = useQuery({ queryKey: turnoverByDeptKey(range), queryFn: () => getTurnoverByDept(range), retry: false });
  const tenures = useQuery({ queryKey: turnoverByTenureKey(range), queryFn: () => getTurnoverByTenure(range), retry: false });
  const reasons = useQuery({ queryKey: turnoverByReasonKey(range), queryFn: () => getTurnoverByReason(range), retry: false });
  const people = useQuery({
    queryKey: resignationsKey({ ...range, page }),
    queryFn: () => getResignations({ ...range, page, size: 20 }),
    placeholderData: keepPreviousData,
    retry: false,
  });

  const trendItems = list(trend.data);
  const totals = trendItems.reduce((a, d) => ({ hires: a.hires + (d.hires ?? 0), resignations: a.resignations + (d.resignations ?? 0) }), { hires: 0, resignations: 0 });
  const deptRows = [...list(depts.data)].sort((a, b) => Number(b.turnoverRate) - Number(a.turnoverRate));
  const tenureItems = TENURE_BUCKETS.map(([key, label]) => ({ key, label, value: list(tenures.data).find((t) => t.bucket === key)?.count ?? 0 }));
  const reasonItems = list(reasons.data).map((r) => ({ key: r.reason, label: labelOf('resignReason', r.reason), count: r.count, ratio: r.ratio }));
  const rows = people.data?.content ?? [];
  const showName = can('EMPLOYEE_MANAGE');

  /** 로딩 · 오류 · 데이터 없음을 같은 모양으로 */
  const state = (q, empty) => {
    if (q.isLoading) return <p className="to-empty">불러오는 중…</p>;
    if (q.error && q.error.status !== 404) return <p className="to-empty">{errorMessage(q.error)}</p>;
    if (empty || q.error) return <p className="to-empty">데이터 없음</p>;
    return null;
  };

  return (
    <div className="hr to-page">
      <PageHead title="퇴사 통계" desc="기간별 퇴사율 추이와 부서 · 근속기간 · 사유별 퇴사 현황을 조회합니다. (조회 전용)" />

      <div className="to-filters">
        <label>
          단위
          <Segmented value={unit} options={UNITS} onChange={(v) => { set({ unit: v }); setPage(0); }} />
        </label>
        <label>
          기간(시작 · 종료 달)
          <DatePicker.RangePicker
            picker="month"
            allowClear={false}
            value={[dayjs(`${from}-01`), dayjs(`${to}-01`)]}
            disabledDate={(d) => d.isAfter(dayjs(), 'month')}
            onChange={(v) => { if (v?.[0] && v?.[1]) { set({ from: v[0].format('YYYY-MM'), to: v[1].format('YYYY-MM') }); setPage(0); } }}
          />
        </label>
        <span className="to-range-note">
          {trendItems.length > 0 && `기간 합계 · 입사 ${totals.hires}명 · 퇴사 ${totals.resignations}명 · 순증감 ${totals.hires - totals.resignations > 0 ? '+' : ''}${totals.hires - totals.resignations}명`}
        </span>
      </div>

      <section className="ex-card">
        <div className="ex-card-head">
          <h3>퇴사율 추이 · 입사자 / 퇴사자</h3>
          <span className="ex-chip">{`${UNITS.find((u) => u.value === unit).label} 단위`}</span>
        </div>
        <div className="to-pad">
          {state(trend, trendItems.length === 0) ?? (
            <>
              <TrendChart items={trendItems} width={1200} height={300} />
              <TrendLegend />
            </>
          )}
        </div>
      </section>

      <div className="to-grid">
        <section className="ex-card">
          <div className="ex-card-head">
            <h3>부서별 퇴사율</h3>
            <span className="ex-chip">퇴사율 높은 순</span>
          </div>
          {state(depts, deptRows.length === 0) ?? (
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
                {deptRows.map((r) => (
                  <tr key={r.orgUnitId}>
                    <td>{r.orgUnitName}</td>
                    <td>{`${Number(r.avgHeadcount).toLocaleString('ko-KR', { maximumFractionDigits: 1 })}명`}</td>
                    <td>{`${r.resignations}명`}</td>
                    <td><strong>{rate(r.turnoverRate)}</strong></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="ex-card">
          <div className="ex-card-head">
            <h3>근속기간별 퇴사자</h3>
          </div>
          <div className="to-pad">{state(tenures, tenureItems.every((t) => !t.value)) ?? <BarChart items={tenureItems} />}</div>
        </section>
      </div>

      <section className="ex-card">
        <div className="ex-card-head">
          <h3>퇴사 사유별 비율</h3>
        </div>
        <div className="to-pad">{state(reasons, reasonItems.length === 0) ?? <RatioBars items={reasonItems} />}</div>
      </section>

      <section className="ex-card">
        <div className="ex-card-head">
          <h3>퇴사자 목록</h3>
          <span className="ex-chip">{showName ? '퇴사일 최근 순' : '퇴사일 최근 순 · 이름 가림'}</span>
        </div>
        {state(people, rows.length === 0) ?? (
          <>
            <table className="ex-table to-table">
              <thead>
                <tr>
                  <th>이름</th>
                  <th>부서</th>
                  <th>직급</th>
                  <th>입사일</th>
                  <th>퇴사일</th>
                  <th>근속기간</th>
                  <th>사유</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={`${r.employeeId}-${r.resignDate}`}>
                    <td><strong>{showName ? r.name : maskName(r.name)}</strong></td>
                    <td>{r.orgUnitName}</td>
                    <td>{r.jobGradeName ?? '–'}</td>
                    <td>{r.hireDate}</td>
                    <td>{r.resignDate}</td>
                    <td>{tenure(r.tenureDays)}</td>
                    <td>{r.resignReason ? labelOf('resignReason', r.resignReason) : '–'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {(people.data?.totalElements ?? 0) > 20 && (
              <div className="to-pager">
                <Pagination size="small" current={page + 1} pageSize={20} total={people.data.totalElements} onChange={(p) => setPage(p - 1)} showSizeChanger={false} />
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
