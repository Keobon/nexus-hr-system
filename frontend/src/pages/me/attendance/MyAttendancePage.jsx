import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { App, Result, Select, Spin } from 'antd';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  changeWorkType,
  checkIn,
  checkOut,
  employeeAttendancesKey,
  getEmployeeAttendances,
  getMyAttendances,
  getMyOvertimes,
  getMyTrips,
  getToday,
  getTrip,
  getWorkSchedules,
  myAttendancesKey,
  myOvertimesKey,
  myTripsKey,
  todayKey,
  workSchedulesKey,
} from '../../../api/attendance';
import { dashboardKey, getDashboardHome } from '../../../api/dashboard';
import { employeesKey, getEmployees } from '../../../api/employees';
import { useMe } from '../../../auth/useMe';
import { useNotify } from '../../../components/common/useNotify';
import { usePendingFeature } from '../../../components/common/usePendingFeature';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { tryLocation } from '../../../utils/location';
import {
  WEEK_LIMIT,
  WEEK_OT_LIMIT,
  WEEKDAY,
  calendarDates,
  clock,
  dayBadge,
  dayCell,
  dayNote,
  dot,
  hm,
  lastDates,
  monthOf,
  prevMonth,
  sumMinutes,
  weekDates,
} from './logic';
import './attendance.css';

const WORKING = ['OFFICE', 'REMOTE', 'FIELD'];
const NO_BUTTON = ['ON_VACATION', 'BUSINESS_TRIP', 'ON_LEAVE'];
const WORK_TYPE_OPTIONS = [
  { value: 'OFFICE', label: '사내' },
  { value: 'REMOTE', label: '재택' },
  { value: 'FIELD', label: '외근' },
];
const TODAY_TONE = { BEFORE_WORK: '', OFFICE: 'green', REMOTE: 'green', FIELD: 'green', OFF_WORK: 'blue', BUSINESS_TRIP: 'amber', ON_VACATION: 'purple', ON_LEAVE: '' };
const TRIP_TONE = { PENDING: 'amber', APPROVED: 'green', REJECTED: 'red', CANCELLED: '' };

function Badge({ tone = '', children }) {
  return <span className={`att-badge ${tone}`}>{children}</span>;
}

function Stat({ label, value, text }) {
  return (
    <div className="att-stat">
      <span className="att-label">{label}</span>
      {text ? <span className="att-text">{value}</span> : <strong>{value}</strong>}
    </div>
  );
}

function Row({ label, children }) {
  return (
    <div className="att-row">
      <span>{label}</span>
      <span>{children ?? '-'}</span>
    </div>
  );
}

function SectionHead({ title, sub }) {
  return (
    <div className="att-section-head">
      <h2>{title}</h2>
      {sub && <span>{sub}</span>}
    </div>
  );
}

/** 1분마다 지금 시각 — 근무 중 경과 시간 표시용 */
function useNow() {
  const [now, setNow] = useState(() => dayjs());
  useEffect(() => {
    const id = setInterval(() => setNow(dayjs()), 60_000);
    return () => clearInterval(id);
  }, []);
  return now;
}

/* ───── 1. 오늘의 출퇴근 및 근무 상태 ───── */
function TodaySection({ today, todayRow, schedule, updatedAt }) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const pending = usePendingFeature();
  const now = useNow();
  const [workType, setWorkType] = useState('OFFICE');
  const [placeMemo, setPlaceMemo] = useState('');

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: todayKey });
    queryClient.invalidateQueries({ queryKey: ['attendance', 'me'] });
    queryClient.invalidateQueries({ queryKey: dashboardKey });
  };
  const inMutation = useMutation({
    mutationFn: async () => checkIn({ workType, placeMemo: workType === 'FIELD' ? placeMemo.trim() || null : null, ...(await tryLocation()) }),
    onSuccess: (data) => {
      notify.success('출근했습니다', data);
      refresh();
    },
    onError: (err) => notify.error(err),
  });
  const outMutation = useMutation({
    mutationFn: async () => checkOut(await tryLocation()),
    onSuccess: (data) => {
      notify.success('퇴근했습니다', data);
      refresh();
    },
    onError: (err) => notify.error(err),
  });
  const typeMutation = useMutation({
    mutationFn: (next) => changeWorkType({ workType: next, placeMemo: null }),
    onSuccess: () => {
      notify.success('근무 형태를 바꿨습니다');
      refresh();
    },
    onError: (err) => notify.error(err),
  });

  const working = WORKING.includes(today.status);
  const before = today.status === 'BEFORE_WORK';
  const hidden = NO_BUTTON.includes(today.status);
  // 근무 중 경과 시간(지금 − 출근) — 표시용. 인정 근무 시간은 퇴근 뒤 서버 값(workMinutes)
  const elapsed = working && today.checkInAt ? now.diff(dayjs(today.checkInAt), 'minute') : null;
  const late = todayRow?.late;
  const busy = inMutation.isPending || outMutation.isPending;

  return (
    <section className="att-section">
      <SectionHead title="오늘의 출퇴근 및 근무 상태" sub={`업데이트 ${dayjs(updatedAt).format('HH:mm')}`} />
      <div className="att-card is-24">
        <div className="att-two is-24">
          <div className="att-block">
            <div className="att-block-head">
              <h3>출퇴근 체크</h3>
              {working && <Badge tone="green">{labelOf('workType', today.workType)}</Badge>}
            </div>
            {hidden ? (
              <p className="att-hint">
                오늘은 {labelOf('todayStatus', today.status)}입니다. 승인된 휴가 · 출장일에는 출퇴근 버튼이 표시되지 않습니다.
              </p>
            ) : (
              <>
                <div className="att-checks">
                  <button
                    type="button"
                    className={`att-check${before ? ' is-primary' : ''}`}
                    disabled={!before || busy}
                    onClick={() => inMutation.mutate()}
                  >
                    {before ? '출근' : '출근 완료'}
                  </button>
                  <button
                    type="button"
                    className={`att-check${working ? ' is-primary' : ''}`}
                    disabled={!working || busy}
                    onClick={() => outMutation.mutate()}
                  >
                    {today.status === 'OFF_WORK' ? '퇴근 완료' : '퇴근'}
                  </button>
                </div>
                {before && (
                  <div className="att-worktype">
                    <span>근무 형태</span>
                    <Select size="small" value={workType} options={WORK_TYPE_OPTIONS} onChange={setWorkType} />
                    {workType === 'FIELD' && (
                      <input placeholder="외근 장소" maxLength={100} value={placeMemo} onChange={(e) => setPlaceMemo(e.target.value)} />
                    )}
                  </div>
                )}
                {working && (
                  <div className="att-worktype">
                    <span>근무 형태 변경</span>
                    <Select
                      size="small"
                      value={today.workType}
                      options={WORK_TYPE_OPTIONS}
                      loading={typeMutation.isPending}
                      onChange={(next) => typeMutation.mutate(next)}
                    />
                  </div>
                )}
                {!today.isWorkday && before && <p className="att-hint">오늘은 휴일입니다. 출근하면 휴일 근무로 기록됩니다.</p>}
              </>
            )}
          </div>

          <div className="att-block">
            <div className="att-block-head">
              <h3>오늘 인정 근무 시간</h3>
              {today.checkInAt && <Badge tone={late ? 'red' : 'blue'}>{late ? '지각' : '정상'}</Badge>}
            </div>
            <div className="att-stats">
              <Stat label="출근 시각" value={clock(today.checkInAt)} />
              <Stat label={working ? '현재 근무 시간' : '퇴근 시각'} value={working ? hm(elapsed) : clock(today.checkOutAt)} />
            </div>
            <div className="att-stats">
              <Stat label="예정 퇴근 시각" value={schedule?.endTime ?? '-'} />
              <Stat label="인정 근무 시간" value={working ? '퇴근 후 확정' : todayRow?.workMinutes != null ? hm(todayRow.workMinutes) : '-'} />
            </div>
          </div>
        </div>

        <div className="att-two is-24">
          <div className="att-block">
            <div className="att-block-head">
              <h3>근무시간 변경 신청</h3>
              <Badge>없음</Badge>
            </div>
            <Stat label="근무시간 변경 내역" value={schedule ? `현재 ${schedule.startTime} ~ ${schedule.endTime} · 신청 내역 없음` : '-'} text />
            <Stat label="신청 상태" value="신청 없음" text />
            <button type="button" className="att-btn" onClick={() => pending('근무시간 변경 신청', '개인 근무시간 변경')}>
              근무시간 변경 신청
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}

/* ───── 2. 출퇴근 기록 및 소명 ───── */
function HistorySection({ rows, today }) {
  const pending = usePendingFeature();
  return (
    <section className="att-section">
      <SectionHead title="출퇴근 기록 및 소명" sub="최근 7일 이력" />
      <div className="att-card is-flush">
        <div className="att-table-top">
          <div>
            <h3>일자별 출퇴근 이력</h3>
            <p>날짜별 출근/퇴근 기록 시각과 근태 상태를 확인합니다.</p>
          </div>
          <button type="button" className="att-btn" onClick={() => pending('소명 신청', '근태 소명')}>
            소명 신청
          </button>
        </div>
        <div className="att-table">
          <div className="att-tr is-history is-head">
            <span>날짜</span>
            <span>출근 시각</span>
            <span>퇴근 시각</span>
            <span>근태 상태</span>
            <span>소명 상태</span>
            <span>비고</span>
          </div>
          {rows.map(({ date, day }) => {
            const badge = dayBadge(day, today);
            return (
              <div key={date} className="att-tr is-history">
                <span className="is-strong">{dot(date)}</span>
                <span>{clock(day?.checkInAt)}</span>
                <span>{clock(day?.checkOutAt)}</span>
                <span>
                  <Badge tone={badge.tone}>{badge.label}</Badge>
                </span>
                <span>
                  <Badge>없음</Badge>
                </span>
                <span className="is-muted">{dayNote(day)}</span>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

/* ───── 3. 근무시간 및 주 52시간 관리 ───── */
function HoursCard({ title, totals, limit, limitLabel }) {
  const ratio = limit ? totals.work / limit : 0;
  const level = ratio >= 1 ? 'over' : ratio >= 44 / 52 ? 'warn' : 'ok';
  const badge = { over: ['red', '초과'], warn: ['red', '주의'], ok: ['blue', '정상'] }[level];
  return (
    <div className="att-card">
      <div className="att-block-head">
        <h3>{title}</h3>
        <Badge tone={badge[0]}>{badge[1]}</Badge>
      </div>
      <div className="att-rows">
        <Row label="기본 근로시간">{hm(totals.base)}</Row>
        <Row label="연장 근로시간">{hm(totals.overtime)}</Row>
        <Row label="누적 근무시간">{hm(totals.work)}</Row>
      </div>
      <div className="att-block" style={{ gap: '0.5rem' }}>
        <div className="att-bar">
          <div className={level === 'ok' ? '' : `is-${level}`} style={{ width: `${Math.min(100, ratio * 100)}%` }} />
        </div>
        <p className="att-hint">
          {limitLabel} {hm(limit)} · 남은 시간 {hm(Math.max(0, limit - totals.work))}
        </p>
      </div>
    </div>
  );
}

function HoursSection({ week, month, monthData, overtimes, monthDays }) {
  const s = monthData.summary;
  // 그달 52시간 기준 = 주 52시간 × (그달 일수 ÷ 7)
  const monthLimit = Math.round(WEEK_LIMIT * (monthDays / 7));
  const live = overtimes.filter((o) => o.status === 'PENDING' || o.status === 'APPROVED');
  const decided = overtimes.filter((o) => o.status === 'APPROVED' || o.status === 'REJECTED');
  const approvedCount = overtimes.filter((o) => o.status === 'APPROVED').length;
  const rate = decided.length ? `${Math.round((approvedCount / decided.length) * 100)}%` : '-';

  return (
    <section className="att-section">
      <SectionHead title="근무시간 및 주 52시간 관리" sub="주간/월간 누적시간" />
      <div className="att-two">
        <HoursCard title="주간 누적 근무시간" totals={week} limit={WEEK_LIMIT} limitLabel="주 52시간 기준" />
        <HoursCard title="월간 누적 근무시간" totals={month} limit={monthLimit} limitLabel="월 누적 기준" />
      </div>
      <div className="att-card">
        <div className="att-block-head">
          <h3>연장/야간/휴일 근무 내역</h3>
          <span className="att-sub">신청 및 실제 인정 시간 · 이번 달</span>
        </div>
        <div className="att-stats is-4">
          <Stat label="신청 연장시간" value={hm(live.reduce((a, o) => a + o.requestedMinutes, 0))} />
          <Stat label="인정 연장시간" value={hm(s.overtimeMinutes)} />
          <Stat label="야간 근무시간" value={hm(s.nightMinutes)} />
          <Stat label="휴일 근무시간" value={hm(s.holidayMinutes + s.holidayOvertimeMinutes)} />
        </div>
        <div className="att-stats is-4">
          <Stat label="잔여 연장 가능시간(주)" value={hm(Math.max(0, WEEK_OT_LIMIT - week.overtime))} />
          <Stat label="주 52시간까지" value={hm(Math.max(0, WEEK_LIMIT - week.work))} />
          <Stat label="월 누적 기준까지" value={hm(Math.max(0, monthLimit - month.work))} />
          <Stat label="연장 근무 승인률" value={rate} />
        </div>
      </div>
    </section>
  );
}

/* ───── 4. 외근 및 출장 관리 ───── */
function TripSection({ trips, monthData, today }) {
  const navigate = useNavigate();
  const { modal } = App.useApp();
  const upcoming = trips
    .filter((t) => t.status !== 'CANCELLED' && t.status !== 'REJECTED' && t.endDate >= today)
    .sort((a, b) => a.startDate.localeCompare(b.startDate));
  const next = upcoming[0];
  const detail = useQuery({ queryKey: ['trip', next?.id], queryFn: () => getTrip(next.id), enabled: !!next });
  const pendingCount = trips.filter((t) => t.status === 'PENDING').length;
  const remoteDays = monthData.days.filter((d) => d.workType === 'REMOTE' && d.status && d.status !== 'NOT_RECORDED');
  const lastRemote = remoteDays[remoteDays.length - 1];

  return (
    <section className="att-section">
      <SectionHead title="외근 및 출장 관리" sub="휴가 외 특수근무" />
      <div className="att-two">
        <div className="att-card">
          <div className="att-block-head">
            <h3>외근/출장 신청 및 현황</h3>
            <Badge tone={pendingCount ? 'amber' : ''}>{pendingCount ? '신청 중' : '없음'}</Badge>
          </div>
          <div className="att-stats">
            <Stat label="예정 출장" value={`${upcoming.length}건`} />
            <Stat label="승인 대기" value={`${pendingCount}건`} />
          </div>
          <div className="att-rows is-10">
            <Row label="일정">{next ? (next.startDate === next.endDate ? dot(next.startDate) : `${dot(next.startDate)} ~ ${dot(next.endDate).slice(5)}`) : '예정된 출장 없음'}</Row>
            <Row label="장소">{next?.destination}</Row>
            <Row label="목적">{detail.data?.purpose}</Row>
            <Row label="신청 상태">
              {next ? <Badge tone={TRIP_TONE[next.status]}>{labelOf('requestStatus', next.status)}</Badge> : '-'}
            </Row>
          </div>
          <button type="button" className="att-btn" onClick={() => navigate('/me/work-requests?tab=trip')}>
            외근/출장 신청
          </button>
        </div>

        <div className="att-card">
          <div className="att-block-head">
            <h3>재택근무 신청 및 현황</h3>
            <Badge tone={monthData.summary.remoteDays ? 'green' : ''}>{monthData.summary.remoteDays ? '이번 달 재택' : '없음'}</Badge>
          </div>
          <div className="att-stats">
            <Stat label="이번 달 재택" value={`${monthData.summary.remoteDays}일`} />
            <Stat label="이번 달 외근" value={`${monthData.summary.fieldDays}일`} />
          </div>
          <div className="att-rows is-10">
            <Row label="최근 재택일">{lastRemote ? dot(lastRemote.date) : '없음'}</Row>
            <Row label="장소">재택</Row>
            <Row label="신청 방법">출근할 때 근무 형태로 선택</Row>
          </div>
          <button
            type="button"
            className="att-btn"
            onClick={() =>
              modal.info({
                title: '재택근무',
                content: '재택근무는 따로 신청하지 않고, 출근할 때 근무 형태를 "재택"으로 고르면 됩니다. 근무 중에도 근무 형태를 바꿀 수 있습니다.',
                okText: '확인',
              })
            }
          >
            재택근무 신청
          </button>
        </div>
      </div>
    </section>
  );
}

/* ───── 5. 근태 캘린더 ───── */
function CalendarSection({ today }) {
  const [month, setMonth] = useState(monthOf(today));
  const q = useQuery({ queryKey: myAttendancesKey(month), queryFn: () => getMyAttendances(month) });
  const byDate = useMemo(() => Object.fromEntries((q.data?.days ?? []).map((d) => [d.date, d])), [q.data]);
  const dates = calendarDates(month);
  const m = dayjs(`${month}-01`);

  return (
    <section className="att-section">
      <SectionHead title="근태 캘린더" sub="월별 근무 달력" />
      <div className="att-card">
        <div className="att-block-head">
          <div className="att-cal-title">
            <button type="button" className="att-cal-nav" aria-label="이전 달" onClick={() => setMonth(m.subtract(1, 'month').format('YYYY-MM'))}>
              ‹
            </button>
            <h3>{m.format('YYYY년 M월')} 근태 달력</h3>
            <button type="button" className="att-cal-nav" aria-label="다음 달" onClick={() => setMonth(m.add(1, 'month').format('YYYY-MM'))}>
              ›
            </button>
          </div>
          <div className="att-legend">
            <Badge tone="green">근무</Badge>
            <Badge tone="blue">외근</Badge>
            <Badge tone="amber">출장</Badge>
            <Badge>재택</Badge>
          </div>
        </div>
        {q.isLoading ? (
          <Spin />
        ) : (
          <div className="att-cal">
            {dates.map((d) => {
              const date = d.format('YYYY-MM-DD');
              const inMonth = d.format('YYYY-MM') === month;
              const cell = inMonth ? dayCell(byDate[date], today) : { cls: '', label: '—' };
              return (
                <div key={date} className={`att-day ${cell.cls}${inMonth ? '' : ' is-out'}${date === today ? ' is-today' : ''}`}>
                  <span>{`${WEEKDAY[d.day()]} · ${d.date()}`}</span>
                  <strong title={cell.label}>{cell.label}</strong>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </section>
  );
}

/* ───── 6. 부서원 근태 현황 — 직원 조회 · 근태 조회 권한(팀은 조직장)이 있을 때만 ───── */
function TeamSection({ me, month, today }) {
  const orgUnitId = me.leadOrgUnitIds?.[0] ?? me.employee.orgUnitId;
  const params = { orgUnitId, status: 'ACTIVE', size: 20 };
  const list = useQuery({ queryKey: employeesKey(params), queryFn: () => getEmployees(params) });
  const members = list.data?.content ?? [];
  const days = useQueries({
    queries: members.map((e) => ({
      queryKey: employeeAttendancesKey(e.id, month),
      queryFn: () => getEmployeeAttendances(e.id, month),
      staleTime: 60_000,
    })),
  });

  return (
    <section className="att-section">
      <SectionHead title="부서원 근태 현황" sub="팀원들의 현재 근무/외근/출장 상태 조회" />
      <div className="att-card is-flush">
        <div className="att-table">
          <div className="att-tr is-team is-head">
            <span>이름</span>
            <span>부서</span>
            <span>근태 상태</span>
            <span>근무 형태</span>
            <span>출근 시각</span>
            <span>퇴근 시각</span>
            <span>비고</span>
          </div>
          {members.length === 0 && <p className="att-empty">{list.isLoading ? '불러오는 중…' : '표시할 부서원이 없습니다.'}</p>}
          {members.map((e, i) => {
            const day = days[i]?.data?.days?.find((d) => d.date === today);
            return (
              <div key={e.id} className="att-tr is-team">
                <span className="is-strong">{e.name}</span>
                <span>{e.orgUnitName}</span>
                <span>
                  <Badge tone={TODAY_TONE[e.todayStatus]}>{labelOf('todayStatus', e.todayStatus)}</Badge>
                </span>
                <span>{day?.workType ? <Badge>{labelOf('workType', day.workType)}</Badge> : '-'}</span>
                <span>{clock(day?.checkInAt)}</span>
                <span>{clock(day?.checkOutAt)}</span>
                <span className="is-muted">{dayNote(day)}</span>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

/** 출퇴근 및 근태 조회(F-ATT-01·02·03) — Figma EMP_출퇴근 */
export default function MyAttendancePage() {
  const { me, can } = useMe();
  const today = useQuery({ queryKey: todayKey, queryFn: getToday });
  const workDate = today.data?.workDate;
  const month = workDate ? monthOf(workDate) : null;
  // 이번 주 · 최근 7일이 지난달에 걸치면 지난달 기록도 받는다
  const needPrev = workDate ? [...weekDates(workDate), ...lastDates(workDate, 7)].some((d) => monthOf(d) !== month) : false;

  const cur = useQuery({ queryKey: myAttendancesKey(month), queryFn: () => getMyAttendances(month), enabled: !!month });
  const prev = useQuery({
    queryKey: myAttendancesKey(month && prevMonth(month)),
    queryFn: () => getMyAttendances(prevMonth(month)),
    enabled: !!month && needPrev,
  });
  const schedules = useQuery({ queryKey: workSchedulesKey, queryFn: getWorkSchedules });
  const home = useQuery({ queryKey: dashboardKey, queryFn: getDashboardHome });
  const overtimes = useQuery({ queryKey: myOvertimesKey(month), queryFn: () => getMyOvertimes(month), enabled: !!month });
  const trips = useQuery({ queryKey: myTripsKey, queryFn: getMyTrips });

  if (today.isLoading || cur.isLoading || (needPrev && prev.isLoading)) {
    return (
      <div className="page-center" style={{ minHeight: '25rem' }}>
        <Spin size="large" />
      </div>
    );
  }
  const error = today.error ?? cur.error;
  if (error) return <Result status="warning" title="근태 정보를 불러오지 못했습니다" subTitle={errorMessage(error)} />;

  const t = today.data;
  const allDays = [...(prev.data?.days ?? []), ...cur.data.days];
  const byDate = Object.fromEntries(allDays.map((d) => [d.date, d]));
  const todayRow = byDate[t.workDate];
  const week = sumMinutes(weekDates(t.workDate).map((d) => byDate[d]));
  const monthTotals = sumMinutes(cur.data.days);
  const historyRows = lastDates(t.workDate, 7).map((date) => ({ date, day: byDate[date] }));
  const schedule = schedules.data?.current;
  const leave = home.data?.me?.leaveBalances?.[0];
  const tripRows = trips.data?.content ?? [];
  const pendingTrips = tripRows.filter((r) => r.status === 'PENDING');
  const nextPending = pendingTrips.sort((a, b) => a.startDate.localeCompare(b.startDate))[0];
  const weekLevel = week.work >= WEEK_LIMIT ? ['red', '초과'] : week.work >= 44 * 60 ? ['red', '주의'] : ['blue', '정상'];
  const showTeam = can('ATTENDANCE_READ') && can('EMPLOYEE_READ');

  return (
    <div className="att">
      <header className="att-head">
        <h1>출퇴근 및 근태 조회</h1>
        <p>오늘의 출퇴근, 근태 이력, 근무시간, 외근/출장, 재택근무를 한 화면에서 관리합니다.</p>
      </header>

      <div className="att-summary">
        <div className="att-summary-card">
          <p className="att-label">오늘 근태</p>
          <div className="att-summary-main">
            <Badge tone={TODAY_TONE[t.status]}>{WORKING.includes(t.status) ? '근무중' : labelOf('todayStatus', t.status)}</Badge>
            <strong>{t.checkInAt ? `${clock(t.checkInAt)} 출근` : '출근 전'}</strong>
          </div>
          <p className="att-summary-sub">
            예정 퇴근 {schedule?.endTime ?? '-'} · 인정시간 {WORKING.includes(t.status) ? '퇴근 후 확정' : todayRow?.workMinutes != null ? hm(todayRow.workMinutes) : '-'}
          </p>
        </div>
        <div className="att-summary-card">
          <p className="att-label">잔여 휴가</p>
          <div className="att-summary-main">
            <Badge tone={leave?.remaining ? 'blue' : ''}>{leave?.remaining ? '사용 가능' : '없음'}</Badge>
            <strong>{leave ? `${leave.remaining}일` : '-'}</strong>
          </div>
          <p className="att-summary-sub">{leave ? `연간 ${leave.granted}일 · 사용 ${leave.used}일 · 신청 ${leave.pending}일` : '부여된 휴가 없음'}</p>
        </div>
        <div className="att-summary-card">
          <p className="att-label">주간 누적시간</p>
          <div className="att-summary-main">
            <Badge tone={weekLevel[0]}>{weekLevel[1]}</Badge>
            <strong>{hm(week.work).replace(/^0/, '')}</strong>
          </div>
          <p className="att-summary-sub">
            주 52시간까지 {hm(Math.max(0, WEEK_LIMIT - week.work))} · 잔여 연장 {hm(Math.max(0, WEEK_OT_LIMIT - week.overtime))}
          </p>
        </div>
        <div className="att-summary-card">
          <p className="att-label">외근/출장</p>
          <div className="att-summary-main">
            <Badge tone={pendingTrips.length ? 'amber' : ''}>{pendingTrips.length ? '신청 중' : '없음'}</Badge>
            <strong>{pendingTrips.length}건</strong>
          </div>
          <p className="att-summary-sub">{nextPending ? `${dot(nextPending.startDate).slice(5)} ${nextPending.destination} 출장 · 승인 대기` : '승인 대기 중인 출장 없음'}</p>
        </div>
      </div>

      <TodaySection today={t} todayRow={todayRow} schedule={schedule} updatedAt={today.dataUpdatedAt} />
      <HistorySection rows={historyRows} today={t.workDate} />
      <HoursSection
        week={week}
        month={monthTotals}
        monthData={cur.data}
        overtimes={overtimes.data?.content ?? []}
        monthDays={dayjs(`${month}-01`).daysInMonth()}
      />
      <TripSection trips={tripRows} monthData={cur.data} today={t.workDate} />
      <CalendarSection today={t.workDate} />
      {showTeam && <TeamSection me={me} month={month} today={t.workDate} />}
    </div>
  );
}
