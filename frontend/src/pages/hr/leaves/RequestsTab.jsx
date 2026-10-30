// 휴가 내역(Figma 70-3465 · 487-17240) — 기간 · 조직 · 상태 · 종류 조건의 휴가 신청 목록, 행을 누르면 오른쪽에 상세(신청 요약 · 승인 타임라인 · 반려 사유)
import { useMemo, useState } from 'react';
import { DatePicker, Drawer, Select, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { allLeaveTypesKey, getAllLeaveTypes, getLeaveRequest, getLeaveRequestPage, leaveRequestKey } from '../../../api/leaves';
import { fetchAllPages } from '../../../api/employees';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { Field, ListCard, OrgField, Pill, SearchButton, Table, useApplied } from '../attendance/shared';

export const TONE = { PENDING: 'warn', APPROVED: 'ok', REJECTED: 'alert', CANCEL_REQUESTED: 'warn', CANCELLED: 'gray' };
export const STATUS = [{ value: 'ALL', label: '전체 상태' }, ...['PENDING', 'APPROVED', 'REJECTED', 'CANCEL_REQUESTED', 'CANCELLED'].map((v) => ({ value: v, label: labelOf('leaveStatus', v) }))];
export const stepText = (r) => (r.currentStep && ['PENDING', 'CANCEL_REQUESTED'].includes(r.status) ? `${r.currentStep.stepOrder}/${r.currentStep.totalSteps} · ${r.currentStep.approverName} 대기` : '–');
export const period = (a, b) => (a === b ? a : `${a} ~ ${b}`);

export default function RequestsTab() {
  const thisMonth = dayjs();
  const f = useApplied({ from: thisMonth.startOf('month').format('YYYY-MM-DD'), to: thisMonth.endOf('month').format('YYYY-MM-DD'), orgUnitId: null, status: 'ALL', leaveTypeId: 'ALL' });
  const { from, to, orgUnitId, status, leaveTypeId } = f.applied;
  const types = useQuery({ queryKey: allLeaveTypesKey, queryFn: getAllLeaveTypes });
  const data = useQuery({
    queryKey: ['leave-requests', 'admin', f.applied],
    queryFn: () =>
      fetchAllPages(getLeaveRequestPage, {
        from,
        to,
        orgUnitId: orgUnitId ?? undefined,
        status: status === 'ALL' ? undefined : status,
        leaveTypeId: leaveTypeId === 'ALL' ? undefined : leaveTypeId,
        sort: 'startDate,desc',
      }),
  });
  const rows = useMemo(() => data.data ?? [], [data.data]);
  const [pick, setPick] = useState(null);

  return (
    <>
      <div className="at-filters lv-box">
        <Field label="기간" width="16rem">
          <DatePicker.RangePicker
            value={[dayjs(f.draft.from), dayjs(f.draft.to)]}
            allowClear={false}
            format="YYYY-MM-DD"
            onChange={(v) => v && f.set({ from: v[0].format('YYYY-MM-DD'), to: v[1].format('YYYY-MM-DD') })}
          />
        </Field>
        <OrgField value={f.draft.orgUnitId} onChange={(v) => f.set({ orgUnitId: v })} />
        <Field label="상태" width="9rem">
          <Select value={f.draft.status} onChange={(v) => f.set({ status: v })} options={STATUS} />
        </Field>
        <Field label="휴가 종류" width="9rem">
          <Select value={f.draft.leaveTypeId} onChange={(v) => f.set({ leaveTypeId: v })} options={[{ value: 'ALL', label: '전체 종류' }, ...(types.data ?? []).map((t) => ({ value: t.id, label: t.name }))]} />
        </Field>
        <SearchButton onClick={f.apply} />
      </div>

      <ListCard title={`휴가 신청 · 총 ${rows.length}건`} note="기간이 조회 기간에 걸친 휴가 · 행을 누르면 상세">
        <Table
          cols={[
            { key: 'who', label: '신청자', width: '7rem' },
            { key: 'org', label: '소속', width: '9rem' },
            { key: 'type', label: '휴가 종류', width: '7rem' },
            { key: 'period', label: '기간', width: '13rem' },
            { key: 'days', label: '일수', width: '5rem' },
            { key: 'st', label: '상태', width: '7rem' },
            { key: 'step', label: '현재 단계' },
          ]}
          loading={data.isLoading}
          error={data.error && errorMessage(data.error)}
          empty={rows.length === 0 && '조건에 맞는 휴가 신청이 없습니다'}
        >
          {rows.map((r) => (
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
      </ListCard>

      {pick && <DetailDrawer row={pick} onClose={() => setPick(null)} />}
    </>
  );
}

/* ───── 오른쪽 상세 — 신청 요약 · 승인 타임라인 · 반려 사유 ───── */
export function DetailDrawer({ row, onClose }) {
  const detail = useQuery({ queryKey: leaveRequestKey(row.id), queryFn: () => getLeaveRequest(row.id) });
  const d = detail.data;
  const steps = (d?.approvalSteps ?? []).filter((s) => s.status !== 'SKIPPED');
  const rejected = steps.find((s) => s.status === 'REJECTED');

  return (
    <Drawer open onClose={onClose} size="default" className="lv-drawer" closable={false} title={null}>
      <div className="lv-dr-head">
        <strong>{`${labelOf('leaveStatus', row.status)} 상세`}</strong>
        <span>{`${row.employeeName} · ${row.leaveTypeName} (${period(row.startDate, row.endDate)})`}</span>
      </div>
      <h4 className="lv-dr-title">신청 요약</h4>
      <div className="lv-dr-sum">
        {[
          ['신청자', row.employeeName],
          ['종류', row.leaveTypeName],
          ['기간', period(row.startDate, row.endDate)],
          ['일수', `${row.days}일`],
          ['사유', row.reason || '–'],
          ...(d?.cancelReason ? [['취소 사유', d.cancelReason]] : []),
        ].map(([k, v]) => (
          <div key={k}>
            <span>{k}</span>
            <strong>{v}</strong>
          </div>
        ))}
      </div>

      <h4 className="lv-dr-title">승인 타임라인</h4>
      {detail.isLoading ? (
        <Spin />
      ) : detail.error ? (
        <p className="at-muted">{errorMessage(detail.error)}</p>
      ) : steps.length === 0 ? (
        <p className="at-muted">결재 단계가 없습니다(자동 승인).</p>
      ) : (
        <ol className="lv-timeline">
          {steps.map((s) => (
            <li key={s.stepId} className={`is-${s.status}`}>
              <span className="lv-dot">{s.stepOrder}</span>
              <div>
                <strong>{`${s.stepOrder}단계 ${s.approverName ?? '미지정'} ${labelOf('approvalStepStatus', s.status)}`}</strong>
                <span>{s.actedAt ? s.actedAt.slice(0, 10) : s.status === 'PENDING' ? '현재 단계' : '대기'}</span>
              </div>
            </li>
          ))}
        </ol>
      )}

      {(rejected?.comment || row.rejectReason) && (
        <div className="lv-reject">
          <strong>반려 사유</strong>
          <p>{rejected?.comment || row.rejectReason}</p>
        </div>
      )}
    </Drawer>
  );
}
