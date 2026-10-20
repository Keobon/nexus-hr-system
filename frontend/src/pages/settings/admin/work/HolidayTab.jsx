// 휴일 탭 — GET /holidays?year(매년 반복은 그 해 날짜로 펼쳐서 옴) · POST · PATCH · DELETE (Figma Admin_Dayoff)
import { useState } from 'react';
import { Checkbox, DatePicker, Form, Input, Modal, Select, Spin } from 'antd';
import dayjs from 'dayjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createHoliday, deleteHoliday, getHolidays, holidaysKey, updateHoliday } from '../../../../api/workSchedule';
import { applyFieldErrors } from '../../../../components/common/formErrors';
import { useNotify } from '../../../../components/common/useNotify';
import { errorMessage } from '../../../../constants/errors';
import { labelOf, optionsOf } from '../../../../constants/labels';
import { ConfirmModal, ModalFoot } from '../../../hr/parts';
import { toDay, ymd } from '../parts';

export default function HolidayTab() {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [year, setYear] = useState(dayjs().year());
  const list = useQuery({ queryKey: holidaysKey(year), queryFn: () => getHolidays(year) });
  const [edit, setEdit] = useState(null);
  const [removing, setRemoving] = useState(null);
  const rows = list.data ?? [];

  return (
    <>
      <div className="ad-bar">
        <div className="ad-year">
          <button type="button" className="prev" aria-label="이전 해" onClick={() => setYear((y) => y - 1)} />
          <span>{year}</span>
          <button type="button" className="next" aria-label="다음 해" onClick={() => setYear((y) => y + 1)} />
        </div>
        <button type="button" className="ad-btn is-lg" onClick={() => setEdit({ holidayDate: `${year}-01-01`, isNew: true })}>+ 휴일 추가</button>
      </div>
      <section className="ad-section" style={{ padding: '1rem' }}>
        <table className="ad-ctable">
          <thead>
            <tr><th style={{ width: '10rem' }}>일자</th><th>휴일</th><th style={{ width: '9rem' }}>유형</th><th style={{ width: '7rem' }}>반복</th><th style={{ width: '8rem' }} /></tr>
          </thead>
          <tbody>
            {list.isLoading ? (
              <tr className="is-empty"><td colSpan={5}><Spin /></td></tr>
            ) : list.error ? (
              <tr className="is-empty"><td colSpan={5}>{errorMessage(list.error)}</td></tr>
            ) : rows.length === 0 ? (
              <tr className="is-empty"><td colSpan={5}>{`${year}년에 등록된 휴일이 없습니다`}</td></tr>
            ) : rows.map((h) => (
              <tr key={`${h.id}-${h.holidayDate}`}>
                <td>{h.holidayDate}</td>
                <td>{h.name}</td>
                <td><span className={`ad-pill is-${h.holidayType}`}>{labelOf('holidayType', h.holidayType)}</span></td>
                <td>{h.isRecurring ? '● 매년' : '-'}</td>
                <td>
                  <span className="ad-stack">
                    <button type="button" onClick={() => setEdit(h)}>수정</button>
                    <button type="button" className="is-red" onClick={() => setRemoving(h)}>삭제</button>
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
      <p className="hr-note">※ 이미 신청된 휴가의 일수는 다시 계산하지 않습니다.</p>

      {edit && <HolidayModal item={edit} onClose={() => setEdit(null)} />}
      <ConfirmModal open={!!removing} danger okText="삭제" title="휴일 삭제"
        desc={removing ? `${removing.name}${removing.isRecurring ? '(매년 반복 — 모든 해에서 지워집니다)' : ''}을(를) 삭제합니다. 계속하시겠습니까?` : ''}
        onClose={() => setRemoving(null)}
        onConfirm={async () => {
          await deleteHoliday(removing.id);
          queryClient.invalidateQueries({ queryKey: ['holidays'] });
          notify.success(`${removing.name}을(를) 삭제했습니다`);
        }} />
    </>
  );
}

function HolidayModal({ item, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const isNew = !item.id;
  const save = useMutation({
    mutationFn: (body) => (isNew ? createHoliday(body) : updateHoliday(item.id, body)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['holidays'] });
      notify.success(isNew ? '휴일을 추가했습니다' : '휴일을 저장했습니다');
      onClose();
    },
    onError: (err) => {
      if (err?.code === 'INVALID_STATE') { form.setFields([{ name: 'holidayDate', errors: [errorMessage(err)] }]); return; }
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });
  return (
    <Modal open title={isNew ? '휴일 추가' : '휴일 수정'} footer={null} onCancel={onClose} width={480} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">이미 신청된 휴가의 일수는 다시 계산하지 않습니다.</p>
      <Form form={form} layout="vertical" requiredMark
        initialValues={{ holidayDate: toDay(item.holidayDate), name: item.name, holidayType: item.holidayType ?? 'COMPANY', isRecurring: item.isRecurring ?? false }}
        onFinish={(v) => save.mutate({ holidayDate: ymd(v.holidayDate), name: v.name.trim(), holidayType: v.holidayType, isRecurring: v.isRecurring })}>
        <Form.Item name="holidayDate" label="날짜" rules={[{ required: true, message: '날짜를 고르세요' }]}>
          <DatePicker className="full-width" />
        </Form.Item>
        <Form.Item name="name" label="이름" rules={[{ required: true, whitespace: true, message: '휴일 이름을 입력하세요' }]}>
          <Input maxLength={50} placeholder="예: 창립기념일" />
        </Form.Item>
        <Form.Item name="holidayType" label="종류" rules={[{ required: true, message: '종류를 고르세요' }]}>
          <Select options={optionsOf('holidayType')} />
        </Form.Item>
        <Form.Item name="isRecurring" valuePropName="checked" style={{ marginBottom: 0 }}>
          <Checkbox>매년 반복</Checkbox>
        </Form.Item>
        <ModalFoot onCancel={onClose} busy={save.isPending} okText={isNew ? '추가' : '저장'} />
      </Form>
    </Modal>
  );
}
