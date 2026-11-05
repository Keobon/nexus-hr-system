// 연장근무 → 붙는 수당(근태 연동 수당) — 날짜 · 시간으로 자동으로 나뉜다. 서버 계산(AttendanceCalculator)과 같은 규칙
// 근무일: 전부 연장 / 휴일(근무 요일이 아니거나 회사 휴일): 소정근로(예: 8시간)까지 휴일, 넘으면 휴일연장 / 야간 시간대(예: 22–06시)에 걸친 만큼 야간 가산
// 배율은 회사 설정 › 급여 항목에 있고 직원은 볼 수 없어 이름 · 시간만 보여 준다
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { getHolidays, getWorkSchedules, holidaysKey, workSchedulesKey } from '../../../api/workSchedule';
import { formatMinutes } from '../../../utils/format';

const DOW = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const toMin = (hhmm) => {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
};

/** [s, e)(분, e 는 다음 날이면 1440 넘음)와 야간 시간대가 겹치는 분 — 야간은 날을 넘는다(22:00–06:00) */
export function nightOverlap(s, e, nightStart = '22:00', nightEnd = '06:00') {
  const ns = toMin(nightStart);
  let ne = toMin(nightEnd);
  if (ne <= ns) ne += 1440;
  let total = 0;
  // 전날 밤 · 그날 밤 · 다음 날 밤 구간과 겹치는 만큼
  for (const shift of [-1440, 0, 1440]) {
    total += Math.max(0, Math.min(e, ne + shift) - Math.max(s, ns + shift));
  }
  return total;
}

/** 수당 줄 — [{ key, name, minutes }] */
export function allowanceLines({ holiday, minutes, nightMinutes, standardMinutes = 480 }) {
  const lines = holiday
    ? [
        { key: 'HOLIDAY', name: '휴일근로수당', minutes: Math.min(minutes, standardMinutes) },
        { key: 'HOLIDAY_OVERTIME', name: '휴일연장근로수당', minutes: Math.max(0, minutes - standardMinutes) },
      ]
    : [{ key: 'OVERTIME', name: '연장근로수당', minutes }];
  lines.push({ key: 'NIGHT', name: '야간근로수당(가산)', minutes: nightMinutes });
  return lines.filter((l) => l.minutes > 0);
}

/** 회사 근무시간 · 휴일 — 근무 요일 · 소정근로 · 야간 시간대 */
export function useWorkCalendar(date) {
  const year = (date ? dayjs(date) : dayjs()).year();
  const schedule = useQuery({ queryKey: workSchedulesKey, queryFn: getWorkSchedules, staleTime: 10 * 60 * 1000 });
  const holidays = useQuery({ queryKey: holidaysKey(year), queryFn: () => getHolidays(year), staleTime: 10 * 60 * 1000 });
  const cur = schedule.data?.current;
  const isHoliday = (d) => {
    const day = dayjs(d);
    const offDay = cur?.workDays ? !cur.workDays.includes(DOW[day.day()]) : day.day() === 0 || day.day() === 6;
    const named = (holidays.data ?? []).find((h) => h.holidayDate === day.format('YYYY-MM-DD'));
    return { holiday: offDay || !!named, holidayName: named?.name ?? (offDay ? '휴무일' : null) };
  };
  return { schedule: cur, isHoliday, ready: !!cur };
}

/** 수당 내역 표시 — title: "예상 수당" · "붙는 수당" */
export function AllowanceBox({ title, holidayName, lines, note }) {
  return (
    <div className="wr-allow">
      <div className="wr-allow-head">
        <strong>{title}</strong>
        {holidayName && <em>{`휴일 근무 · ${holidayName}`}</em>}
      </div>
      {lines.length === 0 ? (
        <p className="wr-allow-empty">붙는 수당이 없습니다.</p>
      ) : (
        <ul>
          {lines.map((l) => (
            <li key={l.key}>
              <span>{l.name}</span>
              <b>{formatMinutes(l.minutes)}</b>
            </li>
          ))}
        </ul>
      )}
      <p className="wr-allow-note">{note ?? '수당 배율은 회사 설정 기준으로 급여에 반영됩니다. 실제 지급은 출퇴근 기록과 승인 시간으로 정해집니다.'}</p>
    </div>
  );
}
