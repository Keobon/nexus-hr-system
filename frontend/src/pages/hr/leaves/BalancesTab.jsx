// 잔여 현황(Figma 487-16827) — 연도 · 조직별 직원 잔여(종류마다 한 줄). 잔여 = 부여 − 사용 − 승인대기
import { useMemo, useState } from 'react';
import { Pagination, Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { getLeaveBalancePage } from '../../../api/leaves';
import { fetchAllPages } from '../../../api/employees';
import { errorMessage } from '../../../constants/errors';
import { Field, OrgField, Table } from '../attendance/shared';

const PAGE = 10;
export const yearChoices = () => {
  const y = dayjs().year();
  return [y + 1, y, y - 1, y - 2].map((v) => ({ value: v, label: String(v) }));
};

export default function BalancesTab() {
  const [year, setYear] = useState(dayjs().year());
  const [orgUnitId, setOrgUnitId] = useState(null);
  const [page, setPage] = useState(1);
  const data = useQuery({ queryKey: ['leave-balances', 'admin', year, orgUnitId], queryFn: () => fetchAllPages(getLeaveBalancePage, { leaveYear: year, orgUnitId: orgUnitId ?? undefined }) });
  const rows = useMemo(() => (data.data ?? []).flatMap((e) => e.balances.map((b) => ({ ...e, b }))), [data.data]);
  const shown = rows.slice((page - 1) * PAGE, page * PAGE);
  const people = data.data?.length ?? 0;

  return (
    <>
      <div className="at-filters lv-box">
        <Field label="휴가 연도" width="8rem">
          <Select value={year} onChange={(v) => { setYear(v); setPage(1); }} options={yearChoices()} />
        </Field>
        <OrgField value={orgUnitId} onChange={(v) => { setOrgUnitId(v); setPage(1); }} />
      </div>

      <section className="at-card">
        <Table
          cols={[
            { key: 'no', label: '사번', width: '9rem' },
            { key: 'name', label: '이름', width: '6rem' },
            { key: 'org', label: '소속' },
            { key: 'type', label: '휴가 종류', width: '9rem' },
            { key: 'g', label: '부여', width: '6rem', align: 'right' },
            { key: 'u', label: '사용', width: '6rem', align: 'right' },
            { key: 'p', label: '승인대기', width: '6rem', align: 'right' },
            { key: 'r', label: '잔여', width: '6rem', align: 'right' },
          ]}
          loading={data.isLoading}
          error={data.error && errorMessage(data.error)}
          empty={rows.length === 0 && `${year}년 부여된 휴가가 없습니다`}
        >
          {shown.map((r) => (
            <tr key={`${r.employeeId}-${r.b.leaveTypeId}`}>
              <td>{r.employeeNo}</td>
              <td className="at-strong">{r.name}</td>
              <td>{r.orgUnitName}</td>
              <td>{r.b.leaveTypeName}</td>
              <td className="is-right">{r.b.granted}</td>
              <td className="is-right">{r.b.used}</td>
              <td className="is-right">{r.b.pending}</td>
              <td className={`is-right at-strong${r.b.remaining < 0 ? ' at-red' : ''}`}>{r.b.remaining}</td>
            </tr>
          ))}
        </Table>
        <div className="lv-pager">
          <span className="at-muted">{rows.length ? `직원 ${people}명 · ${rows.length}줄 중 ${(page - 1) * PAGE + 1}–${Math.min(page * PAGE, rows.length)}` : ''}</span>
          <Pagination current={page} pageSize={PAGE} total={rows.length} onChange={setPage} showSizeChanger={false} hideOnSinglePage size="small" />
        </div>
      </section>
    </>
  );
}
