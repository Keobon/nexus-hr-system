// 휴가 현황 — 조회만 하는 사람(경영진 · 조직장, LEAVE_MANAGE 없음)의 화면. Figma 70-3665(전사 휴가 현황 조회)
// 위 조건(부서 · 직원 · 기간 · 상태)이 두 탭(신청 내역 · 직원별 잔여량)에 같이 걸린다. 팀 범위면 제목을 "팀"으로
import { useMemo, useState } from 'react';
import { DatePicker, Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useMe } from '../../../auth/useMe';
import { getLeaveBalancePage, getLeaveRequestPage } from '../../../api/leaves';
import { fetchAllPages } from '../../../api/employees';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { PageHead } from '../parts';
import { Field, OrgField, Pill, Table } from '../attendance/shared';
import { DetailDrawer, STATUS, TONE, period, stepText } from './RequestsTab';

export default function LeaveReadView() {
  const { me } = useMe();
  const whole = (me?.permissions ?? []).some((p) => p.code === 'LEAVE_READ' && p.scope === 'ALL');
  const [orgUnitId, setOrgUnitId] = useState(null);
  const [employeeId, setEmployeeId] = useState(null);
  const [month, setMonth] = useState(dayjs().format('YYYY-MM'));
  const [status, setStatus] = useState('ALL');
  const [tab, setTab] = useState('requests');
  const [pick, setPick] = useState(null);

  const from = `${month}-01`;
  const to = dayjs(from).endOf('month').format('YYYY-MM-DD');
  const year = Number(month.slice(0, 4));
  const requests = useQuery({
    queryKey: ['leave-requests', 'read', month, orgUnitId, status],
    queryFn: () => fetchAllPages(getLeaveRequestPage, { from, to, orgUnitId: orgUnitId ?? undefined, status: status === 'ALL' ? undefined : status, sort: 'startDate,desc' }),
  });
  const balances = useQuery({ queryKey: ['leave-balances', 'admin', year, orgUnitId], queryFn: () => fetchAllPages(getLeaveBalancePage, { leaveYear: year, orgUnitId: orgUnitId ?? undefined }) });

  // 직원 선택지는 고른 부서의 잔여 현황 직원
  const people = useMemo(() => (balances.data ?? []).map((e) => ({ value: e.employeeId, label: `${e.name} · ${e.orgUnitName}` })), [balances.data]);
  const reqRows = (requests.data ?? []).filter((r) => !employeeId || r.employeeId === employeeId);
  const balRows = (balances.data ?? []).filter((e) => !employeeId || e.employeeId === employeeId).flatMap((e) => e.balances.map((b) => ({ ...e, b })));
  const multiType = new Set(balRows.map((r) => r.b.leaveTypeId)).size > 1;

  return (
    <div className="hr at lv-admin">
      <PageHead
        title={whole ? '전사 휴가 현황 조회' : '팀 휴가 현황 조회'}
        desc={`${whole ? '전사' : '우리 팀'} 휴가 신청·승인 현황과 직원별 잔여량을 조회합니다. 조회 전용 화면입니다.`}
      />

      <div className="at-filters lv-box">
        <OrgField value={orgUnitId} onChange={(v) => { setOrgUnitId(v); setEmployeeId(null); }} />
        <Field label="직원" width="11rem">
          <Select value={employeeId ?? 'ALL'} onChange={(v) => setEmployeeId(v === 'ALL' ? null : v)} options={[{ value: 'ALL', label: '전체 직원' }, ...people]} showSearch optionFilterProp="label" />
        </Field>
        <Field label="기간" width="10rem">
          <DatePicker picker="month" value={dayjs(from)} allowClear={false} format="YYYY년 M월" onChange={(v) => v && setMonth(v.format('YYYY-MM'))} />
        </Field>
        <Field label="상태" width="9rem">
          <Select value={status} onChange={setStatus} options={STATUS} disabled={tab !== 'requests'} />
        </Field>
      </div>

      <section className="at-card">
        <nav className="at-tabs lv-inner-tabs" role="tablist" aria-label="휴가 현황 탭">
          {[
            ['requests', '신청 내역'],
            ['balances', '직원별 잔여량'],
          ].map(([k, label]) => (
            <button key={k} type="button" role="tab" aria-selected={tab === k} className={tab === k ? 'is-on' : undefined} onClick={() => setTab(k)}>
              {label}
            </button>
          ))}
        </nav>

        {tab === 'requests' ? (
          <Table
            cols={[
              { key: 'who', label: '직원', width: '7rem' },
              { key: 'org', label: '부서', width: '9rem' },
              { key: 'type', label: '휴가 종류', width: '7rem' },
              { key: 'period', label: '기간', width: '13rem' },
              { key: 'days', label: '일수', width: '5rem' },
              { key: 'st', label: '상태', width: '7rem' },
              { key: 'step', label: '현재 단계' },
            ]}
            loading={requests.isLoading}
            error={requests.error && errorMessage(requests.error)}
            empty={reqRows.length === 0 && '조건에 맞는 휴가 신청이 없습니다'}
          >
            {reqRows.map((r) => (
              <tr key={r.id} className={`is-click${pick?.id === r.id ? ' is-on' : ''}`} onClick={() => setPick(r)}>
                <td className="at-strong">{r.employeeName}</td>
                <td>{r.orgUnitName}</td>
                <td>{r.leaveTypeName}</td>
                <td>{period(r.startDate, r.endDate)}</td>
                <td>{`${r.days}일`}</td>
                <td>
                  <Pill tone={TONE[r.status]}>{labelOf('leaveStatus', r.status)}</Pill>
                </td>
                <td>{stepText(r)}</td>
              </tr>
            ))}
          </Table>
        ) : (
          <Table
            cols={[
              { key: 'who', label: '직원', width: '8rem' },
              { key: 'org', label: '부서' },
              ...(multiType ? [{ key: 'type', label: '휴가 종류', width: '8rem' }] : []),
              { key: 'g', label: '부여', width: '7rem' },
              { key: 'u', label: '사용', width: '7rem' },
              { key: 'p', label: '승인대기', width: '7rem' },
              { key: 'r', label: '잔여', width: '7rem' },
            ]}
            loading={balances.isLoading}
            error={balances.error && errorMessage(balances.error)}
            empty={balRows.length === 0 && `${year}년 부여된 휴가가 없습니다`}
          >
            {balRows.map((r) => (
              <tr key={`${r.employeeId}-${r.b.leaveTypeId}`}>
                <td className="at-strong">{r.name}</td>
                <td>{r.orgUnitName}</td>
                {multiType && <td>{r.b.leaveTypeName}</td>}
                <td>{`${r.b.granted}일`}</td>
                <td>{`${r.b.used}일`}</td>
                <td>{`${r.b.pending}일`}</td>
                <td className={`lv-remain${r.b.remaining < 0 ? ' at-red' : ''}`}>{`${r.b.remaining}일`}</td>
              </tr>
            ))}
          </Table>
        )}
      </section>

      {pick && <DetailDrawer row={pick} onClose={() => setPick(null)} />}
    </div>
  );
}
