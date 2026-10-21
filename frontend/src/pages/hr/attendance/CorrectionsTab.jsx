// 정정 대상(Figma 484-27817 · 482-11916) — 왼쪽 정정 대상 목록, 오른쪽 상세 · 정정 입력(ATTENDANCE_MANAGE)
// Figma의 "소명 신청"(직원이 사유 · 희망 시각을 내는 기능)은 서버에 없어 뺐다 — 목록은 퇴근미기록 · 휴가·출장 충돌만
// 정산된 달을 고치면 서버가 warning PAY_MONTH_SETTLED 를 준다 → 위에 노란 안내(Figma 484-27817)
import { useMemo, useState } from 'react';
import { Form, Input, Select, TimePicker } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { attendanceCorrectionsKey, correctAttendance, getAttendanceCorrections } from '../../../api/attendance';
import { meKey } from '../../../api/auth';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { Pill, hm } from './shared';

const KIND = {
  MISSING_CHECKOUT: ['퇴근미기록', 'alert'],
  LEAVE_CONFLICT: ['휴가 충돌', 'purple'],
  TRIP_CONFLICT: ['출장 충돌', 'purple'],
};
const CHIPS = [
  ['ALL', '전체', () => true],
  ['MISSING', '퇴근미기록', (c) => c.type === 'MISSING_CHECKOUT'],
  ['CONFLICT', '휴가·출장 충돌', (c) => c.type !== 'MISSING_CHECKOUT'],
];
const keyOf = (c) => `${c.attendanceId}-${c.type}`;

export default function CorrectionsTab() {
  const list = useQuery({ queryKey: attendanceCorrectionsKey, queryFn: getAttendanceCorrections });
  const all = useMemo(() => list.data ?? [], [list.data]);
  const [chip, setChip] = useState('ALL');
  const [pick, setPick] = useState(null);
  const [settledWarn, setSettledWarn] = useState(false);
  const rows = all.filter(CHIPS.find(([k]) => k === chip)[2]);
  const selected = rows.find((c) => keyOf(c) === pick) ?? rows[0];

  return (
    <>
      {settledWarn && <div className="at-banner">이미 정산된 월입니다. 차액은 다음 달 소급 조정으로 처리하세요.</div>}
      <div className="at-split">
        <section className="at-card">
          <div className="at-card-head is-col">
            <div>
              <strong>정정 대상 목록</strong>
              <p className="at-muted">{`총 ${all.length}건 · 퇴근을 찍지 않았거나 승인된 휴가 · 출장과 겹친 날`}</p>
            </div>
            <span className="at-count">{`${all.length}건`}</span>
          </div>
          <div className="at-chips">
            {CHIPS.map(([k, label, pred]) => (
              <button key={k} type="button" className={chip === k ? 'is-on' : undefined} onClick={() => setChip(k)}>
                {`${label} ${all.filter(pred).length}`}
              </button>
            ))}
          </div>
          <div className="at-table-wrap">
            <table className="at-table">
              <thead>
                <tr>
                  <th style={{ width: '6.5rem' }}>날짜</th>
                  <th style={{ width: '4.5rem' }}>이름</th>
                  <th>소속</th>
                  <th style={{ width: '6.5rem' }}>종류</th>
                  <th>현재 기록</th>
                </tr>
              </thead>
              <tbody>
                {list.isLoading || list.error || rows.length === 0 ? (
                  <tr className="is-empty">
                    <td colSpan={5}>{list.isLoading ? '불러오는 중…' : list.error ? errorMessage(list.error) : '정정할 기록이 없습니다'}</td>
                  </tr>
                ) : (
                  rows.map((c) => (
                    <tr key={keyOf(c)} className={`is-click${selected && keyOf(selected) === keyOf(c) ? ' is-on' : ''}`} onClick={() => setPick(keyOf(c))}>
                      <td className="at-strong">{c.workDate}</td>
                      <td>{c.name}</td>
                      <td>{c.orgUnitName}</td>
                      <td>
                        <Pill tone={KIND[c.type][1]}>{KIND[c.type][0]}</Pill>
                      </td>
                      <td>{`출근 ${hm(c.checkInAt)} · 퇴근 ${c.checkOutAt ? hm(c.checkOutAt) : '–'}`}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section className="at-card at-detail">
          {selected ? (
            <Detail key={keyOf(selected)} c={selected} onSettled={() => setSettledWarn(true)} />
          ) : (
            <p className="at-empty">왼쪽 목록에서 정정할 기록을 고르세요.</p>
          )}
        </section>
      </div>
    </>
  );
}

function Detail({ c, onSettled }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const status = Form.useWatch('status', form);
  const conflict = c.type !== 'MISSING_CHECKOUT';
  const isToday = c.workDate === dayjs().format('YYYY-MM-DD');
  const options = [
    { value: 'CHECKED_OUT', label: '퇴근' },
    ...(isToday ? [{ value: 'CHECKED_IN', label: '출근(퇴근 전)' }] : []),
    { value: 'ON_VACATION', label: '휴가' },
    { value: 'ON_BUSINESS_TRIP', label: '출장' },
    ...(conflict ? [{ value: 'KEEP', label: '기록 그대로 두기(확인만)' }] : []),
  ];
  const at = (t) => (t ? dayjs(`${c.workDate}T${t.format('HH:mm')}:00`).format() : null);
  const timed = status === 'CHECKED_OUT' || status === 'CHECKED_IN';

  const save = useMutation({
    mutationFn: (v) => {
      const reason = v.reason.trim();
      if (v.status === 'KEEP') return correctAttendance(c.attendanceId, { reason });
      const t = v.status === 'CHECKED_OUT' || v.status === 'CHECKED_IN';
      return correctAttendance(c.attendanceId, {
        status: v.status,
        ...(t ? { workType: v.workType, checkInAt: at(v.checkIn), checkOutAt: v.status === 'CHECKED_OUT' ? at(v.checkOut) : null } : {}),
        reason,
      });
    },
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
      queryClient.invalidateQueries({ queryKey: meKey });
      notify.success(`${c.name} ${c.workDate} 근태를 정정했습니다`);
      if (res?.warning === 'PAY_MONTH_SETTLED') onSettled();
    },
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });

  return (
    <>
      <div className="at-detail-head">
        <strong>정정 대상 상세</strong>
        <p className="at-muted">{conflict ? '승인된 일정과 겹친 기록을 확인하고, 정정 내용을 입력합니다.' : '직원의 미기록 내역을 확인하고, 정정 내용을 입력합니다.'}</p>
        <p className="at-strong">{`${c.name} · ${c.orgUnitName} · ${c.workDate.replaceAll('-', '.')}`}</p>
      </div>

      <div className="at-kv">
        <h4>현재 기록</h4>
        <div>
          <span>상태</span>
          <Pill tone={KIND[c.type][1]}>{c.type === 'MISSING_CHECKOUT' ? '퇴근미기록' : labelOf('attendanceStatus', c.status)}</Pill>
        </div>
        <div>
          <span>출근 시각</span>
          <strong>{hm(c.checkInAt)}</strong>
        </div>
        <div>
          <span>퇴근 시각</span>
          <strong>{hm(c.checkOutAt)}</strong>
        </div>
        <div>
          <span>근무 형태</span>
          <strong>{c.workType ? labelOf('workType', c.workType) : '–'}</strong>
        </div>
      </div>

      {c.conflict && (
        <div className="at-note-box">
          <span>{c.type === 'LEAVE_CONFLICT' ? '승인된 휴가' : '승인된 출장'}</span>
          <p>{`${c.conflict.name} · ${c.conflict.startDate.replaceAll('-', '.')} ~ ${c.conflict.endDate.replaceAll('-', '.')}`}</p>
        </div>
      )}

      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        className="at-form"
        initialValues={{
          status: conflict ? (c.type === 'LEAVE_CONFLICT' ? 'ON_VACATION' : 'ON_BUSINESS_TRIP') : 'CHECKED_OUT',
          workType: c.workType && c.workType !== 'BUSINESS_TRIP' ? c.workType : 'OFFICE',
          checkIn: c.checkInAt ? dayjs(c.checkInAt) : dayjs(`${c.workDate}T09:00`),
          checkOut: c.checkOutAt ? dayjs(c.checkOutAt) : dayjs(`${c.workDate}T18:00`),
        }}
        onFinish={(v) => save.mutate(v)}
      >
        <h4>정정 입력</h4>
        <Form.Item name="status" label="상태 *" extra={`선택 가능 상태: ${options.map((o) => o.label).join(' / ')}`}>
          <Select options={options} />
        </Form.Item>
        {timed && (
          <>
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
            <Form.Item name="workType" label="근무 형태">
              <Select options={['OFFICE', 'REMOTE', 'FIELD'].map((v) => ({ value: v, label: labelOf('workType', v) }))} />
            </Form.Item>
          </>
        )}
        {(status === 'ON_VACATION' || status === 'ON_BUSINESS_TRIP') && <p className="at-muted">그날 승인된 휴가 · 출장이 있어야 합니다. 출퇴근 시각은 지워집니다.</p>}
        {status === 'KEEP' && <p className="at-muted">기록은 바꾸지 않고 확인했다는 사유만 남깁니다.</p>}
        <Form.Item name="reason" label="사유 *" rules={[{ required: true, whitespace: true, message: '정정 사유를 입력하세요' }]}>
          <Input.TextArea maxLength={255} autoSize={{ minRows: 3, maxRows: 5 }} placeholder="정정 사유를 입력하세요." />
        </Form.Item>
        <p className="at-muted is-11">※ 정정 내용은 감사 로그에 남습니다.</p>
        <div className="at-form-foot">
          <button type="submit" className="at-btn is-wide" disabled={save.isPending}>
            정정 저장
          </button>
        </div>
      </Form>
    </>
  );
}
