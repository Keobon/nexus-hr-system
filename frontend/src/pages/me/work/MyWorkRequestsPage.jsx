import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { App, DatePicker, Form, Input, InputNumber, Modal, Radio, Result, Select, Spin, TimePicker } from 'antd';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  getMyAttendances,
  getMyOvertimes,
  getMyTrips,
  getTrip,
  myAttendancesKey,
  myOvertimesKey,
  myTripsKey,
} from '../../../api/attendance';
import { dashboardKey } from '../../../api/dashboard';
import {
  expenseKey,
  expenseRoot,
  expenseTypesKey,
  getExpense,
  getExpenseTypes,
  getMyExpenses,
  getOvertime,
  myExpensesKey,
  overtimeKey,
  overtimeRoot,
  reportTrip,
  requestExpense,
  requestOvertime,
  requestTrip,
  tripKey,
  tripRoot,
  withdrawExpense,
  withdrawOvertime,
  withdrawTrip,
} from '../../../api/workRequests';
import ApprovalSteps from '../../../components/common/ApprovalSteps';
import FileUpload from '../../../components/common/FileUpload';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { LABELS } from '../../../constants/labels';
import { formatMinutes, formatMoney } from '../../../utils/format';
import { WEEK_OT_LIMIT, sumMinutes, weekDates } from '../attendance/logic';
import iconChevron from '../../../assets/figma/icon-chevron.svg';
import './work.css';

const dot = (d) => (d ? d.replaceAll('-', '.') : '-');
const hm = (iso) => (iso ? iso.slice(11, 16) : '-');
const today = () => dayjs().format('YYYY-MM-DD');

// 배지 색은 Figma(결재 대기 주황, 승인 파랑, 완료 초록), 글자는 부록 A 라벨
const BADGE = {
  PENDING: 'is-amber',
  APPROVED: 'is-blue',
  REJECTED: 'is-red',
  CANCELLED: 'is-grey',
  DONE: 'is-green',
};
const badgeText = (status) => (status === 'DONE' ? '완료' : (LABELS.requestStatus[status]?.label ?? status));
const tripTypeText = (t) => LABELS.tripType?.[t]?.label ?? (t === 'OVERSEAS' ? '해외' : '국내');

/** 연장 예정 시각 — 근무일이 아닌 날로 끝나면 "다음 날 01:00"(프론트 가이드 3.2) */
function otRange(row) {
  const end = row.plannedEnd.slice(0, 10) !== row.workDate ? `다음 날 ${hm(row.plannedEnd)}` : hm(row.plannedEnd);
  return `${hm(row.plannedStart)} - ${end}`;
}
/** 연장 종류 — 서버 값 holiday · nightMinutes 로 "(휴일)" · "(야간)"(노션 수정사항 102, PR #67) */
function otKind(row) {
  const kinds = [row.holiday && '휴일', row.nightMinutes > 0 && '야간'].filter(Boolean);
  return kinds.length ? ` (${kinds.join(' · ')})` : '';
}

/**
 * 요청 · 승인 · 인정 시간. 승인된 건은 그날 근태로 "승인 2시간 · 인정 1시간 40분"(노션 수정사항 98, 백엔드 팀원 답 10/12):
 * 인정 = overtimeMinutes + holidayMinutes + holidayOvertimeMinutes(휴일은 overtimeMinutes 가 0), 승인 = approvedOvertimeMinutes.
 * 승인 전이고 승인이 요청보다 적으면 "2시간 → 1시간 30분 승인"
 */
function otMinutes(row, day) {
  if (row.status === 'APPROVED' && row.approvedMinutes != null) {
    const approved = day?.approvedOvertimeMinutes ?? row.approvedMinutes;
    const recognized = (day?.overtimeMinutes ?? 0) + (day?.holidayMinutes ?? 0) + (day?.holidayOvertimeMinutes ?? 0);
    return `승인 ${formatMinutes(approved)} · ${day?.checkOutAt ? `인정 ${formatMinutes(recognized)}` : '근무 후 인정 시간 확정'}`;
  }
  if (row.approvedMinutes != null && row.approvedMinutes < row.requestedMinutes) {
    return `${formatMinutes(row.requestedMinutes)} → ${formatMinutes(row.approvedMinutes)} 승인`;
  }
  return formatMinutes(row.approvedMinutes ?? row.requestedMinutes);
}

/** 출장 버튼 규칙(프론트 가이드 3.2) — 승인완료이고 종료일부터 결과 보고, 시작일부터 경비 청구(진행 중 청구가 없을 때) */
const canReport = (trip) => trip.status === 'APPROVED' && trip.endDate <= today();
const canClaim = (trip) =>
  trip.status === 'APPROVED' && trip.startDate <= today() && [null, undefined, 'REJECTED', 'CANCELLED'].includes(trip.expenseClaimStatus);

/** 목록 행 하나 — 연장근무 · 출장 · 결과 보고 · 경비 청구를 한 목록에 */
function buildItems({ overtimes, trips, expenses, month, dayOf }) {
  const from = month.startOf('month').format('YYYY-MM-DD');
  const to = month.endOf('month').format('YYYY-MM-DD');
  const inMonth = trips.filter((t) => t.endDate >= from && t.startDate <= to);
  return [
    ...overtimes.map((r) => ({
      key: `ot-${r.id}`,
      kind: 'overtime',
      id: r.id,
      title: `연장 근무 신청${otKind(r)}`,
      sub: `${dot(r.workDate)} · ${otRange(r)} · ${otMinutes(r, dayOf(r.workDate))}`,
      day: dayOf(r.workDate),
      status: r.status,
      date: r.workDate,
      row: r,
    })),
    ...inMonth.map((r) => ({
      key: `trip-${r.id}`,
      kind: 'trip',
      id: r.id,
      title: `출장 신청 (${tripTypeText(r.tripType)})`,
      sub: `${dot(r.startDate)} - ${dot(r.endDate)} · ${r.destination}`,
      status: r.status,
      date: r.startDate,
      row: r,
    })),
    ...inMonth
      .filter((r) => r.reportWritten)
      .map((r) => ({
        key: `report-${r.id}`,
        kind: 'report',
        id: r.id,
        title: '출장 복귀 보고서',
        sub: `${dot(r.endDate)} · ${r.destination}`,
        status: 'DONE',
        date: r.endDate,
        row: r,
      })),
    ...expenses
      .filter((r) => r.createdAt.slice(0, 7) === month.format('YYYY-MM'))
      .map((r) => ({
        key: `exp-${r.id}`,
        kind: 'expense',
        id: r.id,
        title: '출장 경비 청구',
        sub: `${r.destination} · ${formatMoney(r.totalAmount)} · ${r.lineCount}건`,
        status: r.status,
        date: r.createdAt.slice(0, 10),
        row: r,
      })),
  ].sort((a, b) => b.date.localeCompare(a.date));
}

/** 신청 · 철회 뒤 같이 새로고침할 목록 */
function useRefresh() {
  const queryClient = useQueryClient();
  return () => {
    [overtimeRoot, tripRoot, expenseRoot, dashboardKey].forEach((queryKey) => queryClient.invalidateQueries({ queryKey }));
  };
}

/* ───── 상세 정보 ───── */
function DetailCard({ item, onOpen }) {
  const notify = useNotify();
  const refresh = useRefresh();
  const { modal } = App.useApp();
  const kind = item?.kind;
  const id = item?.id;
  const q = useQuery({
    queryKey: kind === 'overtime' ? overtimeKey(id) : kind === 'expense' ? expenseKey(id) : tripKey(id),
    queryFn: () => (kind === 'overtime' ? getOvertime(id) : kind === 'expense' ? getExpense(id) : getTrip(id)),
    enabled: !!item,
  });
  const withdraw = useMutation({
    mutationFn: () => (kind === 'overtime' ? withdrawOvertime(id) : kind === 'expense' ? withdrawExpense(id) : withdrawTrip(id)),
    onSuccess: () => {
      notify.success('신청을 취소했습니다');
      refresh();
    },
    onError: (err) => notify.error(err),
  });

  if (!item) {
    return (
      <section className="wr-card wr-detail">
        <h2>상세 정보</h2>
        <p className="wr-empty">왼쪽 목록에서 신청을 고르면 내용이 보입니다.</p>
      </section>
    );
  }
  const d = q.data;
  const row = item.row;
  const fields = [];
  if (kind === 'overtime') {
    fields.push(['신청 유형', `연장 근무${otKind(row)}`]);
    fields.push(['신청 시간', `${dot(row.workDate)} ${otRange(row)}`]);
    fields.push([row.approvedMinutes != null ? '승인 · 인정 시간' : '요청 시간', otMinutes(row, item.day)]);
    fields.push(['상세 사유', row.reason, true]);
  } else if (kind === 'trip') {
    fields.push(['신청 유형', `출장 (${tripTypeText(row.tripType)})`]);
    fields.push(['출장 기간', `${dot(row.startDate)} - ${dot(row.endDate)} · ${row.destination}`]);
    fields.push(['출장 목적', d?.purpose ?? '…', true]);
    if (row.estimatedCost != null) fields.push(['예상 경비', formatMoney(row.estimatedCost)]);
    if (d?.expenseClaim) {
      const c = d.expenseClaim;
      fields.push([
        '경비 청구',
        `${formatMoney(c.totalAmount)} · ${badgeText(c.status)}${c.settledPayMonth ? ` · ${Number(c.settledPayMonth.slice(5))}월 급여에 반영됨` : ''}`,
      ]);
    }
  } else if (kind === 'report') {
    fields.push(['신청 유형', '출장 복귀 보고서']);
    fields.push(['출장', `${dot(row.startDate)} - ${dot(row.endDate)} · ${row.destination}`]);
    fields.push(['결과 보고', d?.reportText ?? '…', true]);
  } else {
    fields.push(['신청 유형', '출장 경비 청구']);
    fields.push(['출장', d ? `${dot(d.tripStartDate)} - ${dot(d.tripEndDate)} · ${d.destination}` : row.destination]);
    fields.push([
      '청구 금액',
      `${formatMoney(row.totalAmount)}${row.settledPayMonth ? ` · ${Number(row.settledPayMonth.slice(5))}월 급여에 반영됨` : ''}`,
    ]);
    (d?.lines ?? []).forEach((l) =>
      fields.push([`${l.expenseTypeName} · ${dot(l.usedDate)}`, `${formatMoney(l.amount)}${l.description ? ` · ${l.description}` : ''}`]),
    );
  }

  const isPending = item.status === 'PENDING';
  const trip = kind === 'trip' || kind === 'report' ? row : null;
  // 결재 대기 신청서 수정 = 철회하고 같은 내용으로 새 신청서를 연다(노션 수정사항 97 — 휴가 93과 같은 방식)
  const confirmEdit = () =>
    modal.confirm({
      title: '신청서를 수정할까요?',
      content: '지금 신청을 철회하고 같은 내용으로 새 신청서를 엽니다. 고친 뒤 신청하면 결재가 처음부터 다시 진행됩니다.',
      okText: '철회하고 수정',
      cancelText: '닫기',
      onOk: async () => {
        await withdraw.mutateAsync();
        if (kind === 'overtime') {
          onOpen({
            type: 'overtime',
            initial: {
              workDate: dayjs(row.workDate),
              plannedStart: dayjs(`2000-01-01T${hm(row.plannedStart)}`),
              plannedEnd: dayjs(`2000-01-01T${hm(row.plannedEnd)}`),
              reason: row.reason,
            },
          });
        } else {
          onOpen({
            type: 'trip',
            initial: {
              tripType: row.tripType,
              destination: row.destination,
              purpose: d?.purpose ?? '',
              period: [dayjs(row.startDate), dayjs(row.endDate)],
              estimatedCost: row.estimatedCost ?? undefined,
            },
          });
        }
      },
    });
  const confirmWithdraw = () =>
    modal.confirm({
      title: '신청을 취소할까요?',
      content: '결재 대기 중인 신청을 철회합니다.',
      okText: '신청 취소',
      okButtonProps: { danger: true },
      cancelText: '닫기',
      onOk: () => withdraw.mutateAsync(),
    });

  return (
    <section className="wr-card wr-detail">
      <h2>상세 정보</h2>
      <div className="wr-detail-body">
        {fields.map(([label, value, text]) => (
          <div key={label} className="wr-item">
            <span>{label}</span>
            <p className={text ? 'is-text' : undefined}>{value || '-'}</p>
          </div>
        ))}
        {kind !== 'report' && (
          <div className="wr-item">
            <span>결재 진행</span>
            {q.isLoading ? <Spin size="small" /> : <ApprovalSteps steps={d?.approvalSteps ?? []} showMinutes={kind === 'overtime'} />}
          </div>
        )}
      </div>
      <div className="wr-divider" />
      <div className="wr-buttons">
        {isPending && kind !== 'expense' && (
          <button type="button" className="wr-btn is-block" disabled={withdraw.isPending} onClick={confirmEdit}>
            신청서 수정
          </button>
        )}
        {trip && canReport(trip) && (
          <button type="button" className="wr-btn is-block" onClick={() => onOpen({ type: 'report', tripId: trip.id })}>
            {trip.reportWritten ? '결과 보고 수정' : '결과 보고 작성'}
          </button>
        )}
        {kind === 'trip' && canClaim(trip) && (
          <button type="button" className="wr-btn is-block is-line" onClick={() => onOpen({ type: 'expense', tripId: trip.id })}>
            경비 청구
          </button>
        )}
        {isPending && (
          <button type="button" className="wr-btn is-block is-line" disabled={withdraw.isPending} onClick={confirmWithdraw}>
            신청 취소
          </button>
        )}
      </div>
    </section>
  );
}

/* ───── 신청서 모달 ───── */
function useSubmit(form, mutationFn, done, onClose, serverMessageCodes = []) {
  const notify = useNotify();
  const refresh = useRefresh();
  return useMutation({
    mutationFn,
    onSuccess: (data) => {
      notify.success(done, data);
      refresh();
      onClose();
    },
    onError: (err) => {
      if (applyFieldErrors(form, err)) return;
      // 연장근무의 ATT_ON_LEAVE_OR_TRIP 은 5장 문구 대신 서버 메시지 그대로(프론트 가이드 3.2)
      if (serverMessageCodes.includes(err?.code) && err.message) notify.error({ message: err.message });
      else notify.error(err);
    },
  });
}

function OvertimeForm({ onClose, initial }) {
  const [form] = Form.useForm();
  const start = Form.useWatch('plannedStart', form);
  const end = Form.useWatch('plannedEnd', form);
  const submit = useSubmit(
    form,
    (v) =>
      requestOvertime({
        workDate: v.workDate.format('YYYY-MM-DD'),
        plannedStart: v.plannedStart.format('HH:mm'),
        plannedEnd: v.plannedEnd.format('HH:mm'),
        reason: v.reason.trim(),
      }),
    '연장 근무를 신청했습니다',
    onClose,
    ['ATT_ON_LEAVE_OR_TRIP'],
  );
  let hint = '예정 종료가 시작보다 이르면 다음 날로 봅니다.';
  if (start && end) {
    const s = start.hour() * 60 + start.minute();
    let e = end.hour() * 60 + end.minute();
    const nextDay = e <= s;
    if (nextDay) e += 24 * 60;
    hint = `${nextDay ? `다음 날 ${end.format('HH:mm')}까지 · ` : ''}요청 시간 ${formatMinutes(e - s)}`;
  }
  return (
    <Form
      form={form}
      layout="vertical"
      className="wr-form"
      requiredMark={false}
      initialValues={initial}
      onFinish={(v) => submit.mutate(v)}
      id="wr-form"
    >
      <Form.Item name="workDate" label="근무 일자" rules={[{ required: true, message: '근무 일자를 고르세요' }]}>
        <DatePicker style={{ width: '100%' }} format="YYYY.MM.DD (dd)" />
      </Form.Item>
      <div style={{ display: 'flex', gap: 12 }}>
        <Form.Item name="plannedStart" label="예정 시작" style={{ flex: 1 }} rules={[{ required: true, message: '시작 시각을 고르세요' }]}>
          <TimePicker format="HH:mm" minuteStep={10} style={{ width: '100%' }} needConfirm={false} />
        </Form.Item>
        <Form.Item name="plannedEnd" label="예정 종료" style={{ flex: 1 }} rules={[{ required: true, message: '종료 시각을 고르세요' }]}>
          <TimePicker format="HH:mm" minuteStep={10} style={{ width: '100%' }} needConfirm={false} />
        </Form.Item>
      </div>
      <p className="wr-hint">{hint}</p>
      <Form.Item name="reason" label="업무 사유" rules={[{ required: true, whitespace: true, message: '사유를 입력하세요' }]}>
        <Input.TextArea rows={3} maxLength={255} showCount placeholder="예: 프로젝트 마감 대비 야간 작업" />
      </Form.Item>
      <SubmitRow onClose={onClose} loading={submit.isPending} />
    </Form>
  );
}

function TripForm({ onClose, initial }) {
  const [form] = Form.useForm();
  const submit = useSubmit(
    form,
    (v) =>
      requestTrip({
        tripType: v.tripType,
        destination: v.destination.trim(),
        purpose: v.purpose.trim(),
        startDate: v.period[0].format('YYYY-MM-DD'),
        endDate: v.period[1].format('YYYY-MM-DD'),
        estimatedCost: v.estimatedCost ?? null,
      }),
    '출장을 신청했습니다',
    onClose,
  );
  return (
    <Form
      form={form}
      layout="vertical"
      className="wr-form"
      requiredMark={false}
      initialValues={initial ?? { tripType: 'DOMESTIC' }}
      onFinish={(v) => submit.mutate(v)}
      id="wr-form"
    >
      <Form.Item name="tripType" label="구분">
        <Radio.Group
          optionType="button"
          buttonStyle="solid"
          options={[
            { value: 'DOMESTIC', label: '국내' },
            { value: 'OVERSEAS', label: '해외' },
          ]}
        />
      </Form.Item>
      <Form.Item name="destination" label="방문지 · 업체" rules={[{ required: true, whitespace: true, message: '방문지를 입력하세요' }]}>
        <Input maxLength={100} placeholder="예: 부산 · OO전자" />
      </Form.Item>
      <Form.Item name="period" label="기간" rules={[{ required: true, message: '기간을 고르세요' }]}>
        <DatePicker.RangePicker style={{ width: '100%' }} format="YYYY.MM.DD" />
      </Form.Item>
      <p className="wr-hint">지난 날짜도 고를 수 있습니다(사후 신청).</p>
      <Form.Item name="purpose" label="목적" rules={[{ required: true, whitespace: true, message: '목적을 입력하세요' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount />
      </Form.Item>
      <Form.Item name="estimatedCost" label="예상 경비(선택)">
        <InputNumber
          min={0}
          step={10000}
          style={{ width: '100%' }}
          formatter={(v) => (v ? `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : '')}
          parser={(v) => v.replace(/,/g, '')}
          suffix="원"
        />
      </Form.Item>
      <SubmitRow onClose={onClose} loading={submit.isPending} />
    </Form>
  );
}

function ReportForm({ trips, tripId, onClose }) {
  const [form] = Form.useForm();
  const selected = Form.useWatch('tripId', form) ?? tripId;
  const detail = useQuery({
    queryKey: tripKey(selected),
    queryFn: () => getTrip(selected),
    enabled: !!selected,
  });
  const submit = useSubmit(form, (v) => reportTrip(v.tripId, v.reportText.trim()), '결과 보고를 저장했습니다', onClose);
  // 이미 쓴 보고가 있으면 불러와 수정 — 출장을 고를 때마다 그 출장의 보고로 채운다
  useEffect(() => {
    if (detail.data) form.setFieldsValue({ reportText: detail.data.reportText ?? '' });
  }, [detail.data, form]);
  if (trips.length === 0) return <p className="wr-empty">결과 보고를 쓸 수 있는 출장이 없습니다(승인된 출장의 종료일부터 쓸 수 있어요).</p>;
  return (
    <Form
      form={form}
      layout="vertical"
      className="wr-form"
      requiredMark={false}
      initialValues={{ tripId }}
      onFinish={(v) => submit.mutate(v)}
      id="wr-form"
    >
      <Form.Item name="tripId" label="출장" rules={[{ required: true, message: '출장을 고르세요' }]}>
        <Select
          options={trips.map((t) => ({
            value: t.id,
            label: `${dot(t.startDate)} - ${dot(t.endDate)} · ${t.destination}${t.reportWritten ? ' (작성됨)' : ''}`,
          }))}
        />
      </Form.Item>
      <Form.Item name="reportText" label="결과 보고" rules={[{ required: true, whitespace: true, message: '내용을 입력하세요' }]}>
        <Input.TextArea rows={6} placeholder="목적 달성 여부 · 미팅/업무 결과" />
      </Form.Item>
      <SubmitRow onClose={onClose} loading={submit.isPending} text="저장" />
    </Form>
  );
}

function ExpenseForm({ trips, tripId, onClose }) {
  const [form] = Form.useForm();
  const types = useQuery({
    queryKey: expenseTypesKey,
    queryFn: getExpenseTypes,
  });
  const selectedId = Form.useWatch('tripId', form) ?? tripId;
  const lines = Form.useWatch('lines', form) ?? [];
  const trip = trips.find((t) => t.id === selectedId);
  const typeById = Object.fromEntries((types.data ?? []).map((t) => [t.id, t]));
  const total = lines.reduce((a, l) => a + (Number(l?.amount) || 0), 0);
  const submit = useSubmit(
    form,
    (v) =>
      requestExpense({
        businessTripId: v.tripId,
        lines: v.lines.map((l) => ({
          expenseTypeId: l.expenseTypeId,
          usedDate: l.usedDate.format('YYYY-MM-DD'),
          amount: l.amount,
          description: l.description?.trim() || null,
          receiptFileId: l.receiptFileId ?? null,
        })),
      }),
    '경비를 청구했습니다',
    onClose,
  );
  if (trips.length === 0)
    return <p className="wr-empty">경비를 청구할 수 있는 출장이 없습니다(승인된 출장의 시작일부터, 진행 중인 청구가 없을 때).</p>;
  // 사용일은 출장 기간 안에서만(프론트 가이드 3.2)
  const outOfTrip = (d) => !trip || d.format('YYYY-MM-DD') < trip.startDate || d.format('YYYY-MM-DD') > trip.endDate;
  return (
    <Form
      form={form}
      layout="vertical"
      className="wr-form"
      requiredMark={false}
      initialValues={{ tripId, lines: [{}] }}
      onFinish={(v) => submit.mutate(v)}
      id="wr-form"
    >
      <Form.Item name="tripId" label="출장" rules={[{ required: true, message: '출장을 고르세요' }]}>
        <Select
          options={trips.map((t) => ({
            value: t.id,
            label: `${dot(t.startDate)} - ${dot(t.endDate)} · ${t.destination}`,
          }))}
        />
      </Form.Item>
      <Form.List name="lines">
        {(fields, { add, remove }) => (
          <div className="wr-lines">
            {fields.map((f, i) => {
              const required = typeById[lines[i]?.expenseTypeId]?.receiptRequired;
              return (
                <div key={f.key} className="wr-line">
                  <div style={{ display: 'flex', gap: 8 }}>
                    <Form.Item
                      name={[f.name, 'expenseTypeId']}
                      label="종류"
                      style={{ flex: 1 }}
                      rules={[{ required: true, message: '종류' }]}
                    >
                      <Select
                        loading={types.isLoading}
                        options={(types.data ?? []).map((t) => ({
                          value: t.id,
                          label: `${t.name}${t.receiptRequired ? ' · 영수증 필수' : ''}`,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      name={[f.name, 'usedDate']}
                      label="사용일"
                      style={{ flex: 1 }}
                      rules={[{ required: true, message: '사용일' }]}
                    >
                      <DatePicker style={{ width: '100%' }} format="MM.DD" disabledDate={outOfTrip} />
                    </Form.Item>
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <Form.Item name={[f.name, 'amount']} label="금액" style={{ flex: 1 }} rules={[{ required: true, message: '금액' }]}>
                      <InputNumber
                        min={1}
                        style={{ width: '100%' }}
                        formatter={(v) => (v ? `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : '')}
                        parser={(v) => v.replace(/,/g, '')}
                        suffix="원"
                      />
                    </Form.Item>
                    <Form.Item name={[f.name, 'description']} label="내용" style={{ flex: 1 }}>
                      <Input maxLength={255} />
                    </Form.Item>
                  </div>
                  <Form.Item
                    name={[f.name, 'receiptFileId']}
                    label={required ? '영수증 *' : '영수증'}
                    rules={[
                      {
                        required: !!required,
                        message: '영수증이 필요한 경비입니다',
                      },
                    ]}
                    style={{ marginBottom: fields.length > 1 ? 8 : 0 }}
                  >
                    <FileUpload purpose="RECEIPT" />
                  </Form.Item>
                  {fields.length > 1 && (
                    <button
                      type="button"
                      className="wr-chip"
                      style={{ background: '#fef2f2', color: '#dc2626' }}
                      onClick={() => remove(f.name)}
                    >
                      줄 삭제
                    </button>
                  )}
                </div>
              );
            })}
            <button type="button" className="wr-btn is-line" onClick={() => add({})}>
              + 경비 줄 추가
            </button>
          </div>
        )}
      </Form.List>
      <p className="wr-total">{`합계 ${formatMoney(total)}`}</p>
      <SubmitRow onClose={onClose} loading={submit.isPending} text="청구" />
    </Form>
  );
}

function SubmitRow({ onClose, loading, text = '신청' }) {
  return (
    <div
      style={{
        display: 'flex',
        gap: 8,
        justifyContent: 'flex-end',
        marginTop: 8,
      }}
    >
      <button type="button" className="wr-btn is-line" onClick={onClose}>
        닫기
      </button>
      <button type="submit" className="wr-btn" disabled={loading}>
        {loading ? '보내는 중…' : text}
      </button>
    </div>
  );
}

const MODAL_TITLE = {
  overtime: '연장 근무 신청',
  trip: '출장 / 외근 신청서 작성',
  report: '출장 복귀 보고서 작성',
  expense: '출장 경비 청구',
};

function RequestModal({ open, trips, onClose, onType }) {
  if (!open) return null;
  const reportable = trips.filter(canReport);
  const claimable = trips.filter(canClaim);
  return (
    <Modal open title={open.initial ? '신청서 수정' : MODAL_TITLE[open.type]} footer={null} onCancel={onClose} destroyOnHidden width={open.type === 'expense' ? 560 : 480}>
      {open.initial && <p className="wr-hint" style={{ margin: '0 0 0.875rem' }}>이전 신청은 철회되었습니다. 고친 뒤 [신청]을 누르면 새로 결재가 올라갑니다.</p>}
      {!open.initial && (open.type === 'overtime' || open.type === 'trip') && (
        <Radio.Group
          style={{ marginBottom: 16 }}
          optionType="button"
          value={open.type}
          onChange={(e) => onType(e.target.value)}
          options={[
            { value: 'overtime', label: '연장 근무' },
            { value: 'trip', label: '출장' },
          ]}
        />
      )}
      {open.type === 'overtime' && <OvertimeForm onClose={onClose} initial={open.initial} />}
      {open.type === 'trip' && <TripForm onClose={onClose} initial={open.initial} />}
      {open.type === 'report' && <ReportForm trips={reportable} tripId={open.tripId ?? reportable[0]?.id} onClose={onClose} />}
      {open.type === 'expense' && <ExpenseForm trips={claimable} tripId={open.tripId ?? claimable[0]?.id} onClose={onClose} />}
    </Modal>
  );
}

/** 연장 근무 / 출장(F-ATT-06–08) — Figma EMP_연장근무 */
export default function MyWorkRequestsPage() {
  const navigate = useNavigate();
  const [month, setMonth] = useState(() => dayjs().startOf('month'));
  const [tab, setTab] = useState('ALL');
  const [kindFilter, setKindFilter] = useState(null); // 'overtime' | 'trip'
  const [selectedKey, setSelectedKey] = useState(null);
  const [open, setOpen] = useState(null); // { type, tripId? }
  const ym = month.format('YYYY-MM');

  const overtimes = useQuery({
    queryKey: myOvertimesKey(ym),
    queryFn: () => getMyOvertimes(ym),
  });
  const trips = useQuery({ queryKey: myTripsKey, queryFn: getMyTrips });
  const expenses = useQuery({
    queryKey: myExpensesKey,
    queryFn: getMyExpenses,
  });
  // 이번 주(월요일 시작) 근무 합계 — 달이 걸치면 두 달 기록
  const week = weekDates(today());
  const weekMonths = [...new Set(week.map((d) => d.slice(0, 7)))];
  const attendances = useQueries({
    queries: weekMonths.map((m) => ({
      queryKey: myAttendancesKey(m),
      queryFn: () => getMyAttendances(m),
    })),
  });

  // 고른 달의 근태 — 승인된 연장의 인정 시간(그날 overtimeMinutes)
  const monthAtt = useQuery({ queryKey: myAttendancesKey(ym), queryFn: () => getMyAttendances(ym) });
  const monthDays = monthAtt.data?.days;

  const otRows = overtimes.data?.content ?? [];
  const tripRows = trips.data?.content ?? [];
  const expRows = expenses.data?.content ?? [];
  const items = useMemo(
    () =>
      buildItems({
        overtimes: otRows,
        trips: tripRows,
        expenses: expRows,
        month,
        dayOf: (date) => monthDays?.find((d) => d.date === date),
      }),
    [otRows, tripRows, expRows, month, monthDays],
  );

  if (overtimes.isLoading || trips.isLoading || expenses.isLoading) {
    return (
      <div className="page-center" style={{ minHeight: '25rem' }}>
        <Spin size="large" />
      </div>
    );
  }
  const error = overtimes.error ?? trips.error ?? expenses.error;
  if (error) return <Result status="warning" title="신청 내역을 불러오지 못했습니다" subTitle={errorMessage(error)} />;

  const byKind = items.filter((i) => !kindFilter || (kindFilter === 'trip' ? i.kind !== 'overtime' : i.kind === 'overtime'));
  const rows = byKind.filter((i) => tab === 'ALL' || (tab === 'PENDING' ? i.status === 'PENDING' : i.status !== 'PENDING'));
  const selected = rows.find((i) => i.key === selectedKey) ?? rows[0] ?? null;

  const monthTrips = items.filter((i) => i.kind === 'trip');
  const t = today();
  const tripActive = monthTrips.filter((i) => i.status === 'PENDING' || (i.status === 'APPROVED' && i.row.endDate >= t)).length;
  const weekLoaded = attendances.every((a) => a.data);
  const byDate = Object.fromEntries(attendances.flatMap((a) => a.data?.days ?? []).map((d) => [d.date, d]));
  const weekSum = sumMinutes(week.map((d) => byDate[d]));
  // 연장 근로 잔여 = 주 연장 12시간 − 이번 주 연장(출퇴근 화면과 같은 정의, 노션 Q-112)
  const remain = WEEK_OT_LIMIT - weekSum.overtime;
  const h = (min) => `${Math.round((min / 60) * 10) / 10}시간`;

  const pick = (kind) => {
    setKindFilter(kind);
    setTab('ALL');
    setSelectedKey(null);
    document.querySelector('.wr-main')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const menus = [
    {
      title: '01  연장 근무 신청 및 내역',
      links: [
        ['연장 근무 신청', '야간·휴일 / 근무 일자·예정 시간·업무 사유', () => setOpen({ type: 'overtime' })],
        ['연장 근무 결재 현황', '결재 대기 · 승인 · 반려 상태 확인', () => pick('overtime')],
        ['주간 연장 근로 잔여 시간', '주 연장 12시간 기준 · 출퇴근 화면에서 조회', () => navigate('/me/attendance')],
      ],
    },
    {
      title: '02  출장 및 외근 신청',
      links: [
        ['출장 / 외근 신청서 작성', '국내·해외 / 방문지·업체·목적·기간', () => setOpen({ type: 'trip' })],
        ['출장 일정 및 결재 현황', '승인 여부 확인 · 출장 · 경비 내역', () => pick('trip')],
      ],
    },
    {
      title: '03  출장 복귀 및 결과 보고',
      links: [
        ['출장 복귀 보고서 작성', '목적 달성 여부 · 미팅/업무 결과', () => setOpen({ type: 'report' })],
        ['출장 경비 청구', '경비 줄 · 영수증 첨부(jpg · png · pdf)', () => setOpen({ type: 'expense' })],
      ],
    },
  ];

  return (
    <div className="wr">
      <header className="wr-head">
        <div>
          <h1>연장 근무 / 출장</h1>
          <p>연장 근무 신청, 출장 일정, 결재 현황을 한 번에 관리하세요.</p>
        </div>
        <div className="wr-actions">
          <DatePicker
            className="wr-period"
            picker="month"
            value={month}
            allowClear={false}
            inputReadOnly
            format={(v) => `${v.startOf('month').format('YYYY.MM.DD')} - ${v.endOf('month').format('YYYY.MM.DD')}`}
            suffixIcon={<img src={iconChevron} alt="" />}
            onChange={(v) => {
              setMonth(v.startOf('month'));
              setSelectedKey(null);
            }}
          />
          <button type="button" className="wr-btn" onClick={() => setOpen({ type: 'overtime' })}>
            신청서 작성
          </button>
        </div>
      </header>

      <div className="wr-stats">
        <div className="wr-card wr-stat">
          <span>연장 근무 신청</span>
          <strong>{`${otRows.length}건`}</strong>
          <em>{`결재 대기 ${otRows.filter((r) => r.status === 'PENDING').length}건`}</em>
        </div>
        <div className="wr-card wr-stat">
          <span>출장 신청</span>
          <strong>{`${monthTrips.length}건`}</strong>
          <em>{`진행 중 ${tripActive}건`}</em>
        </div>
        <div className="wr-card wr-stat">
          <span>주간 근로 시간</span>
          <strong>{weekLoaded ? h(weekSum.work) : '-'}</strong>
          <em>{weekLoaded ? `기본 ${h(weekSum.base)} + 연장 ${h(weekSum.overtime)}` : '불러오는 중'}</em>
        </div>
        <div className="wr-card wr-stat">
          <span>연장 근로 잔여</span>
          <strong>{weekLoaded ? h(Math.max(0, remain)) : '-'}</strong>
          <em className={remain < 0 ? 'is-red' : undefined}>{`주 연장 12시간 한도 · ${dayjs(week[0]).format('M/D')}–${dayjs(week[6]).format('M/D')}`}</em>
        </div>
      </div>

      <div className="wr-menus">
        {menus.map((m) => (
          <section key={m.title} className="wr-card wr-menu">
            <h2>{m.title}</h2>
            {m.links.map(([label, desc, onClick]) => (
              <button key={label} type="button" className="wr-link" onClick={onClick}>
                <strong>{`${label}  →`}</strong>
                <span>{desc}</span>
              </button>
            ))}
          </section>
        ))}
      </div>

      <div className="wr-main">
        <section className="wr-card wr-list">
          <div className="wr-list-head">
            <h2>
              {`최근 신청 내역 · ${rows.length}건`}
              {kindFilter && (
                <button type="button" className="wr-chip" onClick={() => setKindFilter(null)} title="필터 해제">
                  {`${kindFilter === 'trip' ? '출장 · 경비' : '연장 근무'}만 ✕`}
                </button>
              )}
            </h2>
            <div className="wr-tabs">
              {[
                ['ALL', '전체'],
                ['PENDING', '결재 대기'],
                ['DONE', '완료'],
              ].map(([key, label]) => (
                <button key={key} type="button" className={`wr-tab${tab === key ? ' is-active' : ''}`} onClick={() => setTab(key)}>
                  {label}
                </button>
              ))}
            </div>
          </div>
          <div className="wr-rows">
            {rows.length === 0 && <p className="wr-empty">{`${month.format('M월')}에 해당하는 신청이 없습니다.`}</p>}
            {rows.map((i) => (
              <button
                key={i.key}
                type="button"
                className={`wr-row${selected?.key === i.key ? ' is-active' : ''}`}
                onClick={() => setSelectedKey(i.key)}
              >
                <span className="wr-row-info">
                  <strong>{i.title}</strong>
                  <span>{i.sub}</span>
                </span>
                <span className={`wr-badge ${BADGE[i.status] ?? 'is-grey'}`}>{badgeText(i.status)}</span>
              </button>
            ))}
          </div>
        </section>
        <DetailCard key={selected?.key ?? 'none'} item={selected} onOpen={setOpen} />
      </div>

      <RequestModal open={open} trips={tripRows} onClose={() => setOpen(null)} onType={(type) => setOpen({ type })} />
    </div>
  );
}
