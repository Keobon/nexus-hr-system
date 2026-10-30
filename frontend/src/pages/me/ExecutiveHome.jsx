// 경영 총괄 대시보드(Executive Overview) — Figma 133-6428. 경영진(회사 대시보드 조회만 하고 관리 권한이 없는 역할)의 첫 화면
// 인원 · 본부별 구성 · 최근 발령은 GET /dashboard/home 의 company, 나머지는 조회 API로 계산한다(서버에 회사 단위 일별 근태 API가 없다)
import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueries, useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { employeeAttendancesKey, getAttendanceMonth, getEmployeeAttendances } from '../../api/attendance';
import { getExpensePage, getOvertimePage, getTodayPage, getTripPage } from '../../api/attendanceAdmin';
import { fetchAllPages } from '../../api/employees';
import { getLeaveBalancePage, getLeaveRequestPage } from '../../api/leaves';
import { labelOf } from '../../constants/labels';
import './exec.css';

const ORG_COLORS = ['#a5a1f5', '#9fe0c4', '#fa9fb0', '#fdb24c', '#b9b5f8', '#7dd3fc', '#fcd34d'];
const WEEK = ['일', '월', '화', '수', '목', '금', '토'];
const pct = (n) => (Number.isFinite(n) ? Math.round(n * 1000) / 10 : 0);
const ASSIGN_TONE = { PROMOTION: 'is-green', TRANSFER: 'is-blue', TITLE_CHANGE: 'is-purple' };

export default function ExecutiveHome({ company }) {
  const today = dayjs().format('YYYY-MM-DD');
  const year = dayjs().year();

  // 금일 가동률 — 오늘 근태 전 직원
  const todayRows = useQuery({ queryKey: ['attendance', 'today-all'], queryFn: () => fetchAllPages(getTodayPage, {}), refetchInterval: 60_000 });
  // 연차 소진율 — 올해 차감 종류 부여 대비 사용
  const balances = useQuery({ queryKey: ['leave-balances', 'admin', year, null], queryFn: () => fetchAllPages(getLeaveBalancePage, { leaveYear: year }) });
  // 승인 대기 — 전사 결재 대기(휴가 · 휴가 취소 · 연장 · 출장 · 경비)
  const pending = useQueries({
    queries: [
      ['leave', () => getLeaveRequestPage({ status: 'PENDING', size: 1 })],
      ['leave-cancel', () => getLeaveRequestPage({ status: 'CANCEL_REQUESTED', size: 1 })],
      ['overtime', () => getOvertimePage({ status: 'PENDING', size: 1 })],
      ['trip', () => getTripPage({ status: 'PENDING', size: 1 })],
      ['expense', () => getExpensePage({ status: 'PENDING', size: 1 })],
    ].map(([k, fn]) => ({ queryKey: ['exec', 'pending', k], queryFn: fn })),
  });

  const t = todayRows.data ?? [];
  const checkedIn = t.filter((r) => r.checkInAt).length;
  const onVacation = t.filter((r) => r.todayStatus === 'ON_VACATION').length;
  const used = (balances.data ?? []).flatMap((e) => e.balances).reduce((a, b) => ({ g: a.g + b.granted, u: a.u + b.used }), { g: 0, u: 0 });
  const pendingCounts = pending.map((q) => q.data?.totalElements ?? 0);
  const pendingTotal = pendingCounts.reduce((a, n) => a + n, 0);
  const hc = company.headcount;

  const tiles = [
    { label: '전사 재직 인원', value: hc.active, unit: '명', sub: `휴직 ${hc.onLeave}명 · 퇴직 ${hc.resigned}명`, tone: 'is-green' },
    { label: '금일 전사 가동률', value: t.length ? pct(checkedIn / t.length) : '–', unit: '%', sub: `${checkedIn}명 정상 출근/${onVacation}명 휴가`, tone: 'is-blue' },
    { label: '연간 연차 소진율', value: used.g ? pct(used.u / used.g) : '–', unit: '%', sub: `${year}년 사용 ${used.u}일 / 부여 ${used.g}일`, tone: 'is-orange' },
    {
      label: '승인 대기 건수',
      value: pendingTotal,
      unit: '건',
      sub: `휴가 ${pendingCounts[0] + pendingCounts[1]} · 연장 ${pendingCounts[2]} · 출장 ${pendingCounts[3]} · 경비 ${pendingCounts[4]}`,
      tone: 'is-orange',
    },
  ];

  return (
    <div className="ex">
      <h1 className="ex-title">경영 총괄 대시보드(Executive Overview)</h1>

      <h2 className="ex-sec">전사 실시간 도메인 집계 현황</h2>
      <div className="ex-tiles">
        {tiles.map((x) => (
          <div key={x.label} className="ex-tile">
            <span>{x.label}</span>
            <strong>
              {x.value}
              <small>{x.unit}</small>
            </strong>
            <em className={x.tone}>{x.sub}</em>
          </div>
        ))}
      </div>

      <div className="ex-charts">
        <OrgDonut headcount={hc} />
        <WeeklyAttendance today={today} />
      </div>

      <RecentAssignments rows={company.recentAssignments ?? []} today={today} />
    </div>
  );
}

/* ───── 본부별 인원 구성비 — 최상위 바로 아래 조직, 대표 등 최상위 직속은 "직속" ───── */
function OrgDonut({ headcount }) {
  const orgs = headcount.byOrgUnit ?? [];
  const direct = Math.max(0, headcount.active + headcount.onLeave - orgs.reduce((a, o) => a + o.count, 0));
  const slices = [...orgs.map((o) => ({ name: o.orgUnitName, count: o.count })), ...(direct ? [{ name: '직속', count: direct }] : [])]
    .filter((s) => s.count > 0)
    .map((s, i) => ({ ...s, color: ORG_COLORS[i % ORG_COLORS.length] }));
  const total = slices.reduce((a, s) => a + s.count, 0);
  const R = 70;
  const C = 2 * Math.PI * R;
  let offset = 0;

  return (
    <section className="ex-card">
      <div className="ex-card-head">
        <h3>본부별 인원 구성비</h3>
        <span className="ex-chip">오늘 기준</span>
      </div>
      <div className="ex-donut">
        <svg width="180" height="180" viewBox="0 0 180 180" role="img" aria-label="본부별 인원 구성비">
          <circle cx="90" cy="90" r={R} fill="none" stroke="#f1f5f9" strokeWidth="32" />
          {slices.map((s) => {
            const len = (s.count / total) * C;
            const el = (
              <circle key={s.name} cx="90" cy="90" r={R} fill="none" stroke={s.color} strokeWidth="32" strokeDasharray={`${len} ${C - len}`} strokeDashoffset={-offset} transform="rotate(-90 90 90)">
                <title>{`${s.name} ${s.count}명`}</title>
              </circle>
            );
            offset += len;
            return el;
          })}
          <text x="90" y="96" textAnchor="middle" className="ex-donut-total">{`총 ${total}명`}</text>
        </svg>
        <ul className="ex-legend">
          {slices.map((s) => (
            <li key={s.name}>
              <i style={{ background: s.color }} />
              {`${s.name} (${s.count}명 · ${Math.round((s.count / total) * 100)}%)`}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

/* ───── 주간 근태 현황(출근 vs 휴가) — 최근 6 근무일. 직원별 월 근태를 모아 날짜마다 센다 ───── */
function WeeklyAttendance({ today }) {
  const months = [...new Set([dayjs(today).subtract(10, 'day').format('YYYY-MM'), today.slice(0, 7)])];
  const people = useQuery({ queryKey: ['attendance', 'month', today.slice(0, 7), null], queryFn: () => fetchAllPages(getAttendanceMonth, { month: today.slice(0, 7) }) });
  const ids = (people.data ?? []).map((p) => p.employeeId);
  const monthly = useQueries({ queries: ids.flatMap((id) => months.map((m) => ({ queryKey: employeeAttendancesKey(id, m), queryFn: () => getEmployeeAttendances(id, m), staleTime: 5 * 60 * 1000 }))) });
  const loading = people.isLoading || monthly.some((q) => q.isLoading);

  const days = useMemo(() => {
    const map = new Map();
    monthly.forEach((q) =>
      (q.data?.days ?? []).forEach((d) => {
        if (d.date > today) return;
        const cur = map.get(d.date) ?? { date: d.date, workday: false, work: 0, vacation: 0 };
        cur.workday = cur.workday || d.isWorkday;
        if (['CHECKED_IN', 'CHECKED_OUT', 'MISSING_CHECKOUT'].includes(d.status)) cur.work += 1;
        if (d.status === 'ON_VACATION') cur.vacation += 1;
        map.set(d.date, cur);
      }),
    );
    return [...map.values()].filter((d) => d.workday).sort((a, b) => a.date.localeCompare(b.date)).slice(-6);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [monthly.map((q) => q.dataUpdatedAt).join(), today]);
  const max = Math.max(1, ...days.map((d) => Math.max(d.work, d.vacation)));

  return (
    <section className="ex-card">
      <div className="ex-card-head">
        <h3>주간 근태 현황 (출근vs휴가)</h3>
        <span className="ex-chip">최근 6 근무일</span>
      </div>
      {loading ? (
        <p className="ex-empty">불러오는 중…</p>
      ) : days.length === 0 ? (
        <p className="ex-empty">표시할 근무일이 없습니다.</p>
      ) : (
        <>
          <div className="ex-bars">
            {days.map((d) => (
              <div key={d.date} className="ex-bar-col">
                <div className="ex-bar-pair">
                  <span className="is-work" style={{ height: `${(d.work / max) * 100}%` }} title={`출근 ${d.work}명`} />
                  <span className="is-vac" style={{ height: `${(d.vacation / max) * 100}%` }} title={`휴가 ${d.vacation}명`} />
                </div>
                <em>{`${d.date.slice(5).replace('-', '/')}`}</em>
                <em>{d.date === today ? '(오늘)' : `(${WEEK[dayjs(d.date).day()]})`}</em>
              </div>
            ))}
          </div>
          <div className="ex-bar-legend">
            <span><i className="is-work" />정상출근</span>
            <span><i className="is-vac" />연차/휴가</span>
          </div>
        </>
      )}
    </section>
  );
}

/* ───── 최근 인사발령 이력 ───── */
function RecentAssignments({ rows, today }) {
  const navigate = useNavigate();
  const fromTo = (r) => {
    const from = [r.fromOrgUnitName, r.fromJobGradeName].filter(Boolean).join(' ');
    const to = [r.toOrgUnitName, r.toJobGradeName].filter(Boolean).join(' ');
    return from ? `${from} → ${to}` : `(신규) → ${to}`;
  };
  return (
    <section className="ex-card ex-assign">
      <div className="ex-card-head">
        <h3 className="is-lg">최근 인사발령 이력</h3>
        <button type="button" className="ex-link" onClick={() => navigate('/assignments')}>
          전체 이력 보기 →
        </button>
      </div>
      <table className="ex-table">
        <thead>
          <tr>
            <th style={{ width: '9rem' }}>발효일자</th>
            <th style={{ width: '12rem' }}>성명</th>
            <th style={{ width: '8rem' }}>발령 구분</th>
            <th>발령 내용(변경 전 → 변경 후)</th>
            <th>발령 사유</th>
            <th style={{ width: '7rem' }}>비고</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={6} className="ex-empty">최근 인사발령이 없습니다.</td>
            </tr>
          ) : (
            rows.slice(0, 5).map((r) => (
              <tr key={r.id}>
                <td>{r.effectiveDate}</td>
                <td>
                  <strong>{r.employeeName}</strong>
                  {`(${r.employeeNo})`}
                </td>
                <td>
                  <span className={`ex-pill ${ASSIGN_TONE[r.assignmentType] ?? ''}`}>{labelOf('assignmentType', r.assignmentType)}</span>
                </td>
                <td>{fromTo(r)}</td>
                <td>{r.reason || '–'}</td>
                <td className={r.effectiveDate <= today ? 'ex-done' : 'ex-soon'}>{r.effectiveDate <= today ? '적용 완료' : '적용 예정'}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </section>
  );
}
