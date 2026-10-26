import { useEffect, useMemo, useState } from 'react';
import { Form, Input, Modal, Result, Select, Spin, TimePicker } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  changeWorkType,
  checkIn,
  checkOut,
  createExplanation,
  getMyAttendances,
  getMyExplanations,
  getMyOvertimes,
  getMyTrips,
  getTeamToday,
  getToday,
  getWorkSchedules,
  myAttendancesKey,
  myExplanationsKey,
  myOvertimesKey,
  myTripsKey,
  teamTodayKey,
  todayKey,
  withdrawExplanation,
  workSchedulesKey,
} from '../../../api/attendance';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { dashboardKey, getDashboardHome } from '../../../api/dashboard';
import { useMe } from '../../../auth/useMe';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { tryLocation } from '../../../utils/location';
import {
  WEEK_LIMIT,
  WEEK_OT_LIMIT,
  EXPLANATION_BADGE,
  WEEKDAY,
  calendarDates,
  canExplain,
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
      </div>
    </section>
  );
}

/* ───── 2. 출퇴근 기록 및 소명 ───── */
/** 소명(F-ATT-10) — 지각 · 조퇴 · 퇴근미기록 · 결근인 날에 사유와 실제 시각(선택)을 내면 인사 담당이 승인하면서 근태를 고친다 */
function HistorySection({ rows, today, eligible, month }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [target, setTarget] = useState(null); // { date } | { pick: true }
  const mine = useQuery({ queryKey: myExplanationsKey(month), queryFn: () => getMyExplanations(month), enabled: !!month });
  const withdraw = useMutation({
    mutationFn: withdrawExplanation,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      notify.success('소명을 철회했습니다');
    },
    onError: (err) => notify.error(err),
  });

  return (
    <section className="att-section">
      <SectionHead title="출퇴근 기록 및 소명" sub="최근 7일 이력" />
      <div className="att-card is-flush">
        <div className="att-table-top">
          <div>
            <h3>일자별 출퇴근 이력</h3>
            <p>지각 · 조퇴 · 퇴근 미기록 · 결근인 날은 사유와 실제 시각을 내 소명할 수 있습니다.</p>
          </div>
          <button type="button" className="att-btn" disabled={eligible.length === 0} onClick={() => setTarget({ pick: true })}
            title={eligible.length === 0 ? '소명할 날이 없습니다' : undefined}>
            소명 신청
          </button>
        </div>
        <div className="att-table">
          <div className="att-tr is-history is-head">
            <span>날짜</span>
            <span>출근 시각</span>
            <span>퇴근 시각</span>
            <span>근태 상태</span>
            <span>소명</span>
            <span>비고</span>
          </div>
          {rows.map(({ date, day }) => {
            const badge = dayBadge(day, today);
            const x = day?.explanationStatus && EXPLANATION_BADGE[day.explanationStatus];
            return (
              <div key={date} className="att-tr is-history">
                <span className="is-strong">{dot(date)}</span>
                <span>{clock(day?.checkInAt)}</span>
                <span>{clock(day?.checkOutAt)}</span>
                <span>
                  <Badge tone={badge.tone}>{badge.label}</Badge>
                </span>
                <span className="att-expl">
                  {x && <Badge tone={x[0]}>{x[1]}</Badge>}
                  {canExplain(day, today) && (
                    <button type="button" className="att-link" onClick={() => setTarget({ date })}>
                      소명
                    </button>
                  )}
                  {!x && !canExplain(day, today) && '-'}
                </span>
                <span className="is-muted">{dayNote(day)}</span>
              </div>
            );
          })}
        </div>
      </div>

      {(mine.data ?? []).length > 0 && (
        <div className="att-card is-flush">
          <div className="att-table-top">
            <div>
              <h3>이번 달 소명 내역</h3>
              <p>대기 중인 소명은 철회할 수 있고, 반려되면 같은 날 다시 낼 수 있습니다.</p>
            </div>
          </div>
          <div className="att-table">
            <div className="att-tr is-expl is-head">
              <span>날짜</span>
              <span>사유</span>
              <span>요청</span>
              <span>상태</span>
              <span />
            </div>
            {mine.data.map((x) => (
              <div key={x.id} className="att-tr is-expl">
                <span className="is-strong">{dot(x.workDate)}</span>
                <span>
                  {x.reason}
                  {x.rejectReason && <em className="att-reject">{`반려 사유: ${x.rejectReason}`}</em>}
                </span>
                <span className="is-muted">{requestText(x)}</span>
                <span>
                  <Badge tone={EXPLANATION_BADGE[x.status][0]}>{EXPLANATION_BADGE[x.status][1]}</Badge>
                </span>
                <span>
                  {x.status === 'PENDING' && (
                    <button type="button" className="att-link" disabled={withdraw.isPending} onClick={() => withdraw.mutate(x.id)}>
                      철회
                    </button>
                  )}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {target && (
        <ExplanationModal
          days={target.pick ? eligible : eligible.filter((d) => d.date === target.date)}
          onClose={() => setTarget(null)}
        />
      )}
    </section>
  );
}

const requestText = (x) => {
  const parts = [
    x.checkInAt && `출근 ${clock(x.checkInAt)}`,
    x.checkOutAt && `퇴근 ${clock(x.checkOutAt)}`,
    x.workType && labelOf('workType', x.workType),
  ].filter(Boolean);
  return parts.length ? parts.join(' · ') : '사유만';
};

/** 소명 신청 창 — 날짜(목록에서 고르거나 행에서 정해짐) · 지금 기록 · 사유(필수) · 실제 출퇴근 시각 · 근무 형태(선택) */
function ExplanationModal({ days, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const date = Form.useWatch('workDate', form) ?? days[0]?.date;
  const day = days.find((d) => d.date === date);
  const absent = day?.status === 'ABSENT';
  const at = (t) => (t ? dayjs(`${date}T${t.format('HH:mm')}:00`).format() : undefined);

  const save = useMutation({
    mutationFn: (v) =>
      createExplanation({ workDate: v.workDate, reason: v.reason.trim(), checkInAt: at(v.checkIn), checkOutAt: at(v.checkOut), workType: v.workType }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      notify.success(`${dot(date)} 소명을 냈습니다. 인사팀이 승인하면 근태가 정정됩니다`);
      onClose();
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });

  const current = day
    ? `${dayBadge(day, '9999-12-31').label} · 출근 ${clock(day.checkInAt)} · 퇴근 ${clock(day.checkOutAt)}`
    : '-';

  return (
    <Modal open title="근태 소명" footer={null} onCancel={onClose} width={520} destroyOnHidden centered>
      <Form form={form} layout="vertical" requiredMark={false} initialValues={{ workDate: days[0]?.date }} onFinish={(v) => save.mutate(v)}>
        <Form.Item name="workDate" label="날짜">
          <Select disabled={days.length === 1} options={days.map((d) => ({ value: d.date, label: `${dot(d.date)} · ${dayBadge(d, '9999-12-31').label}` }))} />
        </Form.Item>
        <p className="att-hint" style={{ marginTop: '-0.5rem' }}>{`지금 기록: ${current}`}</p>
        <Form.Item name="reason" label="사유 *" rules={[{ required: true, whitespace: true, message: '사유를 입력하세요' }]}>
          <Input.TextArea maxLength={250} showCount autoSize={{ minRows: 2, maxRows: 4 }} placeholder="예: 퇴근 시 카드 미태깅, 외부 미팅으로 늦게 출근" />
        </Form.Item>
        <div className="att-two">
          <Form.Item name="checkIn" label="실제 출근 시각" extra={absent ? '결근인 날은 출근 · 퇴근을 둘 다 넣어야 정정됩니다' : '비우면 그대로'}>
            <TimePicker className="full-width" format="HH:mm" minuteStep={5} needConfirm={false} />
          </Form.Item>
          <Form.Item name="checkOut" label="실제 퇴근 시각">
            <TimePicker className="full-width" format="HH:mm" minuteStep={5} needConfirm={false} />
          </Form.Item>
        </div>
        <Form.Item name="workType" label="근무 형태">
          <Select allowClear placeholder="그대로" options={['OFFICE', 'REMOTE', 'FIELD'].map((v) => ({ value: v, label: labelOf('workType', v) }))} />
        </Form.Item>
        <p className="att-hint">시각 없이 사유만 내면 인사팀이 확인만 하고 기록은 그대로 둡니다.</p>
        <div className="att-modal-foot">
          <button type="button" className="att-btn is-line" onClick={onClose}>
            취소
          </button>
          <button type="submit" className="att-btn" disabled={save.isPending}>
            소명 내기
          </button>
        </div>
      </Form>
    </Modal>
  );
}

/* ───── 3. 근무시간 및 주 52시간 관리 ───── */
/** 누적 근무시간 카드 — limit 이 있으면(주 52시간) 막대 · 남은 시간, 없으면(월간) 합계만 */
function HoursCard({ title, totals, limit, limitLabel }) {
  const ratio = limit ? totals.work / limit : 0;
  const level = ratio >= 1 ? 'over' : ratio >= 44 / 52 ? 'warn' : 'ok';
  const badge = { over: ['red', '초과'], warn: ['red', '주의'], ok: ['blue', '정상'] }[level];
  return (
    <div className="att-card">
      <div className="att-block-head">
        <h3>{title}</h3>
        {limit ? <Badge tone={badge[0]}>{badge[1]}</Badge> : null}
      </div>
      <div className="att-rows">
        <Row label="기본 근로시간">{hm(totals.base)}</Row>
        <Row label="연장 근로시간">{hm(totals.overtime)}</Row>
        <Row label="누적 근무시간">{hm(totals.work)}</Row>
      </div>
      {limit ? (
        <div className="att-block" style={{ gap: '0.5rem' }}>
          <div className="att-bar">
            <div className={level === 'ok' ? '' : `is-${level}`} style={{ width: `${Math.min(100, ratio * 100)}%` }} />
          </div>
          <p className="att-hint">
            {limitLabel} {hm(limit)} · 남은 시간 {hm(Math.max(0, limit - totals.work))}
          </p>
        </div>
      ) : null}
    </div>
  );
}

/**
 * 근무시간 — 주간 합계 · 52시간 막대는 화면 합산(K-84). "연장 근로 잔여"는 두 화면 모두 주 연장 12시간 − 이번 주 연장(Q-112).
 * 근거 없는 지표(월 누적 기준 · 연장 승인률)는 뺐다.
 */
function HoursSection({ week, month, monthData, overtimes }) {
  const s = monthData.summary;
  const live = overtimes.filter((o) => o.status === 'PENDING' || o.status === 'APPROVED');

  return (
    <section className="att-section">
      <SectionHead title="근무시간 및 주 52시간 관리" sub="주간/월간 누적시간" />
      <div className="att-two">
        <HoursCard title="주간 누적 근무시간" totals={week} limit={WEEK_LIMIT} limitLabel="주 52시간 기준" />
        <HoursCard title="월간 누적 근무시간" totals={month} />
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
        <div className="att-stats">
          <Stat label="연장 근로 잔여(주 12시간)" value={hm(Math.max(0, WEEK_OT_LIMIT - week.overtime))} />
          <Stat label="주 52시간까지" value={hm(Math.max(0, WEEK_LIMIT - week.work))} />
        </div>
      </div>
    </section>
  );
}

/* ───── 4. 근태 캘린더 ───── */
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

/* ───── 5. 부서원 근태 현황 — 직원 조회 · 근태 조회 권한(팀은 조직장)이 있을 때만 ───── */
/** 부서원 오늘 근태의 비고 — 자정 넘긴 퇴근 · 근무 장소 */
function teamNote(row, today) {
  if (row.checkOutAt && row.checkOutAt.slice(0, 10) !== today) return '자정 넘긴 퇴근';
  if (row.placeMemo) return row.placeMemo;
  if (row.workType === 'REMOTE') return '재택 근무';
  if (row.workType === 'FIELD') return '외근';
  return '-';
}

/** 부서원 근태 — GET /attendances/today 한 번(노션 2.3 K-85, PR #67). 하위 조직 포함, 퇴직자 제외 */
function TeamSection({ me, today }) {
  const orgUnitId = me.leadOrgUnitIds?.[0] ?? me.employee.orgUnitId;
  const list = useQuery({ queryKey: teamTodayKey(orgUnitId), queryFn: () => getTeamToday(orgUnitId), staleTime: 60_000 });
  const members = list.data?.content ?? [];

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
          {members.map((e) => (
            <div key={e.employeeId} className="att-tr is-team">
              <span className="is-strong">{e.name}</span>
              <span>{e.orgUnitName}</span>
              <span>
                <Badge tone={TODAY_TONE[e.todayStatus]}>{labelOf('todayStatus', e.todayStatus)}</Badge>
              </span>
              <span>{e.workType ? <Badge>{labelOf('workType', e.workType)}</Badge> : '-'}</span>
              <span>{clock(e.checkInAt)}</span>
              <span>{clock(e.checkOutAt)}</span>
              <span className="is-muted">{teamNote(e, today)}</span>
            </div>
          ))}
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
  const showTeam = can('ATTENDANCE_READ'); // GET /attendances/today 권한(팀 범위는 조직장)

  return (
    <div className="att">
      <header className="att-head">
        <h1>출퇴근 및 근태 조회</h1>
        <p>오늘의 출퇴근, 근태 이력, 근무시간을 한 화면에서 관리합니다.</p>
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
      <HistorySection rows={historyRows} today={t.workDate} month={month}
        eligible={allDays.filter((d) => canExplain(d, t.workDate)).sort((a, b) => b.date.localeCompare(a.date))} />
      <HoursSection
        week={week}
        month={monthTotals}
        monthData={cur.data}
        overtimes={overtimes.data?.content ?? []}
      />
      <CalendarSection today={t.workDate} />
      {showTeam && <TeamSection me={me} today={t.workDate} />}
    </div>
  );
}
