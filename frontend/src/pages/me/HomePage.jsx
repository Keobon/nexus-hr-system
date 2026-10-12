import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Input, Result, Segmented, Select, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { changeWorkType, checkIn, checkOut, todayKey } from '../../api/attendance';
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
        <button type="button" className="home-btn" onClick={() => navigate('/me/leaves/new')}>
          휴가 신청
        </button>
      </div>
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
      </div>

      <InfoCard profile={profile.data} payrollEligible={me.employee?.payrollEligible} />
    </div>
  );
}
