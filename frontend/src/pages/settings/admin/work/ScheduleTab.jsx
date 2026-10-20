// 근무시간 탭 — GET /work-schedules { current, upcoming[], history[] } · POST(변경) · DELETE(적용 예정만)
import { useState } from 'react';
import { DatePicker, Form, InputNumber, Modal, Spin, Switch, TimePicker } from 'antd';
import dayjs from 'dayjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { WEEKDAYS, createWorkSchedule, deleteWorkSchedule, getWorkSchedules, workSchedulesKey } from '../../../../api/workSchedule';
import { applyFieldErrors } from '../../../../components/common/formErrors';
import { useNotify } from '../../../../components/common/useNotify';
import { errorMessage } from '../../../../constants/errors';
import { formatMinutes } from '../../../../utils/format';
import { ConfirmModal, ModalFoot } from '../../../hr/parts';
import { today, ymd } from '../parts';

const DAY_LABEL = Object.fromEntries(WEEKDAYS);
const ORDER = WEEKDAYS.map(([k]) => k);

/** ['MON'…'FRI'] → '월-금', 띄엄띄엄이면 '월·수·금' */
export function daysText(days = []) {
  const idx = days.map((d) => ORDER.indexOf(d)).sort((a, b) => a - b);
  if (idx.length === 0) return '-';
  const consecutive = idx.every((v, i) => i === 0 || v === idx[i - 1] + 1);
  if (consecutive && idx.length >= 3) return `${DAY_LABEL[ORDER[idx[0]]]}-${DAY_LABEL[ORDER[idx.at(-1)]]}`;
  return idx.map((i) => DAY_LABEL[ORDER[i]]).join('·');
}

const summary = (s) => `${s.startTime}-${s.endTime} · 휴게 ${s.breakMinutes}분 · ${daysText(s.workDays)}`;

export default function ScheduleTab() {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: workSchedulesKey, queryFn: getWorkSchedules });
  const [editing, setEditing] = useState(false);
  const [removing, setRemoving] = useState(null);

  if (list.isLoading) return <Spin />;
  if (list.error) return <p className="hr-notice is-error">{errorMessage(list.error)}</p>;
  const { current, upcoming = [], history = [] } = list.data ?? {};
  const past = [...(current ? [current] : []), ...history];

  return (
    <>
      <section className="ad-section">
        <div className="ad-section-head">
          <h2>현재 적용 중 {current ? <small>{`${current.effectiveFrom}부터`}</small> : null}</h2>
          <button type="button" className="ad-btn" onClick={() => setEditing(true)}>변경</button>
        </div>
        {current ? (
          <>
            <div className="ad-now">
              <div className="ad-stat">
                <span className="k">1일 소정근로시간</span>
                <span className="v">{formatMinutes(current.dailyStandardMinutes)}</span>
                <span className="s">{`${current.dailyStandardMinutes}분 (휴게 ${current.breakMinutes}분 제외)`}</span>
              </div>
              <div className="ad-vals">
                <Val k="출근 시각" v={current.startTime} />
                <Val k="퇴근 시각" v={current.endTime} />
                <Val k="휴게" v={`${current.breakMinutes}분`} />
                <Val k="지각 허용" v={`${current.lateGraceMinutes}분`} />
                <Val k="연장 근무 승인" v={current.overtimeApprovalRequired ? '필요' : '불필요'} />
                <Val k="야간 시간대" v={`${current.nightStart}–${current.nightEnd}`} />
              </div>
            </div>
            <div className="ad-days-block">
              근무 요일
              <Days value={current.workDays} />
            </div>
          </>
        ) : (
          <p className="hr-note">적용 중인 근무시간이 없습니다. [변경]으로 등록하세요.</p>
        )}
      </section>

      <section className="ad-section">
        <div className="ad-section-head"><h2>적용 예정</h2></div>
        {upcoming.length === 0 ? (
          <p className="hr-note">예정된 변경이 없습니다.</p>
        ) : (
          upcoming.map((s) => (
            <div key={s.id} className="ad-upcoming">
              <span style={{ width: '7rem' }}>{s.effectiveFrom}</span>
              <span>{summary(s)}</span>
              <button type="button" className="ad-mini is-dark" style={{ width: '4.625rem' }} onClick={() => setRemoving(s)}>삭제</button>
            </div>
          ))
        )}
      </section>

      <section className="ad-section">
        <div className="ad-section-head"><h2 className="is-20">변경 이력 <small>이미 시작된 근무시간은 고치거나 지울 수 없습니다.</small></h2></div>
        <table className="ad-ctable">
          <thead>
            <tr><th>적용 시작일</th><th>출근</th><th>퇴근</th><th>휴게</th><th>지각 허용</th><th>야간</th><th>연장 승인</th><th>근무 요일</th></tr>
          </thead>
          <tbody>
            {past.length === 0 ? (
              <tr className="is-empty"><td colSpan={8}>이력이 없습니다</td></tr>
            ) : past.map((s) => (
              <tr key={s.id}>
                <td>{s.effectiveFrom}{s.id === current?.id ? <span className="hr-badge is-info" style={{ marginLeft: '0.5rem' }}>현재</span> : null}</td>
                <td>{s.startTime}</td>
                <td>{s.endTime}</td>
                <td>{`${s.breakMinutes}분`}</td>
                <td>{`${s.lateGraceMinutes}분`}</td>
                <td>{`${s.nightStart}–${s.nightEnd}`}</td>
                <td>{s.overtimeApprovalRequired ? '필요' : '불필요'}</td>
                <td>{daysText(s.workDays)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {editing && <ScheduleModal base={upcoming.at(-1) ?? current} onClose={() => setEditing(false)} />}
      <ConfirmModal open={!!removing} danger okText="삭제" title="적용 예정 근무시간 삭제"
        desc={removing ? `${removing.effectiveFrom}부터 적용할 근무시간(${summary(removing)})을 지웁니다. 계속하시겠습니까?` : ''}
        onClose={() => setRemoving(null)}
        onConfirm={async () => {
          await deleteWorkSchedule(removing.id);
          queryClient.invalidateQueries({ queryKey: workSchedulesKey });
          notify.success('적용 예정 근무시간을 삭제했습니다');
        }} />
    </>
  );
}

function Val({ k, v }) {
  return (
    <div className="ad-val">
      <span className="k">{k}</span>
      <span className="v">{v}</span>
    </div>
  );
}

/** 요일 동그라미 — onChange 가 있으면 눌러서 켜고 끈다 */
export function Days({ value = [], onChange }) {
  const toggle = (d) => {
    const next = value.includes(d) ? value.filter((x) => x !== d) : [...value, d];
    onChange(ORDER.filter((x) => next.includes(x)));
  };
  return (
    <div className="ad-days">
      {WEEKDAYS.map(([k, label]) => (onChange ? (
        <button key={k} type="button" className={`ad-day${value.includes(k) ? ' is-on' : ''}`} aria-pressed={value.includes(k)} onClick={() => toggle(k)}>
          {label}
        </button>
      ) : (
        <span key={k} className={`ad-day${value.includes(k) ? ' is-on' : ''}`}>{label}</span>
      )))}
    </div>
  );
}

const t = (s) => (s ? dayjs(`2000-01-01T${s}`) : null);
const hm = (d) => (d ? d.format('HH:mm') : null);

function ScheduleModal({ base, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const save = useMutation({
    mutationFn: createWorkSchedule,
    onSuccess: (s) => {
      queryClient.invalidateQueries({ queryKey: workSchedulesKey });
      notify.success(s.effectiveFrom === today() ? '오늘부터 새 근무시간을 적용합니다' : `${s.effectiveFrom}부터 적용할 근무시간을 등록했습니다`);
      onClose();
    },
    onError: (err) => {
      if (err?.code === 'SCHEDULE_PAST_DATE' || err?.code === 'INVALID_STATE') {
        form.setFields([{ name: 'effectiveFrom', errors: [errorMessage(err)] }]);
        return;
      }
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });
  const init = {
    effectiveFrom: null,
    workDays: base?.workDays ?? ['MON', 'TUE', 'WED', 'THU', 'FRI'],
    startTime: t(base?.startTime ?? '09:00'),
    endTime: t(base?.endTime ?? '18:00'),
    breakMinutes: base?.breakMinutes ?? 60,
    lateGraceMinutes: base?.lateGraceMinutes ?? 0,
    nightStart: t(base?.nightStart ?? '22:00'),
    nightEnd: t(base?.nightEnd ?? '06:00'),
    overtimeApprovalRequired: base?.overtimeApprovalRequired ?? true,
  };
  const onFinish = (v) => save.mutate({
    effectiveFrom: ymd(v.effectiveFrom),
    startTime: hm(v.startTime),
    endTime: hm(v.endTime),
    breakMinutes: v.breakMinutes,
    lateGraceMinutes: v.lateGraceMinutes,
    nightStart: hm(v.nightStart),
    nightEnd: hm(v.nightEnd),
    overtimeApprovalRequired: v.overtimeApprovalRequired,
    workDays: v.workDays,
  });
  const timeRule = (msg) => [{ required: true, message: msg }];
  return (
    <Modal open title="근무시간 변경" footer={null} onCancel={onClose} width={640} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">적용 시작일부터 새 근무시간이 적용됩니다. 기존 이력은 수정되지 않습니다.</p>
      <Form form={form} layout="vertical" requiredMark initialValues={init} onFinish={onFinish} className="ad-grid" style={{ '--cols': 3 }}>
        <Form.Item name="effectiveFrom" label="적용 시작일" rules={[{ required: true, message: '적용 시작일을 고르세요' }]}>
          <DatePicker className="full-width" disabledDate={(d) => d.isBefore(dayjs(), 'day')} />
        </Form.Item>
        <Form.Item name="workDays" label="근무 요일" className="ad-span-2"
          rules={[{ validator: (_, v) => (v?.length ? Promise.resolve() : Promise.reject(new Error('근무 요일을 하루 이상 고르세요'))) }]}>
          <DaysInput />
        </Form.Item>
        <Form.Item name="startTime" label="출근 시간" rules={timeRule('출근 시간을 고르세요')}>
          <TimePicker format="HH:mm" minuteStep={10} className="full-width" needConfirm={false} />
        </Form.Item>
        <Form.Item name="endTime" label="퇴근 시간" dependencies={['startTime']}
          rules={[...timeRule('퇴근 시간을 고르세요'), ({ getFieldValue }) => ({
            validator: (_, v) => (!v || !getFieldValue('startTime') || hm(v) > hm(getFieldValue('startTime'))
              ? Promise.resolve() : Promise.reject(new Error('퇴근은 출근보다 늦어야 합니다'))),
          })]}>
          <TimePicker format="HH:mm" minuteStep={10} className="full-width" needConfirm={false} />
        </Form.Item>
        <Form.Item name="breakMinutes" label="휴게(분)" rules={[{ required: true, message: '휴게 시간을 입력하세요' }]}>
          <InputNumber min={0} max={720} precision={0} />
        </Form.Item>
        <Form.Item name="nightStart" label="야간 시작" rules={timeRule('야간 시작을 고르세요')}>
          <TimePicker format="HH:mm" minuteStep={30} className="full-width" needConfirm={false} />
        </Form.Item>
        <Form.Item name="nightEnd" label="야간 종료" rules={timeRule('야간 종료를 고르세요')}>
          <TimePicker format="HH:mm" minuteStep={30} className="full-width" needConfirm={false} />
        </Form.Item>
        <Form.Item name="lateGraceMinutes" label="지각 허용(분)" rules={[{ required: true, message: '지각 허용 시간을 입력하세요' }]}>
          <InputNumber min={0} max={720} precision={0} />
        </Form.Item>
        <Form.Item name="overtimeApprovalRequired" label="연장 근무 승인 필요" valuePropName="checked">
          <Switch checkedChildren="예" unCheckedChildren="아니오" />
        </Form.Item>
        <div className="ad-span-2" />
        <div style={{ gridColumn: '1 / -1' }}>
          <ModalFoot onCancel={onClose} okText="변경" busy={save.isPending} />
        </div>
      </Form>
    </Modal>
  );
}

function DaysInput({ value, onChange }) {
  return <Days value={value ?? []} onChange={onChange} />;
}
