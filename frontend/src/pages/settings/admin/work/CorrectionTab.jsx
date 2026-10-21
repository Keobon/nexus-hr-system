// 근무 정정 탭(F-ATT-04, ATTENDANCE_MANAGE) — 정정이 필요한 기록 목록 + 직원을 눌러 그 달 근태를 보고 날짜별로 정정 · 입력
// Figma에 없는 화면이라 근무시간 · 휴일 탭의 표 · 버튼 모양을 따랐다
// 이미 있는 기록은 기록 ID가 있어야 고칠 수 있다 — 정정 대상 목록의 attendanceId, 또는 날짜 행의 attendanceId(백엔드 요청 중, 오면 그대로 쓴다)
import { useMemo, useState } from 'react';
import { DatePicker, Drawer, Form, Input, Modal, Radio, Select, Spin, TimePicker } from 'antd';
import dayjs from 'dayjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  attendanceCorrectionsKey,
  attendanceMonthKey,
  correctAttendance,
  createAttendance,
  employeeAttendancesKey,
  getAttendanceCorrections,
  getAttendanceMonth,
  getEmployeeAttendances,
} from '../../../../api/attendance';
import { meKey } from '../../../../api/auth';
import { fetchAllPages } from '../../../../api/employees';
import { applyFieldErrors } from '../../../../components/common/formErrors';
import { useNotify } from '../../../../components/common/useNotify';
import { errorMessage } from '../../../../constants/errors';
import { labelOf } from '../../../../constants/labels';
import { formatMinutes } from '../../../../utils/format';
import { ModalFoot, useOrgOptions } from '../../../hr/parts';
import './ac.css';

const TYPE = {
  MISSING_CHECKOUT: ['퇴근 미기록', 'is-warn'],
  LEAVE_CONFLICT: ['휴가와 겹침', 'is-info'],
  TRIP_CONFLICT: ['출장과 겹침', 'is-info'],
};
const WEEK = ['일', '월', '화', '수', '목', '금', '토'];
const hm = (t) => (t ? dayjs(t).format('HH:mm') : '–');
const dateText = (d) => `${d.replaceAll('-', '.')} (${WEEK[dayjs(d).day()]})`;
/** 출퇴근 기록이 있는 날 — 출근 · 퇴근 · 퇴근미기록 */
const hasRecord = (d) => ['CHECKED_IN', 'CHECKED_OUT', 'MISSING_CHECKOUT'].includes(d.status);
/** 기록이 없는 오늘까지의 근무일(미기록 · 결근) — 새로 입력할 수 있다. 상태가 없는 날은 입사 전 · 쉬는 날 */
const canCreate = (d) => ['NOT_RECORDED', 'ABSENT'].includes(d.status) && !dayjs(d.date).isAfter(dayjs(), 'day');
/** 정정 대상 기록 — "출근 09:01 · 퇴근 없음 · 퇴근미기록" */
const recordText = (c) => `출근 ${hm(c.checkInAt)} · 퇴근 ${c.checkOutAt ? hm(c.checkOutAt) : '없음'} · ${labelOf('attendanceStatus', c.status)}`;

export default function CorrectionTab() {
  const [target, setTarget] = useState(null); // 정정 창 { mode: 'patch' | 'create', employeeName, workDate, attendanceId?, employeeId, record?, conflict? }
  const [person, setPerson] = useState(null); // 월 근태 서랍 { employeeId, name, orgUnitName }
  const corrections = useQuery({ queryKey: attendanceCorrectionsKey, queryFn: getAttendanceCorrections });
  const list = corrections.data ?? [];

  return (
    <>
      <section className="ad-section">
        <div className="ad-section-head">
          <h2 className="is-20">
            정정이 필요한 기록 <small>{`퇴근을 찍지 않았거나 승인된 휴가 · 출장과 겹친 날 · ${list.length}건`}</small>
          </h2>
        </div>
        <table className="ad-ctable">
          <thead>
            <tr>
              <th style={{ width: '8rem' }}>구분</th>
              <th>직원</th>
              <th style={{ width: '9rem' }}>날짜</th>
              <th style={{ width: '14rem' }}>기록</th>
              <th>겹친 일정</th>
              <th style={{ width: '6rem' }} />
            </tr>
          </thead>
          <tbody>
            {corrections.isLoading ? (
              <tr className="is-empty"><td colSpan={6}><Spin /></td></tr>
            ) : corrections.error ? (
              <tr className="is-empty"><td colSpan={6}>{errorMessage(corrections.error)}</td></tr>
            ) : list.length === 0 ? (
              <tr className="is-empty"><td colSpan={6}>정정이 필요한 기록이 없습니다</td></tr>
            ) : (
              list.map((c) => (
                <tr key={`${c.attendanceId}-${c.type}`}>
                  <td><span className={`hr-badge ${TYPE[c.type]?.[1] ?? 'is-gray'}`}>{TYPE[c.type]?.[0] ?? c.type}</span></td>
                  <td>
                    <button type="button" className="ac-person" onClick={() => setPerson({ employeeId: c.employeeId, name: c.name, orgUnitName: c.orgUnitName, month: c.workDate.slice(0, 7) })}>
                      <strong>{c.name}</strong> <span>{`${c.orgUnitName} · ${c.employeeNo}`}</span>
                    </button>
                  </td>
                  <td>{dateText(c.workDate)}</td>
                  <td>{recordText(c)}</td>
                  <td className="muted">{c.conflict ? `${c.conflict.name} (${c.conflict.startDate.slice(5).replace('-', '.')}–${c.conflict.endDate.slice(5).replace('-', '.')})` : '–'}</td>
                  <td>
                    <button type="button" className="ad-btn" onClick={() => setTarget({ mode: 'patch', attendanceId: c.attendanceId, employeeId: c.employeeId, employeeName: c.name, workDate: c.workDate, record: c, conflict: c.conflict, type: c.type })}>
                      정정
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </section>

      <EmployeeMonthList onPick={setPerson} />

      {person && <MonthDrawer person={person} corrections={list} onClose={() => setPerson(null)} onCorrect={setTarget} />}
      {target && <CorrectModal target={target} onClose={() => setTarget(null)} />}
    </>
  );
}

/* ───── 직원별 월 근태 — 눌러서 그 달 날짜별 기록 보기 ───── */
function EmployeeMonthList({ onPick }) {
  const org = useOrgOptions();
  const [month, setMonth] = useState(dayjs());
  const [orgUnitId, setOrgUnitId] = useState(null);
  const [q, setQ] = useState('');
  const ym = month.format('YYYY-MM');
  const rows = useQuery({
    queryKey: attendanceMonthKey(ym, orgUnitId),
    queryFn: () => fetchAllPages(getAttendanceMonth, { month: ym, orgUnitId: orgUnitId ?? undefined }),
  });
  const shown = useMemo(() => (rows.data ?? []).filter((r) => !q.trim() || r.name.includes(q.trim()) || r.employeeNo.includes(q.trim())), [rows.data, q]);

  return (
    <section className="ad-section">
      <div className="ad-section-head">
        <h2 className="is-20">
          직원별 근태 <small>직원을 누르면 그 달 날짜별 기록을 보고 정정할 수 있습니다</small>
        </h2>
      </div>
      <div className="ac-filters">
        <DatePicker picker="month" value={month} allowClear={false} onChange={(v) => v && setMonth(v)} format="YYYY년 MM월" />
        <Select placeholder="전체 부서" allowClear value={orgUnitId} onChange={setOrgUnitId} options={org.options} showSearch optionFilterProp="label" style={{ width: '12rem' }} />
        <Input.Search placeholder="이름 · 사번" allowClear value={q} onChange={(e) => setQ(e.target.value)} style={{ width: '14rem' }} />
        <span className="muted">{`${shown.length}명`}</span>
      </div>
      <table className="ad-ctable">
        <thead>
          <tr>
            <th style={{ width: '9rem' }}>사번</th>
            <th>이름</th>
            <th>부서</th>
            <th style={{ width: '6rem' }}>지각</th>
            <th style={{ width: '6rem' }}>조퇴</th>
            <th style={{ width: '6rem' }}>결근</th>
            <th style={{ width: '8rem' }}>근무시간</th>
            <th style={{ width: '8rem' }}>연장</th>
          </tr>
        </thead>
        <tbody>
          {rows.isLoading ? (
            <tr className="is-empty"><td colSpan={8}><Spin /></td></tr>
          ) : rows.error ? (
            <tr className="is-empty"><td colSpan={8}>{errorMessage(rows.error)}</td></tr>
          ) : shown.length === 0 ? (
            <tr className="is-empty"><td colSpan={8}>직원이 없습니다</td></tr>
          ) : (
            shown.map((r) => (
              <tr key={r.employeeId} className="ac-row" onClick={() => onPick({ employeeId: r.employeeId, name: r.name, orgUnitName: r.orgUnitName, month: ym })}>
                <td>{r.employeeNo}</td>
                <td className="hr-strong">{r.name}</td>
                <td>{r.orgUnitName}</td>
                <td className={r.summary.lateCount ? 'ac-warn' : 'muted'}>{r.summary.lateCount}</td>
                <td className={r.summary.earlyLeaveCount ? 'ac-warn' : 'muted'}>{r.summary.earlyLeaveCount}</td>
                <td className={r.summary.absentDays ? 'ac-red' : 'muted'}>{r.summary.absentDays}</td>
                <td>{formatMinutes(r.summary.workMinutes)}</td>
                <td>{formatMinutes(r.summary.overtimeMinutes)}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </section>
  );
}

/* ───── 한 직원의 한 달 — 날짜별 정정 · 입력 ───── */
function MonthDrawer({ person, corrections, onClose, onCorrect }) {
  const [month, setMonth] = useState(dayjs(`${person.month}-01`));
  const ym = month.format('YYYY-MM');
  const data = useQuery({ queryKey: employeeAttendancesKey(person.employeeId, ym), queryFn: () => getEmployeeAttendances(person.employeeId, ym) });
  // 정정 대상 목록에 있는 날은 거기의 기록 ID로 고친다
  const idByDate = useMemo(() => Object.fromEntries(corrections.filter((c) => c.employeeId === person.employeeId).map((c) => [c.workDate, c])), [corrections, person.employeeId]);
  const days = data.data?.days ?? [];
  const blocked = days.some((d) => hasRecord(d) && !(d.attendanceId ?? idByDate[d.date]?.attendanceId));

  return (
    <Drawer open title={`${person.name} · ${person.orgUnitName}`} size="large" onClose={onClose} className="ac-drawer">
      <div className="ac-filters">
        <DatePicker picker="month" value={month} allowClear={false} onChange={(v) => v && setMonth(v)} format="YYYY년 MM월" />
        {data.data?.summary && (
          <span className="muted">{`근무 ${formatMinutes(data.data.summary.workMinutes)} · 지각 ${data.data.summary.lateCount} · 조퇴 ${data.data.summary.earlyLeaveCount} · 결근 ${data.data.summary.absentDays}`}</span>
        )}
      </div>
      <table className="ad-ctable ac-days">
        <thead>
          <tr>
            <th style={{ width: '8rem' }}>날짜</th>
            <th>상태</th>
            <th style={{ width: '5rem' }}>근무형태</th>
            <th style={{ width: '5rem' }}>출근</th>
            <th style={{ width: '5rem' }}>퇴근</th>
            <th style={{ width: '6rem' }}>근무</th>
            <th style={{ width: '6rem' }} />
          </tr>
        </thead>
        <tbody>
          {data.isLoading ? (
            <tr className="is-empty"><td colSpan={7}><Spin /></td></tr>
          ) : data.error ? (
            <tr className="is-empty"><td colSpan={7}>{errorMessage(data.error)}</td></tr>
          ) : (
            days.map((d) => {
              const c = idByDate[d.date];
              const id = d.attendanceId ?? c?.attendanceId;
              const off = !d.isWorkday && !d.status;
              return (
                <tr key={d.date} className={off ? 'is-off' : undefined}>
                  <td className={dayjs(d.date).day() === 0 || d.holidayName ? 'ac-red' : undefined}>{dateText(d.date).slice(5)}</td>
                  <td>
                    {d.status ? <span className={`ac-status is-${d.status}`}>{labelOf('attendanceStatus', d.status)}</span> : <span className="muted">{d.holidayName ?? (d.isWorkday ? '–' : '쉬는 날')}</span>}
                    {d.late && <em className="ac-flag">지각</em>}
                    {d.earlyLeave && <em className="ac-flag">조퇴</em>}
                  </td>
                  <td>{d.workType ? labelOf('workType', d.workType) : '–'}</td>
                  <td>{hm(d.checkInAt)}</td>
                  <td>{hm(d.checkOutAt)}</td>
                  <td>{d.workMinutes != null ? formatMinutes(d.workMinutes) : '–'}</td>
                  <td>
                    {hasRecord(d) ? (
                      <button type="button" className="ac-act" disabled={!id} title={id ? undefined : '이 기록을 고치려면 기록 ID가 필요합니다(백엔드 요청 중)'}
                        onClick={() => onCorrect({ mode: 'patch', attendanceId: id, employeeId: person.employeeId, employeeName: person.name, workDate: d.date, record: d, conflict: c?.conflict, type: c?.type })}>
                        정정
                      </button>
                    ) : canCreate(d) ? (
                      <button type="button" className="ac-act is-line" onClick={() => onCorrect({ mode: 'create', employeeId: person.employeeId, employeeName: person.name, workDate: d.date })}>
                        입력
                      </button>
                    ) : null}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
      {blocked && <p className="hr-note">※ 정정 대상이 아닌 출퇴근 기록은 아직 고칠 수 없습니다. 날짜 행에 기록 ID가 오면(백엔드 요청 중) 바로 고칠 수 있게 됩니다.</p>}
    </Drawer>
  );
}

/* ───── 정정 · 입력 창 ───── */
function CorrectModal({ target, onClose }) {
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
        {`${target.employeeName} · ${dateText(target.workDate)}`}
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
