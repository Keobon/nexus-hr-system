// 출장(Figma 484-27187) — 국내 · 해외 출장 신청과 결과 보고 현황
// 목록 행에 목적이 없어 행마다 상세(/business-trips/{id})를 읽어 채운다
import { useMemo } from 'react';
import { Select } from 'antd';
import { useQueries, useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { getTripDetail, getTripPage, tripKey } from '../../../api/attendanceAdmin';
import { fetchAllPages } from '../../../api/employees';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { Field, ListCard, MonthField, OrgField, Person, Pill, SearchButton, SearchField, Table, matches, range, useApplied, useEmployeeNos } from './shared';

/** 승인된 출장은 날짜로 승인 · 진행 중 · 완료를 나눈다 */
function statusPill(r) {
  if (r.status === 'APPROVED') {
    const today = dayjs().format('YYYY-MM-DD');
    if (today < r.startDate) return ['승인', 'info'];
    if (today <= r.endDate) return ['진행 중', 'info'];
    return ['완료', 'ok'];
  }
  if (r.status === 'PENDING') return [r.currentStep?.stepOrder > 1 ? '검토 중' : '신청', 'warn'];
  if (r.status === 'REJECTED') return ['반려', 'alert'];
  return ['취소', 'gray'];
}

function reportCell(r) {
  if (r.reportWritten) return <Pill tone="ok">제출 완료</Pill>;
  if (r.status === 'APPROVED' && dayjs().format('YYYY-MM-DD') > r.endDate) return <Pill tone="warn">미제출</Pill>;
  if (r.status !== 'APPROVED') return <span className="at-muted">–</span>;
  return <span className="at-muted">{dayjs().format('YYYY-MM-DD') >= r.startDate ? '작성 중' : '작성 전'}</span>;
}

export default function TripTab() {
  const nos = useEmployeeNos();
  const f = useApplied({ month: dayjs().format('YYYY-MM'), tripType: 'ALL', orgUnitId: null, q: '' });
  const { month, tripType, orgUnitId, q } = f.applied;
  const from = `${month}-01`;
  const to = dayjs(from).endOf('month').format('YYYY-MM-DD');
  const data = useQuery({ queryKey: ['trips', 'admin', month, orgUnitId], queryFn: () => fetchAllPages(getTripPage, { from, to, orgUnitId: orgUnitId ?? undefined }) });
  const rows = useMemo(
    () => (data.data ?? []).filter((r) => (tripType === 'ALL' || r.tripType === tripType) && matches(q, r.employeeName, nos[r.employeeId])),
    [data.data, tripType, q, nos],
  );
  const details = useQueries({ queries: rows.map((r) => ({ queryKey: tripKey(r.id), queryFn: () => getTripDetail(r.id), staleTime: 5 * 60 * 1000 })) });
  const purposeOf = Object.fromEntries(details.map((d, i) => [rows[i]?.id, d.data?.purpose]));

  return (
    <>
      <div className="at-filters">
        <MonthField value={f.draft.month} onChange={(v) => f.set({ month: v })} />
        <Field label="국내/해외" width="7.5rem">
          <Select
            value={f.draft.tripType}
            onChange={(v) => f.set({ tripType: v })}
            options={[
              { value: 'ALL', label: '전체' },
              { value: 'DOMESTIC', label: '국내' },
              { value: 'OVERSEAS', label: '해외' },
            ]}
          />
        </Field>
        <OrgField value={f.draft.orgUnitId} onChange={(v) => f.set({ orgUnitId: v })} />
        <SearchField value={f.draft.q} onChange={(v) => f.set({ q: v })} onEnter={f.apply} />
        <SearchButton onClick={f.apply} />
      </div>

      <ListCard title={`출장 내역 · 총 ${rows.length}건`} note="출장 기간이 조회 월에 걸친 내역">
        <Table
          cols={[
            { key: 'who', label: '신청자', width: '9rem' },
            { key: 'type', label: '국내/해외', width: '5.5rem' },
            { key: 'dest', label: '출장지', width: '11rem' },
            { key: 'period', label: '기간', width: '9rem' },
            { key: 'purpose', label: '목적' },
            { key: 'st', label: '상태', width: '6rem' },
            { key: 'report', label: '결과 보고', width: '7rem' },
          ]}
          loading={data.isLoading}
          error={data.error && errorMessage(data.error)}
          empty={rows.length === 0 && '조건에 맞는 출장이 없습니다'}
        >
          {rows.map((r) => {
            const [label, tone] = statusPill(r);
            return (
              <tr key={r.id}>
                <td>
                  <Person name={r.employeeName} org={r.orgUnitName} />
                </td>
                <td>{labelOf('tripType', r.tripType)}</td>
                <td>{r.destination}</td>
                <td>{range(r.startDate, r.endDate)}</td>
                <td className="at-ellipsis" title={r.rejectReason ? `반려 사유: ${r.rejectReason}` : purposeOf[r.id]}>
                  {r.rejectReason ? `반려 · ${r.rejectReason}` : purposeOf[r.id] ?? '…'}
                </td>
                <td>
                  <Pill tone={tone}>{label}</Pill>
                </td>
                <td>{reportCell(r)}</td>
              </tr>
            );
          })}
        </Table>
      </ListCard>
    </>
  );
}
