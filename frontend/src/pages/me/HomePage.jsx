import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Input, Result, Segmented, Select, Spin } from 'antd';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { changeWorkType, checkIn, checkOut, getMyAttendances, myAttendancesKey, todayKey } from '../../api/attendance';
import { getLeaveTypes, getMyLeaveRequests, leaveTypesKey, myLeaveRequestsKey } from '../../api/leaves';
import { dashboardKey, getDashboardHome } from '../../api/dashboard';
import { getMyProfile, myProfileKey } from '../../api/profile';
import { useMe } from '../../auth/useMe';
import { useNotify } from '../../components/common/useNotify';
import { labelOf } from '../../constants/labels';
import { errorMessage } from '../../constants/errors';
import { formatDays, formatTime } from '../../utils/format';
import { tryLocation } from '../../utils/location';
import './home.css';
import { rem } from '../../utils/size';

const WORKING = ['OFFICE', 'REMOTE', 'FIELD'];
const NO_BUTTON = ['ON_VACATION', 'BUSINESS_TRIP', 'ON_LEAVE'];
const WORK_TYPE_OPTIONS = [
  { value: 'OFFICE', label: '사내' },
  { value: 'REMOTE', label: '재택' },
  { value: 'FIELD', label: '외근' },
];
const EMP_STATUS_TONE = { ACTIVE: 'tone-green', ON_LEAVE: 'tone-orange', RESIGNED: 'tone-gray' };

/** 오늘 근태 → 요약 카드 문구. 시각은 그 근태의 날짜(workDate) 기준(자정 넘긴 퇴근) */
function todaySummary(today) {
  const time = (iso) => formatTime(iso, today.workDate);
  if (WORKING.includes(today.status)) {
    return { value: `${time(today.checkInAt)} 출근`, sub: '근무 중 · 퇴근 등록 가능', tone: 'tone-green', status: '근무중' };
  }
  if (today.status === 'OFF_WORK') {
    return { value: `${time(today.checkOutAt)} 퇴근`, sub: '오늘 근무 완료', tone: 'tone-gray', status: '퇴근' };
  }
  if (today.status === 'BEFORE_WORK') {
    return {
      value: '출근 전',
      sub: today.isWorkday ? '출근 등록 가능' : '휴일 · 출근하면 휴일 근무로 기록',
      tone: 'tone-blue',
      status: '출근 전',
    };
  }
  const label = labelOf('todayStatus', today.status);
  return { value: label, sub: '출퇴근 기록 없음', tone: 'tone-blue', status: label };
}

function formatScore(score) {
  const n = Number(score);
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

function StatCard({ label, value, sub, tone, to }) {
  const body = (
    <>
      <p className="home-stat-label">{label}</p>
      <p className="home-stat-value">{value}</p>
      <p className={`home-stat-sub ${tone}`}>{sub}</p>
    </>
  );
  return to ? (
    <Link to={to} className="home-stat" style={{ display: 'block', textDecoration: 'none' }}>
      {body}
    </Link>
  ) : (
    <div className="home-stat">{body}</div>
  );
}

/** 오늘 근태 패널 — 출근 전이면 [출근 등록] + 근무 형태, 근무 중이면 [퇴근 등록] + 근무 형태 변경(프론트 가이드 3.2) */
function AttendancePanel({ today }) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const [workType, setWorkType] = useState('OFFICE');
  const [placeMemo, setPlaceMemo] = useState('');
  const summary = todaySummary(today);
  const time = (iso) => formatTime(iso, today.workDate);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: dashboardKey });
    queryClient.invalidateQueries({ queryKey: todayKey });
  };

  const checkInMutation = useMutation({
    mutationFn: async () => checkIn({ workType, placeMemo: workType === 'FIELD' ? placeMemo.trim() || null : null, ...(await tryLocation()) }),
    onSuccess: (data) => {
      notify.success('출근했습니다', data);
      refresh();
    },
    onError: (err) => notify.error(err),
  });

  const checkOutMutation = useMutation({
    mutationFn: async () => checkOut(await tryLocation()),
    onSuccess: (data) => {
      notify.success('퇴근했습니다', data);
      refresh();
    },
    onError: (err) => notify.error(err),
  });

  const workTypeMutation = useMutation({
    mutationFn: (next) => changeWorkType({ workType: next, placeMemo: null }),
    onSuccess: () => {
      notify.success('근무 형태를 바꿨습니다');
      refresh();
    },
    onError: (err) => notify.error(err),
  });

  const busy = checkInMutation.isPending || checkOutMutation.isPending;

  return (
    <section className="home-card home-panel">
      <h2 className="home-card-title">오늘 근태</h2>
      <div className="home-attendance-head">
        <p className="home-field-label">현재 상태</p>
        <p className={`home-status ${summary.tone}`}>{summary.status}</p>
      </div>
      <div className="home-times">
        <div>
          <p className="home-field-label">출근 시각</p>
          <p className="home-time">{time(today.checkInAt)}</p>
        </div>
        <div>
          <p className="home-field-label">퇴근 시각</p>
          <p className="home-time">{time(today.checkOutAt)}</p>
        </div>
      </div>
      <p className="home-note">※ 승인된 휴가일에는 출퇴근 버튼이 표시되지 않습니다.</p>

      {!NO_BUTTON.includes(today.status) && today.status !== 'OFF_WORK' && (
        <div className="home-actions">
          {today.status === 'BEFORE_WORK' ? (
            <>
              {workType === 'FIELD' && (
                <Input
                  className="home-place"
                  placeholder="외근 장소"
                  value={placeMemo}
                  maxLength={100}
                  onChange={(e) => setPlaceMemo(e.target.value)}
                />
              )}
              <Select className="home-worktype" value={workType} options={WORK_TYPE_OPTIONS} onChange={setWorkType} aria-label="근무 형태" />
              <button type="button" className="home-btn" disabled={busy} onClick={() => checkInMutation.mutate()}>
                출근 등록
              </button>
            </>
          ) : (
            <>
              <Select
                className="home-worktype"
                value={today.workType}
                options={WORK_TYPE_OPTIONS}
                loading={workTypeMutation.isPending}
                onChange={(next) => workTypeMutation.mutate(next)}
                aria-label="근무 형태 변경"
              />
              <button type="button" className="home-btn" disabled={busy} onClick={() => checkOutMutation.mutate()}>
                퇴근 등록
              </button>
            </>
          )}
        </div>
      )}
    </section>
  );
}

/** 나의 휴가 현황 — me.leaveBalances(차감 종류, 올해 휴가 연도). 종류가 여럿이면 위에서 고른다 */
function LeavePanel({ balances }) {
  const navigate = useNavigate();
  const [typeId, setTypeId] = useState(balances[0]?.leaveTypeId);
  const current = balances.find((b) => b.leaveTypeId === typeId) ?? balances[0];

  return (
    <section className="home-card home-panel">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <h2 className="home-card-title">나의 휴가 현황</h2>
        {balances.length > 1 && (
          <Segmented
            size="small"
            value={current.leaveTypeId}
            options={balances.map((b) => ({ value: b.leaveTypeId, label: b.leaveTypeName }))}
            onChange={setTypeId}
          />
        )}
      </div>
      {current ? (
        <dl className="home-leave-rows">
          <div className="home-leave-row">
            <dt>연간 부여</dt>
            <dd>{formatDays(current.granted)}</dd>
          </div>
          <div className="home-leave-row">
            <dt>사용 완료</dt>
            <dd>{formatDays(current.used)}</dd>
          </div>
          <div className="home-leave-row">
            <dt>승인 대기</dt>
            <dd>{formatDays(current.pending)}</dd>
          </div>
          <div className="home-leave-row">
            <dt>잔여 휴가</dt>
            <dd className="tone-blue">{formatDays(current.remaining)}</dd>
          </div>
        </dl>
      ) : (
        <p className="home-note" style={{ marginTop: rem(33) }}>올해 부여된 휴가가 없습니다.</p>
      )}
      <p className="home-note">휴가 신청 시 잔여일수를 초과하면 제출할 수 없습니다.</p>
      <div className="home-actions">
        <button type="button" className="home-btn" onClick={() => navigate('/me/leaves?apply=1')}>
          휴가 신청
        </button>
      </div>
    </section>
  );
}

/** 승인 대기 현황 — 내가 신청해서 결재를 기다리는 건수(대시보드 me.myPending, API 13장) */
function PendingCard({ pending }) {
  const navigate = useNavigate();
  const rows = [
    ['휴가 신청', pending?.leave, '/me/leaves'],
    ['연장근무', pending?.overtime, '/me/work-requests'],
    ['출장 신청', pending?.businessTrip, '/me/work-requests'],
    ['출장 경비 청구', pending?.expenseClaim, '/me/work-requests'],
  ];
  return (
    <section className="home-card home-panel is-short">
      <h2 className="home-card-title">승인 대기 현황</h2>
      <div className="home-pending">
        {rows.map(([label, count, to]) => (
          <Link key={label} to={to}>
            <span>{label}</span>
            <strong>{`${count ?? 0}건`}</strong>
          </Link>
        ))}
      </div>
      <div className="home-actions">
        <button type="button" className="home-btn" onClick={() => navigate('/me/work-requests')}>
          상세 보기
        </button>
      </div>
    </section>
  );
}

const TREND_MONTHS = 4;
const PRESENT = ['CHECKED_OUT', 'CHECKED_IN', 'MISSING_CHECKOUT', 'ON_BUSINESS_TRIP'];

/**
 * 근태 출근율 추이 — 최근 4개월 내 근태(GET /me/attendances)로 화면에서 계산.
 * 출근율 = 출근 기록이 있는 날(출장 포함) ÷ (그 날 + 결근일). 휴가 · 휴일 · 기록 전 날은 빼고 센다
 */
function AttendanceTrend({ today }) {
  const months = Array.from({ length: TREND_MONTHS }, (_, i) =>
    dayjs(today)
      .subtract(TREND_MONTHS - 1 - i, 'month')
      .format('YYYY-MM'),
  );
  const results = useQueries({ queries: months.map((m) => ({ queryKey: myAttendancesKey(m), queryFn: () => getMyAttendances(m) })) });
  const [hover, setHover] = useState(null);
  const points = months.map((m, i) => {
    const days = results[i].data?.days ?? [];
    const present = days.filter((d) => PRESENT.includes(d.status)).length;
    const expected = present + days.filter((d) => d.status === 'ABSENT').length;
    return { month: m, present, expected, rate: expected ? Math.round((present / expected) * 100) : null };
  });
  const loading = results.some((r) => r.isLoading);
  const W = 300;
  const H = 140;
  const pad = { l: 28, r: 12, t: 12, b: 24 };
  const x = (i) => pad.l + (i * (W - pad.l - pad.r)) / (TREND_MONTHS - 1);
  const y = (v) => pad.t + ((100 - v) * (H - pad.t - pad.b)) / 100;
  const drawn = points.map((p, i) => ({ ...p, cx: x(i), cy: p.rate == null ? null : y(p.rate) }));
  const path = drawn
    .filter((p) => p.cy != null)
    .map((p, i) => `${i ? 'L' : 'M'}${p.cx},${p.cy}`)
    .join(' ');
  const hasData = drawn.some((p) => p.cy != null);

  return (
    <section className="home-card home-chart">
      <h2>근태 출근율 추이</h2>
      <p className="home-chart-sub">최근 4개월 · 출근 기록 ÷ (출근 + 결근)일</p>
      {loading && <Spin />}
      {!loading && !hasData && <p className="home-chart-empty">아직 근태 기록이 없습니다.</p>}
      {!loading && hasData && (
        <div className="home-line">
          <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="월별 출근율 꺾은선 그래프">
            {[0, 50, 100].map((v) => (
              <g key={v}>
                <line x1={pad.l} x2={W - pad.r} y1={y(v)} y2={y(v)} stroke="#e2e8f0" strokeWidth="1" />
                <text x={pad.l - 6} y={y(v) + 3} textAnchor="end" fontSize="9" fill="#64748b">
                  {`${v}%`}
                </text>
              </g>
            ))}
            <path d={path} fill="none" stroke="#38bdf8" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
            {drawn.map((p) => (
              <g key={p.month}>
                <text x={p.cx} y={H - 6} textAnchor="middle" fontSize="10" fill="#64748b">
                  {`${Number(p.month.slice(5))}월`}
                </text>
                {p.cy != null && <circle cx={p.cx} cy={p.cy} r="4" fill="#38bdf8" stroke="#fff" strokeWidth="2" />}
                {p.cy != null && (
                  <circle cx={p.cx} cy={p.cy} r="14" fill="transparent" onMouseEnter={() => setHover(p)} onMouseLeave={() => setHover(null)} />
                )}
              </g>
            ))}
          </svg>
          {hover && (
            <span className="home-tip" style={{ left: `${(hover.cx / W) * 100}%`, top: `${(hover.cy / H) * 100}%` }}>
              {`${Number(hover.month.slice(5))}월 · 출근율 ${hover.rate}% (${hover.present}/${hover.expected}일)`}
            </span>
          )}
        </div>
      )}
    </section>
  );
}

// 휴가 종류 순서대로 고정 색(Figma) — 넷째 종류부터는 "기타" 회색으로 묶는다
const LEAVE_COLORS = ['#38bdf8', '#10b981', '#f9952d'];
const OTHER_COLOR = '#94a3b8';

/** 휴가 유형 분포 — 올해 승인된 내 휴가(GET /me/leave-requests)의 종류별 일수 */
function LeaveDistribution() {
  const year = dayjs().year();
  const reqs = useQuery({ queryKey: myLeaveRequestsKey, queryFn: getMyLeaveRequests });
  const types = useQuery({ queryKey: leaveTypesKey, queryFn: getLeaveTypes });
  const [hover, setHover] = useState(null);
  const used = (reqs.data?.content ?? []).filter((r) => (r.status === 'APPROVED' || r.status === 'CANCEL_REQUESTED') && r.leaveYear === year);
  const order = (types.data ?? []).map((t) => t.name);
  const byType = {};
  used.forEach((r) => {
    byType[r.leaveTypeName] = (byType[r.leaveTypeName] ?? 0) + r.days;
  });
  // 색은 순위가 아니라 종류에 붙는다 — 회사 휴가 종류 순서의 앞 세 개만 색, 나머지는 기타
  const rank = (name) => (order.indexOf(name) < 0 ? 99 : order.indexOf(name));
  const named = Object.keys(byType).sort((a, b) => rank(a) - rank(b));
  const slices = [];
  let rest = 0;
  named.forEach((name) => {
    const idx = rank(name);
    if (idx < LEAVE_COLORS.length) slices.push({ name, days: byType[name], color: LEAVE_COLORS[idx] });
    else rest += byType[name];
  });
  if (rest) slices.push({ name: '기타', days: rest, color: OTHER_COLOR });
  const total = slices.reduce((a, sl) => a + sl.days, 0);
  const R = 60;
  const C = 2 * Math.PI * R;
  const gap = slices.length > 1 ? 2 : 0; // 조각 사이 2px 틈
  let offset = 0;
  const arcs = slices.map((sl) => {
    const len = (sl.days / total) * C;
    const arc = { ...sl, len, offset };
    offset += len;
    return arc;
  });
  const shown = arcs.find((a) => a.name === hover);

  return (
    <section className="home-card home-chart">
      <h2>휴가 유형 분포</h2>
      <p className="home-chart-sub">{`${year}년 승인된 내 휴가 · 종류별 일수`}</p>
      {(reqs.isLoading || types.isLoading) && <Spin />}
      {!reqs.isLoading && !types.isLoading && total === 0 && <p className="home-chart-empty">올해 승인된 휴가가 없습니다.</p>}
      {total > 0 && (
        <div className="home-donut">
          <svg viewBox="0 0 160 160" role="img" aria-label="휴가 종류별 사용 일수 도넛 그래프">
            <g transform="rotate(-90 80 80)">
              {arcs.map((a) => (
                <circle
                  key={a.name}
                  cx="80"
                  cy="80"
                  r={R}
                  fill="none"
                  stroke={a.color}
                  strokeWidth={hover === a.name ? 22 : 18}
                  strokeDasharray={`${Math.max(0, a.len - gap)} ${C}`}
                  strokeDashoffset={-a.offset}
                  onMouseEnter={() => setHover(a.name)}
                  onMouseLeave={() => setHover(null)}
                />
              ))}
            </g>
            <text x="80" y="76" textAnchor="middle" fontSize="11" fill="#64748b">
              {shown ? shown.name : '합계'}
            </text>
            <text x="80" y="96" textAnchor="middle" fontSize="18" fontWeight="700" fill="#0f172a">
              {`${shown ? shown.days : total}일`}
            </text>
          </svg>
          <ul className="home-legend">
            {arcs.map((a) => (
              <li key={a.name}>
                <i style={{ background: a.color }} />
                {a.name}
                <em>{`${a.days}일 · ${Math.round((a.days / total) * 100)}%`}</em>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function InfoCard({ profile, payrollEligible }) {
  return (
    <section className="home-card home-info">
      <div className="home-info-head">
        <div>
          <h2 className="home-card-title">내 정보</h2>
          <p className="home-info-desc">기본 인사정보와 최근 개인 이력을 확인할 수 있습니다.</p>
        </div>
        <Link to="/me" className="home-link">
          상세 보기 →
        </Link>
      </div>
      <div className="home-table-wrap">
        <table className="home-table">
          <colgroup>
            <col style={{ width: '15.7%' }} />
            <col style={{ width: '18.7%' }} />
            <col style={{ width: '14.7%' }} />
            <col style={{ width: '25.5%' }} />
            <col />
          </colgroup>
          <thead>
            <tr>
              <th>이름</th>
              <th>부서</th>
              <th>직급</th>
              <th>직책</th>
              <th>재직상태</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{profile.name}</td>
              <td>{profile.orgUnitName}</td>
              <td>{profile.jobGradeName ?? '-'}</td>
              <td>{profile.jobTitleName ?? '-'}</td>
              <td className={EMP_STATUS_TONE[profile.status]}>{labelOf('employmentStatus', profile.status)}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={5}>
                <div className="home-quick">
                  <span className="home-quick-label">빠른 이동</span>
                  <span className="home-quick-links">
                    {payrollEligible && (
                      <Link to="/me/payroll" className="home-link">
                        급여 명세서 보기 →
                      </Link>
                    )}
                    <Link to="/me/evaluations" className="home-link">
                      평가 결과 보기 →
                    </Link>
                    <Link to="/me/evaluations?tab=assign" className="home-link">
                      인사발령 이력 →
                    </Link>
                  </span>
                </div>
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  );
}

/** 대시보드(F-DASH-01) — Figma EMP_home. 응답에 있는 것만 그린다 */
export default function HomePage() {
  const { me } = useMe();
  const home = useQuery({ queryKey: dashboardKey, queryFn: getDashboardHome });
  const profile = useQuery({ queryKey: myProfileKey, queryFn: getMyProfile });

  if (home.isLoading || profile.isLoading) {
    return (
      <div className="page-center" style={{ minHeight: rem(400) }}>
        <Spin size="large" />
      </div>
    );
  }
  if (home.error || profile.error) {
    return <Result status="warning" title="화면을 불러오지 못했습니다" subTitle={errorMessage(home.error ?? profile.error)} />;
  }

  const { me: mine } = home.data;
  const today = todaySummary(mine.today);
  const leave = mine.leaveBalances?.[0];
  const hasPayslipField = 'latestPayslip' in mine; // 급여 대상이 아니면 필드 자체가 없다(API 13장)

  return (
    <div>
      <h1 className="home-title">나의 업무 홈(My Overview)</h1>
      <p className="home-sub">오늘 필요한 개인 업무와 인사 정보를 한눈에 확인하세요.</p>

      <div className="home-stats">
        <StatCard label="오늘 근태" value={today.value} sub={today.sub} tone={today.tone} to="/me/attendance" />
        <StatCard
          label="잔여 휴가"
          value={leave ? `${leave.remaining} 일` : '-'}
          sub={leave ? `연간 ${leave.granted}일 · 사용 ${leave.used}일` : '부여된 휴가 없음'}
          tone="tone-blue"
          to="/me/leaves"
        />
        {hasPayslipField && (
          <StatCard
            label="최근 급여"
            value={mine.latestPayslip ? `${Number(mine.latestPayslip.payMonth.slice(5, 7))}월 명세서` : '명세서 없음'}
            sub={mine.latestPayslip ? '실지급액 확인 가능' : '확정된 명세서가 없습니다'}
            tone={mine.latestPayslip ? 'tone-blue' : 'tone-gray'}
            to="/me/payroll"
          />
        )}
        <StatCard
          label="최근 평가"
          value={mine.latestEvaluation ? `${formatScore(mine.latestEvaluation.totalScore)} 점` : '-'}
          sub={mine.latestEvaluation ? `${mine.latestEvaluation.cycleName} · 확정` : '확정된 평가 없음'}
          tone={mine.latestEvaluation ? 'tone-orange' : 'tone-gray'}
          to="/me/evaluations"
        />
      </div>

      <div className="home-panels">
        <AttendancePanel today={mine.today} />
        <LeavePanel balances={mine.leaveBalances ?? []} />
        <PendingCard pending={mine.myPending} />
        <LeaveDistribution />
        <AttendanceTrend today={mine.today.workDate} />
      </div>

      <InfoCard profile={profile.data} payrollEligible={me.employee?.payrollEligible} />
    </div>
  );
}
