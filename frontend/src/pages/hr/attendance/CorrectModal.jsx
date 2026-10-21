// 근태 정정 · 입력 창(F-ATT-04, ATTENDANCE_MANAGE) — 근태 조회의 일별 상세에서 연다
// target: { mode: 'patch' | 'create', employeeId, employeeName, workDate, attendanceId?, record?, conflict?, type? }
import { Form, Input, Modal, Radio, TimePicker } from 'antd';
import dayjs from 'dayjs';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { correctAttendance, createAttendance } from '../../../api/attendance';
import { meKey } from '../../../api/auth';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { labelOf } from '../../../constants/labels';
import { ModalFoot } from '../parts';
import { dotDay, hm } from './shared';

/** 지금 기록 — "출근 09:01 · 퇴근 없음 · 퇴근미기록" */
const recordText = (r) => `출근 ${hm(r.checkInAt)} · 퇴근 ${r.checkOutAt ? hm(r.checkOutAt) : '없음'} · ${labelOf('attendanceStatus', r.status)}`;

export default function CorrectModal({ target, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const isCreate = target.mode === 'create';
  const r = target.record ?? {};
  const isToday = target.workDate === dayjs().format('YYYY-MM-DD');
  const conflict = target.type === 'LEAVE_CONFLICT' || target.type === 'TRIP_CONFLICT';
  const status = Form.useWatch('status', form);

  const statusOptions = [
    ...(isToday ? [{ value: 'CHECKED_IN', label: '출근(퇴근 전)' }] : []),
    { value: 'CHECKED_OUT', label: '출퇴근 기록' },
    { value: 'ON_VACATION', label: '휴가' },
    { value: 'ON_BUSINESS_TRIP', label: '출장' },
    ...(conflict ? [{ value: 'KEEP', label: '기록 그대로 두기(확인만)' }] : []),
  ];
  const initialStatus = conflict ? (target.type === 'LEAVE_CONFLICT' ? 'ON_VACATION' : 'ON_BUSINESS_TRIP') : r.status === 'CHECKED_IN' && isToday ? 'CHECKED_IN' : 'CHECKED_OUT';
  const at = (t) => (t ? dayjs(`${target.workDate}T${t.format('HH:mm')}:00`).format() : null);

  const save = useMutation({
    mutationFn: (v) => {
      const reason = v.reason.trim();
      const timed = v.status === 'CHECKED_OUT' || v.status === 'CHECKED_IN';
      const values = v.status === 'KEEP' ? {} : { status: v.status, ...(timed ? { workType: v.workType, checkInAt: at(v.checkIn), checkOutAt: v.status === 'CHECKED_OUT' ? at(v.checkOut) : null } : {}) };
      return isCreate ? createAttendance({ employeeId: target.employeeId, workDate: target.workDate, ...values, reason }) : correctAttendance(target.attendanceId, { ...values, reason });
    },
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      queryClient.invalidateQueries({ queryKey: meKey }); // 정정 대상 건수(todos)
      notify.success(`${target.employeeName} ${target.workDate.slice(5).replace('-', '/')} 근태를 ${isCreate ? '입력' : '정정'}했습니다`, res);
      onClose();
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });

  return (
    <Modal open title={isCreate ? '근태 입력' : '근태 정정'} footer={null} onCancel={onClose} width={520} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">
        {`${target.employeeName} · ${dotDay(target.workDate)}`}
        {!isCreate && ` · 지금 기록: ${recordText(r)}`}
      </p>
      {target.conflict && <p className="hr-note" style={{ marginTop: 0 }}>{`승인된 일정: ${target.conflict.name} (${target.conflict.startDate} ~ ${target.conflict.endDate})`}</p>}
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{
          status: initialStatus,
          workType: r.workType && r.workType !== 'BUSINESS_TRIP' ? r.workType : 'OFFICE',
          checkIn: r.checkInAt ? dayjs(r.checkInAt) : dayjs(`${target.workDate}T09:00`),
          checkOut: r.checkOutAt ? dayjs(r.checkOutAt) : dayjs(`${target.workDate}T18:00`),
        }}
        onFinish={(v) => save.mutate(v)}
      >
        <Form.Item name="status" label="정정할 내용">
          <Radio.Group options={statusOptions} optionType="button" buttonStyle="solid" />
        </Form.Item>
        {(status === 'CHECKED_OUT' || status === 'CHECKED_IN') && (
          <>
            <Form.Item name="workType" label="근무 형태">
              <Radio.Group options={['OFFICE', 'REMOTE', 'FIELD'].map((v) => ({ value: v, label: labelOf('workType', v) }))} />
            </Form.Item>
            <div className="hr-form-row">
              <Form.Item name="checkIn" label="출근 시각" rules={[{ required: true, message: '출근 시각을 고르세요' }]}>
                <TimePicker className="full-width" format="HH:mm" minuteStep={5} needConfirm={false} />
              </Form.Item>
              {status === 'CHECKED_OUT' && (
                <Form.Item
                  name="checkOut"
                  label="퇴근 시각"
                  dependencies={['checkIn']}
                  rules={[
                    { required: true, message: '퇴근 시각을 고르세요' },
                    ({ getFieldValue }) => ({
                      validator: (_, v) => (!v || !getFieldValue('checkIn') || v.isAfter(getFieldValue('checkIn')) ? Promise.resolve() : Promise.reject(new Error('퇴근은 출근보다 늦어야 합니다'))),
                    }),
                  ]}
                >
                  <TimePicker className="full-width" format="HH:mm" minuteStep={5} needConfirm={false} />
                </Form.Item>
              )}
            </div>
          </>
        )}
        {(status === 'ON_VACATION' || status === 'ON_BUSINESS_TRIP') && (
          <p className="hr-hint" style={{ marginTop: 0 }}>그날 승인된 휴가 · 출장이 있어야 합니다. 출퇴근 시각은 지워집니다.</p>
        )}
        <Form.Item name="reason" label="정정 사유" rules={[{ required: true, whitespace: true, message: '정정 사유를 입력하세요' }]}>
          <Input.TextArea maxLength={255} showCount autoSize={{ minRows: 2, maxRows: 4 }} placeholder="예: 퇴근 시 카드 미태깅 — 팀장 확인" />
        </Form.Item>
        <ModalFoot onCancel={onClose} busy={save.isPending} okText={isCreate ? '입력' : '정정'} />
      </Form>
    </Modal>
  );
}
