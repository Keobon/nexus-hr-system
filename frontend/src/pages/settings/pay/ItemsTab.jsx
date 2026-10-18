// 수당·공제 항목 탭(Figma 451:8943) + 항목 추가 / 수정 창(453:9063 고정액 · 453:9360 근태 연동 · 453:9651 직접 입력 · 453:10360 기본급 대비 %)
// Figma 의 "적용 시작일"은 서버에 없어 뺐다 — 바꾸면 다음 급여 정산부터 반영된다
import { useState } from 'react';
import { App, Checkbox, Form, Input, InputNumber, Modal, Select, Spin, Switch } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createPayItem, deletePayItem, getPayItems, payItemsKey, updatePayItem } from '../../../api/paySettings';
import { applyFieldErrors } from '../../../components/common/formErrors';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { optionsOf } from '../../../constants/labels';
import { ModalFoot } from '../../hr/parts';
import { CALC_BY_KIND, CALC_LABEL, KindBadge, Lock, amountText, won } from './shared';

const KIND_FILTER = [
  ['ALL', '전체'],
  ['EARNING', '지급'],
  ['DEDUCTION', '공제'],
];
// 칸 너비 — Figma px(140 · 100 · 150 · 160 · 90 · 120 · 100 · 80) ÷ 16
const COLS = [8.75, 6.25, 9.375, 10, 5.625, 7.5, 6.25, 5];

export default function ItemsTab({ modal, setModal, onTab }) {
  const items = useQuery({ queryKey: payItemsKey, queryFn: getPayItems });
  const [kind, setKind] = useState('ALL');
  const [showOff, setShowOff] = useState(false);

  if (items.isLoading) return <Spin />;
  if (items.error) return <p className="hr-notice is-error">{errorMessage(items.error)}</p>;

  const all = items.data ?? [];
  const insurance = all.filter((i) => i.calcMethod === 'TAXABLE_RATE');
  const editable = all.filter((i) => i.calcMethod !== 'TAXABLE_RATE' && (showOff || i.isActive) && (kind === 'ALL' || i.itemKind === kind));
  // 고정 줄 — 기본급(급여 정보) · 4대보험 · 소득세(각 탭에서 관리)
  const showEarning = kind !== 'DEDUCTION';
  const showDeduction = kind !== 'EARNING';

  return (
    <>
      <div className="pay-bar">
        <div className="pay-chips">
          {KIND_FILTER.map(([k, l]) => (
            <button key={k} type="button" className={kind === k ? 'is-on' : undefined} onClick={() => setKind(k)}>
              {l}
            </button>
          ))}
        </div>
        <label className="pay-switch">
          사용 안 함 항목 보기
          <Switch size="small" checked={showOff} onChange={setShowOff} />
        </label>
      </div>

      <div className="hr-table pay-table">
        <div className="hr-tr is-head">
          {['항목명', '구분', '계산 방식', '금액·요율', '과세', '적용 대상', '통상임금', '사용'].map((t, i) => (
            <span key={t} style={{ flex: `${COLS[i]} 0 ${COLS[i]}rem` }}>
              {t}
            </span>
          ))}
        </div>
        {showEarning && (
          <Row cells={['기본급', <KindBadge key="k" kind="EARNING" />, '급여 정보', '직원별 월 기본급', '과세', '전체', '포함', <span key="u" className="pay-use">사용 <Lock /></span>]} locked />
        )}
        {editable.map((it) => (
          <Row
            key={it.id}
            off={!it.isActive}
            onClick={() => setModal({ mode: 'edit', item: it })}
            cells={[
              it.name,
              <KindBadge key="k" kind={it.itemKind} />,
              CALC_LABEL[it.calcMethod],
              amountText(it),
              it.itemKind === 'DEDUCTION' ? '–' : it.isTaxable ? '과세' : (
                <span key="t" className="pay-two">
                  비과세
                  {it.nonTaxableLimit != null && <em>{`월 ${won(it.nonTaxableLimit).replace('원', '')}원 한도`}</em>}
                </span>
              ),
              it.applyTo === 'SELECTED' ? '선택 직원' : '전체',
              it.itemKind === 'DEDUCTION' ? '–' : it.inOrdinaryWage ? '포함' : '제외',
              it.isActive ? '사용' : '사용 안 함',
            ]}
          />
        ))}
        {showDeduction && insurance.length > 0 && (
          <Row
            onClick={() => onTab('insurance')}
            title="4대보험 탭에서 관리합니다"
            cells={[insurance.map((i) => i.name).join(' / '), <KindBadge key="k" kind="DEDUCTION" />, '4대보험', '4대보험 탭에서 관리', '–', '전체', '–', <span key="u" className="pay-use">사용 <Lock /></span>]}
          />
        )}
        {showDeduction && (
          <Row
            onClick={() => onTab('tax')}
            title="소득세 탭에서 관리합니다"
            cells={['소득세 / 지방소득세', <KindBadge key="k" kind="DEDUCTION" />, '소득세 구간', '소득세 탭에서 관리', '–', '전체', '–', <span key="u" className="pay-use">사용 <Lock /></span>]}
          />
        )}
      </div>

      {modal && <ItemModal mode={modal.mode} item={modal.item} items={all.filter((i) => i.calcMethod !== 'TAXABLE_RATE')} onClose={() => setModal(null)} />}
    </>
  );
}

function Row({ cells, onClick, locked, off, title }) {
  return (
    <div className={`hr-tr pay-row${onClick ? ' is-click' : ''}${off ? ' is-off' : ''}${locked ? ' is-locked' : ''}`} onClick={onClick} title={title}>
      {cells.map((c, i) => (
        <span key={i} className={i === 0 ? 'hr-strong' : i === 3 ? 'pay-amount' : undefined} style={{ flex: `${COLS[i]} 0 ${COLS[i]}rem` }}>
          {c}
        </span>
      ))}
    </div>
  );
}

/** 항목 → 폼 값(복사할 때는 이름에 "(복사)") */
const toForm = (it, copy) => ({
  name: copy ? `${it.name} (복사)` : it.name,
  itemKind: it.itemKind,
  calcMethod: it.calcMethod,
  defaultAmount: it.defaultAmount ?? undefined,
  baseRate: it.baseRate ?? undefined,
  attendanceBasis: it.attendanceBasis ?? undefined,
  multiplier: it.multiplier ?? undefined,
  isTaxable: it.isTaxable,
  nonTaxableLimit: it.nonTaxableLimit ?? undefined,
  applyTo: it.applyTo,
  inOrdinaryWage: it.inOrdinaryWage,
  isActive: copy ? true : it.isActive,
});
const EMPTY = { itemKind: 'EARNING', calcMethod: 'FIXED', isTaxable: true, applyTo: 'ALL', inOrdinaryWage: false, isActive: true };
const money = { formatter: (v) => (v ? `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''), parser: (v) => v.replace(/,/g, '') };

function ItemModal({ mode, item, items, onClose }) {
  const notify = useNotify();
  const { modal } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const editing = mode === 'edit';
  const locked = editing && item.inUse; // 쓰인 항목 — 구분 · 계산 방식 · 적용 대상은 못 바꾼다
  const kind = Form.useWatch('itemKind', form) ?? (editing ? item.itemKind : EMPTY.itemKind);
  const calc = Form.useWatch('calcMethod', form) ?? (editing ? item.calcMethod : EMPTY.calcMethod);
  const taxable = Form.useWatch('isTaxable', form);
  const applyTo = Form.useWatch('applyTo', form);
  const earning = kind === 'EARNING';

  const done = (text) => {
    queryClient.invalidateQueries({ queryKey: payItemsKey });
    notify.success(text);
    onClose();
  };
  const save = useMutation({
    mutationFn: (body) => (editing ? updatePayItem(item.id, body) : createPayItem(body)),
    onSuccess: (data) => done(editing ? `${data.name} 항목을 저장했습니다` : `${data.name} 항목을 추가했습니다`),
    onError: (err) => {
      if (!applyFieldErrors(form, err)) notify.error(err);
    },
  });
  const remove = () =>
    modal.confirm({
      title: `${item.name} 항목을 삭제할까요?`,
      content: item.inUse ? '이미 급여에 쓰인 항목이라 지우지 않고 "사용 안 함"으로 바꿉니다.' : '삭제하면 되돌릴 수 없습니다.',
      okText: item.inUse ? '사용 안 함으로' : '삭제',
      okButtonProps: { danger: true },
      cancelText: '취소',
      onOk: async () => {
        try {
          const r = await deletePayItem(item.id);
          done(r?.result === 'DEACTIVATED' ? `${item.name} 항목을 사용 안 함으로 바꿨습니다` : `${item.name} 항목을 삭제했습니다`);
        } catch (err) {
          notify.error(err);
        }
      },
    });

  // 계산 방식마다 쓰는 값만 보낸다(나머지는 서버가 무시)
  const onFinish = (v) => {
    const body = {
      name: v.name.trim(),
      itemKind: v.itemKind,
      calcMethod: v.calcMethod,
      applyTo: v.applyTo,
      isTaxable: earning ? v.isTaxable : true,
      nonTaxableLimit: earning && !v.isTaxable ? (v.nonTaxableLimit ?? null) : null,
      inOrdinaryWage: earning ? !!v.inOrdinaryWage : false,
      defaultAmount: v.calcMethod === 'FIXED' ? (v.defaultAmount ?? null) : null,
      baseRate: v.calcMethod === 'BASE_RATE' ? v.baseRate : null,
      attendanceBasis: v.calcMethod === 'ATTENDANCE' ? v.attendanceBasis : null,
      multiplier: v.calcMethod === 'ATTENDANCE' ? v.multiplier : null,
      isActive: editing ? !!v.isActive : true,
    };
    save.mutate(body);
  };

  const req = (message) => [{ required: true, message }];
  return (
    <Modal open title="항목 추가 / 수정" footer={null} onCancel={onClose} width={560} className="hr-modal pay-modal" destroyOnHidden centered>
      <Form form={form} layout="vertical" requiredMark={false} initialValues={editing ? toForm(item) : EMPTY} onFinish={onFinish}>
        {mode === 'copy' && (
          <Form.Item label="복사할 항목">
            <Select
              placeholder="가져올 항목을 고르세요"
              options={items.map((i) => ({ value: i.id, label: i.name }))}
              onChange={(id) => form.setFieldsValue(toForm(items.find((i) => i.id === id), true))}
            />
          </Form.Item>
        )}
        <Form.Item name="name" label="항목명" rules={req('항목명을 입력하세요')}>
          <Input maxLength={50} placeholder="예: 식대" />
        </Form.Item>
        <Form.Item name="itemKind" label="구분">
          <Select
            disabled={locked}
            options={[
              { value: 'EARNING', label: '지급' },
              { value: 'DEDUCTION', label: '공제' },
            ]}
            onChange={(k) => !CALC_BY_KIND[k].includes(form.getFieldValue('calcMethod')) && form.setFieldValue('calcMethod', 'FIXED')}
          />
        </Form.Item>
        <Form.Item name="calcMethod" label="계산 방식">
          <Select disabled={locked} options={CALC_BY_KIND[kind].filter((c) => c !== 'TAXABLE_RATE').map((c) => ({ value: c, label: CALC_LABEL[c] }))} />
        </Form.Item>

        {calc === 'FIXED' && (
          <Form.Item name="defaultAmount" label="금액(원)" extra={applyTo === 'SELECTED' ? '비우면 직원별 금액만 씁니다' : undefined} rules={applyTo === 'SELECTED' ? [] : req('금액을 입력하세요')}>
            <InputNumber className="full-width" min={0} step={10000} {...money} />
          </Form.Item>
        )}
        {calc === 'BASE_RATE' && (
          <Form.Item name="baseRate" label="비율(%)" rules={req('비율을 입력하세요')}>
            <InputNumber className="full-width" min={0} max={1000} step={0.5} suffix="%" />
          </Form.Item>
        )}
        {calc === 'ATTENDANCE' && (
          <div className="hr-form-row">
            <Form.Item name="attendanceBasis" label="근거 시간" rules={req('근거 시간을 고르세요')}>
              <Select options={optionsOf('attendanceBasis')} />
            </Form.Item>
            <Form.Item name="multiplier" label="배율" rules={req('배율을 입력하세요')}>
              <InputNumber className="full-width" min={0} max={10} step={0.5} />
            </Form.Item>
          </div>
        )}
        {calc === 'MANUAL' && <p className="pay-hint">월 급여 정산 화면에서 직원별로 입력합니다.</p>}
        {calc === 'TRIP_EXPENSE' && <p className="pay-hint">승인된 출장 경비 청구 금액이 그 달 급여에 자동으로 들어갑니다.</p>}

        {earning && (
          <div className="hr-form-row">
            <Form.Item name="isTaxable" label="과세 여부">
              <Select
                options={[
                  { value: true, label: '과세' },
                  { value: false, label: '비과세' },
                ]}
              />
            </Form.Item>
            {taxable === false ? (
              <Form.Item name="nonTaxableLimit" label="비과세 월 한도(원)" extra="비우면 전액 비과세">
                <InputNumber className="full-width" min={0} step={10000} {...money} />
              </Form.Item>
            ) : (
              <div />
            )}
          </div>
        )}
        <Form.Item name="applyTo" label="적용 대상">
          <Select
            disabled={locked}
            options={[
              { value: 'ALL', label: '전체 직원' },
              { value: 'SELECTED', label: '선택 직원' },
            ]}
          />
        </Form.Item>
        {applyTo === 'SELECTED' && <p className="pay-warn">직원별 금액 · 대상은 급여 › 직원 급여에서 설정합니다.</p>}
        <div className="pay-checks">
          {earning && (
            <Form.Item name="inOrdinaryWage" valuePropName="checked">
              <Checkbox>통상임금 포함</Checkbox>
            </Form.Item>
          )}
          {editing && (
            <Form.Item name="isActive" valuePropName="checked">
              <Checkbox>사용</Checkbox>
            </Form.Item>
          )}
        </div>
        {locked && <p className="pay-hint">이미 급여에 쓰인 항목이라 구분 · 계산 방식 · 적용 대상은 바꿀 수 없습니다.</p>}
        <p className="hr-notice">변경 내용은 다음 급여 정산부터 반영됩니다. 확정된 급여는 바뀌지 않습니다.</p>
        <div className="pay-modal-foot">
          {editing ? (
            <button type="button" className="hr-link is-red" onClick={remove}>
              {item.inUse ? '사용 안 함으로' : '항목 삭제'}
            </button>
          ) : (
            <span />
          )}
          <ModalFoot onCancel={onClose} busy={save.isPending} />
        </div>
      </Form>
    </Modal>
  );
}
