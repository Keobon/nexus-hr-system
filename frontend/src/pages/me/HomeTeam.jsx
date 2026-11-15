// 홈 — 할 일(결재 · 평가 · 관리 배지)과 조직장의 내 조직 현황(F-DASH-05). 값은 GET /dashboard/home 이 권한대로 준다(API 13장)
import { Link } from 'react-router-dom';

/**
 * 오늘 할 일 — 처리할 것이 있는 사람에게만 보인다.
 * 결재 · 평가는 조직장이거나 건수가 있을 때, 관리 배지(admin)는 서버가 권한대로 넣은 필드만.
 */
export function TodoBar({ todos, admin, isLead }) {
  const items = [];
  const approvals = todos?.approvalsPending ?? 0;
  const evaluations = todos?.evaluationsToSubmit ?? 0;
  if (isLead || approvals > 0) items.push({ label: '결재할 건', count: approvals, to: '/approvals' });
  if (isLead || evaluations > 0) items.push({ label: '제출할 평가', count: evaluations, to: '/evaluations/todo' });
  if (admin && 'attendanceCorrections' in admin) {
    items.push({ label: '근태 정정 대상', count: admin.attendanceCorrections, to: '/attendance?tab=corrections' });
  }
  if (admin && 'reassignNeeded' in admin) {
    items.push({ label: '승인자 재지정 필요', count: admin.reassignNeeded, to: '/settings/approval-lines?tab=reassign' });
  }
  if (admin?.setupProgress) {
    const { doneSteps, totalSteps } = admin.setupProgress;
    items.push({ label: '초기 설정', value: `${doneSteps}/${totalSteps}단계`, to: '/setup', urgent: true });
  }
  if (items.length === 0) return null;

  return (
    <section className="home-card home-todo" aria-label="오늘 할 일">
      <h2 className="home-card-title">오늘 할 일</h2>
      <div className="home-todo-items">
        {items.map((it) => {
          const urgent = it.urgent || (it.count ?? 0) > 0;
          return (
            <Link key={it.label} to={it.to} className={`home-todo-item${urgent ? ' is-urgent' : ''}`}>
              <span>{it.label}</span>
              <strong>{it.value ?? `${it.count ?? 0}건`}</strong>
            </Link>
          );
        })}
      </div>
    </section>
  );
}

const TEAM_TILES = [
  ['working', '근무 중', '사내 · 재택 · 외근 · 퇴근'],
  ['notCheckedIn', '출근 전', null],
  ['onVacation', '휴가', null],
  ['onTrip', '출장', null],
  ['onLeave', '휴직', null],
];

/** 내 조직 현황 — 내가 조직장인 조직과 하위 조직의 재직 · 휴직 직원(나 포함). 휴일에는 출근 전이 결근이 아니라고 알린다 */
export function MyOrgCard({ org, isWorkday }) {
  const usage = org.leaveUsageThisMonth ?? [];
  return (
    <section className="home-card home-team" aria-label="내 조직 현황">
      <div className="home-team-head">
        <div>
          <h2 className="home-card-title">내 조직 현황</h2>
          <p className="home-team-sub">{`${org.orgUnitName} · ${org.memberCount}명(하위 조직 포함)`}</p>
        </div>
        <div className="home-team-links">
          <Link to="/attendance" className="home-link">
            근태 현황 보기 →
          </Link>
          <Link to="/leaves" className="home-link">
            휴가 현황 보기 →
          </Link>
        </div>
      </div>
      <div className="home-team-tiles">
        {TEAM_TILES.map(([key, label, hint]) => (
          <div key={key} className={`home-team-tile${key === 'notCheckedIn' && isWorkday && org.today[key] > 0 ? ' is-warn' : ''}`}>
            <span>{label}</span>
            <strong>{`${org.today[key] ?? 0}명`}</strong>
            {hint && <em>{hint}</em>}
          </div>
        ))}
      </div>
      <p className="home-team-foot">
        {!isWorkday && <span className="home-team-holiday">오늘은 휴일이라 출근 전 인원은 결근이 아닙니다. </span>}
        {`이번 달 지각 ${org.lateCountThisMonth}회`}
        {' · 이번 달 휴가 '}
        {usage.length === 0 ? '사용 없음' : usage.map((u) => `${u.leaveTypeName} ${u.days}일`).join(', ')}
      </p>
    </section>
  );
}
