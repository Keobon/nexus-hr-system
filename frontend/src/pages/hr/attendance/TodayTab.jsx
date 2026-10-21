// 오늘 근태(Figma 484-26574) — 전사 요약 6칸 + 조직 · 직원 조건의 직원별 오늘 근태
// 서버는 오늘만 준다(/attendances/today) — 기준일은 오늘로 고정. 다른 날은 근태 조회 탭의 일별 상세에서 본다
import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { getTodayPage } from '../../../api/attendanceAdmin';
import { fetchAllPages } from '../../../api/employees';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { Field, ListCard, OrgField, Pill, SearchButton, SearchField, Table, hm, matches, useApplied, useEmployeeNos } from './shared';

const TONE = { OFFICE: 'ok', OFF_WORK: 'ok', BEFORE_WORK: 'warn', REMOTE: 'info', FIELD: 'info', BUSINESS_TRIP: 'info', ON_VACATION: 'purple', ON_LEAVE: 'gray' };
const TEXT = { OFFICE: '출근', BEFORE_WORK: '미출근' };
const WEEK = ['일', '월', '화', '수', '목', '금', '토'];

export default function TodayTab() {
  const nos = useEmployeeNos();
  const f = useApplied({ orgUnitId: null, q: '' });
  // 전사 요약은 조건과 무관 — 전체를 한 번 읽어 센다
  const all = useQuery({ queryKey: ['attendance', 'today-all'], queryFn: () => fetchAllPages(getTodayPage, {}), refetchInterval: 60_000 });
  const scoped = useQuery({
    queryKey: ['attendance', 'today-org', f.applied.orgUnitId],
    queryFn: () => fetchAllPages(getTodayPage, { orgUnitId: f.applied.orgUnitId }),
    enabled: f.applied.orgUnitId != null,
  });
  const source = f.applied.orgUnitId == null ? all : scoped;
  const rows = useMemo(() => (source.data ?? []).filter((r) => matches(f.applied.q, r.name, nos[r.employeeId])), [source.data, f.applied.q, nos]);

  const list = all.data ?? [];
  const count = (pred) => list.filter(pred).length;
  const tiles = [
    ['출근', count((r) => r.checkInAt != null)],
    ['미출근', count((r) => r.todayStatus === 'BEFORE_WORK'), 'is-warn'],
    ['재택', count((r) => r.todayStatus === 'REMOTE')],
    ['외근', count((r) => r.todayStatus === 'FIELD')],
    ['출장', count((r) => r.todayStatus === 'BUSINESS_TRIP')],
    ['휴가', count((r) => r.todayStatus === 'ON_VACATION')],
  ];
  const now = dayjs();

  return (
    <>
      <div className="at-summary">
        <p className="at-muted is-strong">{`전사 전체 현황 · ${now.format('YYYY.MM.DD')} (${WEEK[now.day()]}) ${now.format('HH:mm')} 기준`}</p>
        <div className="at-tiles">
          {tiles.map(([label, n, cls]) => (
            <div key={label} className={`at-tile${cls ? ` ${cls}` : ''}`}>{`${label} ${all.isLoading ? '–' : n}`}</div>
          ))}
        </div>
        <p className="at-muted">전사 현황은 아래 조직·직원 조회 조건과 무관합니다.</p>
      </div>

      <div className="at-filters">
        <Field label="기준일" width="11.25rem">
          <div className="at-fixed">{`${now.format('YYYY.MM.DD')} (${WEEK[now.day()]})`}</div>
        </Field>
        <OrgField value={f.draft.orgUnitId} onChange={(v) => f.set({ orgUnitId: v })} />
        <SearchField value={f.draft.q} onChange={(v) => f.set({ q: v })} onEnter={f.apply} />
        <SearchButton onClick={f.apply} />
      </div>

      <ListCard title={`직원별 오늘 근태 · 조회 결과 ${rows.length}명`} note="1분마다 새로 고칩니다">
        <Table
          cols={[
            { key: 'no', label: '사번', width: '8rem' },
            { key: 'name', label: '이름', width: '6rem' },
            { key: 'org', label: '소속', width: '10rem' },
            { key: 'st', label: '오늘 상태', width: '7rem' },
            { key: 'wt', label: '근무 형태', width: '6rem' },
            { key: 'memo', label: '장소 메모' },
            { key: 'in', label: '출근', width: '6rem' },
            { key: 'out', label: '퇴근', width: '6rem' },
          ]}
          loading={source.isLoading}
          error={source.error && errorMessage(source.error)}
          empty={rows.length === 0 && '조건에 맞는 직원이 없습니다'}
        >
          {rows.map((r) => (
            <tr key={r.employeeId}>
              <td>{nos[r.employeeId] ?? '–'}</td>
              <td>{r.name}</td>
              <td>{r.orgUnitName}</td>
              <td>
                <Pill tone={TONE[r.todayStatus]}>{TEXT[r.todayStatus] ?? labelOf('todayStatus', r.todayStatus)}</Pill>
              </td>
              <td>{r.workType ? labelOf('workType', r.workType) : '–'}</td>
              <td>{r.placeMemo || (r.todayStatus === 'BEFORE_WORK' ? '출근 기록 없음' : '–')}</td>
              <td>{hm(r.checkInAt)}</td>
              <td>{hm(r.checkOutAt)}</td>
            </tr>
          ))}
        </Table>
      </ListCard>
    </>
  );
}
