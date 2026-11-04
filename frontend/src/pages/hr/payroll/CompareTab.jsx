// 급여 · 평가 비교 — 전 직원의 급여와 평가를 한 표로 놓고, 같은 직급 안에서 보상이 성과와 맞는지 본다(기업 운영자 스토리보드 장면 4)
// 서버에 합친 API 가 없어 조회 API 셋을 화면에서 잇는다: GET /salaries · /employees · /evaluations/results(최근 기간 순)
import { useMemo, useState } from 'react';
import { Select, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { evalResultsKey, getEvalResults } from '../../../api/evalAdmin';
import { fetchAllPages, getEmployees } from '../../../api/employees';
import { jobGradesApi } from '../../../api/orgSettings';
import { getSalaries, salariesKey } from '../../../api/payrollAdmin';
import { errorMessage } from '../../../constants/errors';
import { useOrgOptions } from '../parts';
import { won } from './shared';

const COLS = [
  ['name', '이름', '6rem'],
  ['org', '소속', '9rem'],
  ['grade', '직급', '6rem'],
  ['annual', '연봉', '9rem'],
  ['monthly', '월 기본급', '8.5rem'],
  ['vs', '같은 직급 평균 대비', '9rem'],
  ['regular', '최근 정기 평가', '7rem'],
  ['promotion', '최근 승진 고과', '7rem'],
];
const score = (n) => (n == null ? '–' : Number(n).toFixed(1));

export default function CompareTab() {
  const org = useOrgOptions();
  const salaries = useQuery({ queryKey: salariesKey({ all: true }), queryFn: () => fetchAllPages(getSalaries, {}) });
  const employees = useQuery({ queryKey: ['employees', 'compare'], queryFn: () => fetchAllPages(getEmployees, {}) });
  const results = useQuery({ queryKey: evalResultsKey({ all: true }), queryFn: () => fetchAllPages(getEvalResults, {}) });
  const grades = useQuery({ queryKey: jobGradesApi.key(true), queryFn: () => jobGradesApi.list(true) });
  const [orgUnitId, setOrgUnitId] = useState();
  const [gradeName, setGradeName] = useState();
  const [sort, setSort] = useState({ key: 'grade', dir: -1 });

  const rows = useMemo(() => {
    const emp = new Map((employees.data ?? []).map((e) => [e.id, e]));
    const rank = new Map((grades.data ?? []).map((g) => [g.name, g.sortOrder]));
    // 결과는 최근 기간부터 온다 — 직원마다 유형별 첫 건이 최근 값
    const latest = { REGULAR: new Map(), PROMOTION: new Map() };
    (results.data ?? []).forEach((r) => {
      const m = latest[r.cycleKind];
      if (m && !m.has(r.targetId) && r.totalScore != null) m.set(r.targetId, r);
    });
    const base = (salaries.data ?? []).filter((s) => s.registered).map((s) => {
      const gradeOf = emp.get(s.employeeId)?.jobGradeName ?? null;
      return {
        id: s.employeeId,
        name: s.name,
        org: s.orgUnitName,
        grade: gradeOf,
        rank: rank.get(gradeOf) ?? -1,
        annual: s.annualSalary,
        monthly: s.monthlyBase,
        regular: latest.REGULAR.get(s.employeeId)?.totalScore ?? null,
        promotion: latest.PROMOTION.get(s.employeeId)?.totalScore ?? null,
      };
    });
    // 같은 직급 평균은 표 전체 기준이다 — 조직을 골라도 값이 바뀌지 않게. 혼자인 직급은 비교할 수 없다
    const byGrade = new Map();
    base.forEach((r) => byGrade.set(r.grade, [...(byGrade.get(r.grade) ?? []), r.monthly]));
    return base.map((r) => {
      const peers = byGrade.get(r.grade);
      const avg = peers.reduce((a, n) => a + n, 0) / peers.length;
      return { ...r, vs: r.grade && peers.length > 1 ? Math.round((r.monthly / avg - 1) * 1000) / 10 : null };
    });
  }, [salaries.data, employees.data, results.data, grades.data]);

  const focus = orgUnitId ? org.byId(orgUnitId) : null;
  const inFocus = focus ? new Set(org.flat.filter((o) => o.path.slice(0, focus.path.length).join('›') === focus.path.join('›')).map((o) => o.name)) : null;
  const shown = rows
    .filter((r) => (!inFocus || inFocus.has(r.org)) && (!gradeName || r.grade === gradeName))
    .sort((a, b) => {
      const [x, y] = sort.key === 'grade' ? [a.rank, b.rank] : [a[sort.key], b[sort.key]];
      // 값이 없는 줄은 정렬 방향과 상관없이 맨 아래
      if (x == null || y == null) return x == null ? (y == null ? 0 : 1) : -1;
      const c = typeof x === 'string' ? x.localeCompare(y) : x - y;
      return c !== 0 ? c * sort.dir : b.monthly - a.monthly;
    });
  const loading = salaries.isLoading || employees.isLoading || results.isLoading;
  const error = salaries.error ?? employees.error ?? results.error;
  const toggle = (key) => setSort((s) => (s.key === key ? { key, dir: -s.dir } : { key, dir: -1 }));

  return (
    <>
      <div className="pr-bar">
        <div className="hr-field">
          <label>조직</label>
          <Select className="pr-select" allowClear placeholder="전체 조직" value={orgUnitId} onChange={setOrgUnitId} options={org.options} popupMatchSelectWidth={false} />
        </div>
        <div className="hr-field">
          <label>직급</label>
          <Select className="pr-select" allowClear placeholder="전체 직급" value={gradeName} onChange={setGradeName} options={(grades.data ?? []).map((g) => ({ value: g.name, label: g.name }))} />
        </div>
        <span className="pr-muted pr-self-end">{`${shown.length}명 · 열 제목을 누르면 정렬합니다`}</span>
      </div>

      <section className="hr-table pr-card">
        <div className="hr-tr is-head" style={{ gap: '1rem' }}>
          {COLS.map(([key, label, width]) => (
            <span key={key} style={{ width }}>
              <button type="button" className="hr-link" aria-pressed={sort.key === key} onClick={() => toggle(key)}>
                {`${label}${sort.key === key ? (sort.dir > 0 ? ' ▲' : ' ▼') : ''}`}
              </button>
            </span>
          ))}
        </div>
        {loading && (
          <div className="hr-tr is-empty">
            <Spin />
          </div>
        )}
        {error && <div className="hr-tr is-empty">{errorMessage(error)}</div>}
        {!loading && !error && shown.length === 0 && <div className="hr-tr is-empty">조건에 맞는 직원이 없습니다</div>}
        {!loading &&
          !error &&
          shown.map((r) => (
            <div key={r.id} className="hr-tr pr-row" style={{ gap: '1rem' }}>
              <span className="hr-strong" style={{ width: '6rem' }}>{r.name}</span>
              <span style={{ width: '9rem' }}>{r.org}</span>
              <span style={{ width: '6rem' }}>{r.grade ?? '–'}</span>
              <span style={{ width: '9rem' }}>{won(r.annual)}</span>
              <span style={{ width: '8.5rem' }}>{won(r.monthly)}</span>
              <span style={{ width: '9rem' }} className={r.vs > 0 ? 'pr-red' : r.vs < 0 ? 'pr-blue' : undefined}>
                {r.vs == null ? '–' : `${r.vs > 0 ? '+' : ''}${r.vs}%`}
              </span>
              <span style={{ width: '7rem' }}>{score(r.regular)}</span>
              <span style={{ width: '7rem' }}>{score(r.promotion)}</span>
            </div>
          ))}
      </section>
      <p className="hr-notice">같은 직급 평균 대비는 월 기본급 기준이고, 같은 직급이 1명이면 표시하지 않습니다. 평가는 확정된 결과만 나오고 총점은 100점 만점입니다.</p>
    </>
  );
}
