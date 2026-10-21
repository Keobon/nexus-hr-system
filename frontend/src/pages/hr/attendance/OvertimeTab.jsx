// 연장근무(Figma 484-26894) — 신청 목록, 요청 → 인정 시간 · 휴일 · 야간 · 상태
import { useMemo } from 'react';
import { Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { getOvertimePage } from '../../../api/attendanceAdmin';
import { fetchAllPages } from '../../../api/employees';
import { errorMessage } from '../../../constants/errors';
import { Field, ListCard, MonthField, OrgField, Person, Pill, SearchButton, SearchField, Table, dotDay, hhmm, hm, matches, useApplied, useEmployeeNos } from './shared';

export const STATUS_OPTIONS = [
  { value: 'ALL', label: '전체' },
  { value: 'PENDING', label: '승인 대기' },
  { value: 'APPROVED', label: '승인' },
  { value: 'REJECTED', label: '반려' },
  { value: 'CANCELLED', label: '취소' },
];

function statusPill(r) {
  if (r.status === 'APPROVED') return r.approvedMinutes != null && r.approvedMinutes < r.requestedMinutes ? ['일부 인정', 'warn'] : ['승인', 'ok'];
  if (r.status === 'PENDING') return [r.currentStep?.stepOrder > 1 ? '검토 중' : '신청', 'info'];
  if (r.status === 'REJECTED') return ['반려', 'alert'];
  return ['취소', 'gray'];
}

export default function OvertimeTab() {
  const nos = useEmployeeNos();
  const f = useApplied({ month: dayjs().format('YYYY-MM'), orgUnitId: null, q: '', status: 'ALL' });
  const { month, orgUnitId, q, status } = f.applied;
  const data = useQuery({
    queryKey: ['overtime', 'admin', month, orgUnitId, status],
    queryFn: () => fetchAllPages(getOvertimePage, { month, orgUnitId: orgUnitId ?? undefined, status: status === 'ALL' ? undefined : status }),
  });
  const rows = useMemo(() => (data.data ?? []).filter((r) => matches(q, r.employeeName, nos[r.employeeId])), [data.data, q, nos]);

  return (
    <>
      <div className="at-filters">
        <MonthField value={f.draft.month} onChange={(v) => f.set({ month: v })} />
        <OrgField value={f.draft.orgUnitId} onChange={(v) => f.set({ orgUnitId: v })} />
        <SearchField value={f.draft.q} onChange={(v) => f.set({ q: v })} onEnter={f.apply} />
        <Field label="상태" width="9.375rem">
          <Select value={f.draft.status} onChange={(v) => f.set({ status: v })} options={STATUS_OPTIONS} />
        </Field>
        <SearchButton onClick={f.apply} />
      </div>

      <ListCard title={`연장근무 신청 · 총 ${rows.length}건`} note="요청 → 인정 시간 · 미확정은 – 표시">
        <Table
          cols={[
            { key: 'who', label: '신청자', width: '10rem' },
            { key: 'date', label: '근무일', width: '9rem' },
            { key: 'plan', label: '예정 시간', width: '9rem' },
            { key: 'min', label: '요청 → 인정', width: '9rem' },
            { key: 'hn', label: '휴일·야간', width: '8rem' },
            { key: 'reason', label: '사유' },
            { key: 'st', label: '상태', width: '7rem' },
          ]}
          loading={data.isLoading}
          error={data.error && errorMessage(data.error)}
          empty={rows.length === 0 && '조건에 맞는 연장근무 신청이 없습니다'}
        >
          {rows.map((r) => {
            const [label, tone] = statusPill(r);
            return (
              <tr key={r.id}>
                <td>
                  <Person name={r.employeeName} org={r.orgUnitName} />
                </td>
                <td>{dotDay(r.workDate)}</td>
                <td>{`${hm(r.plannedStart)}–${hm(r.plannedEnd)}`}</td>
                <td>{`${hhmm(r.requestedMinutes)} → ${r.approvedMinutes == null ? '–' : hhmm(r.approvedMinutes)}`}</td>
                <td>{r.holiday ? `휴일 ${hhmm(r.requestedMinutes)}` : r.nightMinutes ? `야간 ${hhmm(r.nightMinutes)}` : '해당 없음'}</td>
                <td className="at-ellipsis" title={r.rejectReason ? `반려 사유: ${r.rejectReason}` : r.reason}>
                  {r.rejectReason ? `반려 · ${r.rejectReason}` : r.reason}
                </td>
                <td>
                  <Pill tone={tone}>{label}</Pill>
                </td>
              </tr>
            );
          })}
        </Table>
      </ListCard>
    </>
  );
}
