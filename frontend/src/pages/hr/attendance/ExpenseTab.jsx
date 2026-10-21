// 출장 경비(Figma 484-27503) — 출장별 경비 청구 · 정산 반영 월
// 서버 목록에는 월 조건이 없어 청구일 기준으로 화면에서 거른다. 출장 목적 · 기간은 출장 상세에서 채운다
// Figma의 "보완 요청 · 임시 저장" 상태는 서버에 없다
import { useMemo } from 'react';
import { Select } from 'antd';
import { useQueries, useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { getExpensePage, getTripDetail, tripKey } from '../../../api/attendanceAdmin';
import { fetchAllPages } from '../../../api/employees';
import { errorMessage } from '../../../constants/errors';
import { Field, ListCard, MonthField, OrgField, Person, Pill, SearchButton, SearchField, Table, matches, range, useApplied, useEmployeeNos } from './shared';

const STATUS = [
  { value: 'ALL', label: '전체' },
  { value: 'PENDING', label: '검토 중' },
  { value: 'APPROVED', label: '승인(정산 전)' },
  { value: 'SETTLED', label: '정산 완료' },
  { value: 'REJECTED', label: '반려' },
];
const paramsOf = (s) =>
  s === 'SETTLED' ? { settled: true } : s === 'APPROVED' ? { status: 'APPROVED', settled: false } : s === 'ALL' ? {} : { status: s };

function statusPill(r) {
  if (r.settledPayMonth) return ['정산 완료', 'ok'];
  if (r.status === 'APPROVED') return ['승인', 'info'];
  if (r.status === 'PENDING') return ['검토 중', 'info'];
  if (r.status === 'REJECTED') return ['반려', 'alert'];
  return ['취소', 'gray'];
}
const won = (n) => `${Number(n).toLocaleString('ko-KR')}원`;
const payMonth = (m) => (m ? `${m.slice(0, 4)}년 ${Number(m.slice(5, 7))}월` : '미정');

export default function ExpenseTab() {
  const nos = useEmployeeNos();
  const f = useApplied({ month: dayjs().format('YYYY-MM'), orgUnitId: null, q: '', status: 'ALL' });
  const { month, orgUnitId, q, status } = f.applied;
  const data = useQuery({
    queryKey: ['expenses', 'admin', orgUnitId, status],
    queryFn: () => fetchAllPages(getExpensePage, { orgUnitId: orgUnitId ?? undefined, ...paramsOf(status) }),
  });
  const rows = useMemo(
    () => (data.data ?? []).filter((r) => dayjs(r.createdAt).format('YYYY-MM') === month && matches(q, r.employeeName, nos[r.employeeId])),
    [data.data, month, q, nos],
  );
  const trips = useQueries({ queries: rows.map((r) => ({ queryKey: tripKey(r.businessTripId), queryFn: () => getTripDetail(r.businessTripId), staleTime: 5 * 60 * 1000 })) });
  const tripOf = Object.fromEntries(trips.map((t, i) => [rows[i]?.id, t.data]));

  return (
    <>
      <div className="at-filters">
        <MonthField value={f.draft.month} onChange={(v) => f.set({ month: v })} />
        <OrgField value={f.draft.orgUnitId} onChange={(v) => f.set({ orgUnitId: v })} />
        <SearchField value={f.draft.q} onChange={(v) => f.set({ q: v })} onEnter={f.apply} />
        <Field label="상태" width="9.375rem">
          <Select value={f.draft.status} onChange={(v) => f.set({ status: v })} options={STATUS} />
        </Field>
        <SearchButton onClick={f.apply} />
      </div>

      <ListCard title={`출장 경비 · 총 ${rows.length}건`} note="청구일이 조회 월인 내역 · 미완료 출장의 경비는 현재 제출 금액입니다.">
        <Table
          cols={[
            { key: 'who', label: '신청자', width: '9rem' },
            { key: 'trip', label: '출장' },
            { key: 'sum', label: '경비 합계', width: '9rem' },
            { key: 'lines', label: '경비 항목', width: '7rem' },
            { key: 'st', label: '상태', width: '7rem' },
            { key: 'month', label: '정산 반영 월', width: '8rem' },
          ]}
          loading={data.isLoading}
          error={data.error && errorMessage(data.error)}
          empty={rows.length === 0 && '조건에 맞는 경비 청구가 없습니다'}
        >
          {rows.map((r) => {
            const [label, tone] = statusPill(r);
            const t = tripOf[r.id];
            return (
              <tr key={r.id}>
                <td>
                  <Person name={r.employeeName} org={r.orgUnitName} />
                </td>
                <td>
                  <div className="at-person">
                    <strong>{t?.purpose ? `${r.destination} · ${t.purpose}` : r.destination}</strong>
                    <span>{t ? range(t.startDate, t.endDate) : ' '}</span>
                  </div>
                </td>
                <td className="at-strong">{won(r.totalAmount)}</td>
                <td>
                  <Pill tone="plain">{`${r.lineCount}건`}</Pill>
                </td>
                <td title={r.rejectReason ? `반려 사유: ${r.rejectReason}` : undefined}>
                  <Pill tone={tone}>{label}</Pill>
                </td>
                <td>{payMonth(r.settledPayMonth)}</td>
              </tr>
            );
          })}
        </Table>
      </ListCard>
    </>
  );
}
