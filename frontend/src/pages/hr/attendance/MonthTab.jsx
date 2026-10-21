// 근태 조회(Figma 484-26008) — 직원별 월 집계, 행을 누르면 아래에 일별 근태 상세
// 일별 상세에서 ATTENDANCE_MANAGE 는 날짜별로 정정(기록 있는 날, attendanceId) · 입력(미기록 · 결근 날)한다(F-ATT-04)
import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { attendanceMonthKey, employeeAttendancesKey, getAttendanceMonth, getEmployeeAttendances } from '../../../api/attendance';
import { fetchAllPages } from '../../../api/employees';
import { useMe } from '../../../auth/useMe';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import CorrectModal from './CorrectModal';
import { ListCard, MonthField, OrgField, Pill, SearchButton, SearchField, Table, dotDay, hhmm, hm, matches, useApplied } from './shared';

const times = (n, unit) => `${n}${unit}`;
/** 출퇴근 기록이 있는 날 — 정정할 수 있다 */
const hasRecord = (d) => ['CHECKED_IN', 'CHECKED_OUT', 'MISSING_CHECKOUT'].includes(d.status);
/** 기록이 없는 근무일(미기록 · 결근) — 새로 입력할 수 있다 */
const canCreate = (d) => ['NOT_RECORDED', 'ABSENT'].includes(d.status);

export default function MonthTab() {
  const f = useApplied({ month: dayjs().format('YYYY-MM'), orgUnitId: null, q: '' });
  const [pick, setPick] = useState(null);
  const { month, orgUnitId, q } = f.applied;
  const data = useQuery({ queryKey: attendanceMonthKey(month, orgUnitId), queryFn: () => fetchAllPages(getAttendanceMonth, { month, orgUnitId: orgUnitId ?? undefined }) });
  const rows = useMemo(() => (data.data ?? []).filter((r) => matches(q, r.name, r.employeeNo)), [data.data, q]);
  const selected = rows.find((r) => r.employeeId === pick);
  const [y, m] = month.split('-');

  return (
    <>
      <div className="at-filters">
        <MonthField value={f.draft.month} onChange={(v) => f.set({ month: v })} />
        <OrgField value={f.draft.orgUnitId} onChange={(v) => f.set({ orgUnitId: v })} />
        <SearchField value={f.draft.q} onChange={(v) => f.set({ q: v })} onEnter={f.apply} />
        <SearchButton onClick={f.apply} />
      </div>

      <ListCard title={`${y}년 ${Number(m)}월 근태 집계 · 총 ${rows.length}명`} note="월 마감 기준 · 시간은 시:분으로 표시">
        <Table
          cols={[
            { key: 'no', label: '사번', width: '8.75rem' },
            { key: 'name', label: '이름', width: '5rem' },
            { key: 'org', label: '소속', width: '8rem' },
            { key: 'late', label: '지각' },
            { key: 'early', label: '조퇴' },
            { key: 'absent', label: '결근' },
            { key: 'remote', label: '재택' },
            { key: 'field', label: '외근' },
            { key: 'trip', label: '출장' },
            { key: 'work', label: '근무' },
            { key: 'ot', label: '연장' },
            { key: 'night', label: '야간' },
            { key: 'holi', label: '휴일' },
          ]}
          loading={data.isLoading}
          error={data.error && errorMessage(data.error)}
          empty={rows.length === 0 && '조건에 맞는 직원이 없습니다'}
        >
          {rows.map((r) => {
            const s = r.summary;
            return (
              <tr key={r.employeeId} className={`is-click${pick === r.employeeId ? ' is-on' : ''}`} onClick={() => setPick(pick === r.employeeId ? null : r.employeeId)}>
                <td>{r.employeeNo}</td>
                <td>{r.name}</td>
                <td>{r.orgUnitName}</td>
                <td className={s.lateCount ? 'at-warn' : undefined}>{times(s.lateCount, '회')}</td>
                <td className={s.earlyLeaveCount ? 'at-warn' : undefined}>{times(s.earlyLeaveCount, '회')}</td>
                <td className={s.absentDays ? 'at-red' : undefined}>{times(s.absentDays, '일')}</td>
                <td>{times(s.remoteDays, '일')}</td>
                <td>{times(s.fieldDays, '일')}</td>
                <td>{times(s.tripDays, '일')}</td>
                <td>{hhmm(s.workMinutes)}</td>
                <td>{hhmm(s.overtimeMinutes)}</td>
                <td>{hhmm(s.nightMinutes)}</td>
                <td>{hhmm(s.holidayMinutes + s.holidayOvertimeMinutes)}</td>
              </tr>
            );
          })}
        </Table>
      </ListCard>

      {selected && <DayDetail key={`${selected.employeeId}-${month}`} person={selected} month={month} onClose={() => setPick(null)} />}
    </>
  );
}

/** 하루 상태 알약 — 지각 · 조퇴가 있으면 그게 먼저, 쉬는 날은 공휴일 · 주말 */
function dayPill(d) {
  if (d.late) return ['지각', 'warn'];
  if (d.earlyLeave) return ['조퇴', 'warn'];
  if (!d.status) return [d.holidayName ? '공휴일' : [0, 6].includes(dayjs(d.date).day()) ? '주말' : '쉬는 날', 'plain'];
  const tone = { CHECKED_OUT: 'ok', CHECKED_IN: 'ok', MISSING_CHECKOUT: 'warn', ON_VACATION: 'purple', ON_BUSINESS_TRIP: 'info', ABSENT: 'alert', NOT_RECORDED: 'gray', ON_LEAVE: 'gray' };
  return [labelOf('attendanceStatus', d.status), tone[d.status] ?? 'gray'];
}

function DayDetail({ person, month, onClose }) {
  const { can } = useMe();
  const manage = can('ATTENDANCE_MANAGE');
  const [target, setTarget] = useState(null);
  const data = useQuery({ queryKey: employeeAttendancesKey(person.employeeId, month), queryFn: () => getEmployeeAttendances(person.employeeId, month) });
  // 오늘 이후 날짜는 빼고 보여 준다
  const days = (data.data?.days ?? []).filter((d) => !dayjs(d.date).isAfter(dayjs(), 'day'));
  const first = days[0]?.date;
  const last = days.at(-1)?.date;

  return (
    <ListCard title={`${person.name} · 일별 근태 상세`} note={`${person.employeeNo} · ${person.orgUnitName}`}>
      <div className="at-sub-head">
        <span className="at-muted">{first ? `조회 구간 ${first.replaceAll('-', '.')}–${last.slice(5).replace('-', '.')} · ${days.length}일` : ''}</span>
        <button type="button" className="at-link" onClick={onClose}>
          상세 접기
        </button>
      </div>
      <Table
        cols={[
          { key: 'date', label: '날짜', width: '11rem' },
          { key: 'st', label: '상태', width: '6rem' },
          { key: 'wt', label: '근무 형태' },
          { key: 'in', label: '출근' },
          { key: 'out', label: '퇴근' },
          { key: 'work', label: '근무' },
          { key: 'ot', label: '연장' },
          { key: 'night', label: '야간' },
          ...(manage ? [{ key: 'act', label: '', width: '5rem' }] : []),
        ]}
        loading={data.isLoading}
        error={data.error && errorMessage(data.error)}
        empty={days.length === 0 && '아직 표시할 날짜가 없습니다'}
      >
        {days.map((d) => {
          const [label, tone] = dayPill(d);
          const off = !d.status;
          return (
            <tr key={d.date} className={off ? 'is-off' : undefined}>
              <td>{`${dotDay(d.date)}${d.holidayName ? ` ${d.holidayName}` : ''}`}</td>
              <td>
                <Pill tone={tone}>{label}</Pill>
              </td>
              <td>{d.workType ? labelOf('workType', d.workType) : '–'}</td>
              <td>{hm(d.checkInAt)}</td>
              <td>{hm(d.checkOutAt)}</td>
              <td>{hhmm(d.workMinutes)}</td>
              <td>{hhmm(d.overtimeMinutes)}</td>
              <td>{hhmm(d.nightMinutes)}</td>
              {manage && (
                <td>
                  {hasRecord(d) && d.attendanceId ? (
                    <button type="button" className="at-link" onClick={() => setTarget({ mode: 'patch', attendanceId: d.attendanceId, employeeId: person.employeeId, employeeName: person.name, workDate: d.date, record: d })}>
                      정정
                    </button>
                  ) : canCreate(d) ? (
                    <button type="button" className="at-link" onClick={() => setTarget({ mode: 'create', employeeId: person.employeeId, employeeName: person.name, workDate: d.date })}>
                      입력
                    </button>
                  ) : null}
                </td>
              )}
            </tr>
          );
        })}
      </Table>
      <p className="at-foot">공휴일·주말은 근무 기록이 없으면 집계에서 제외됩니다. 월간 합계와 위 조회 구간은 다를 수 있습니다.</p>
      {target && <CorrectModal target={target} onClose={() => setTarget(null)} />}
    </ListCard>
  );
}
