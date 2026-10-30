// 승인함(F-APPR-01 · 02) — Figma 112-5774. 내가 현재 단계 승인자인 휴가 · 휴가 취소 · 연장근무 · 출장 · 출장 경비를 승인 · 반려한다
// 승인자는 신청할 때 승인선으로 정해진다(소속 조직장, 본인 · 공석이면 위 조직장, 최상위면 자동 승인 — 회사 설정 › 승인선)
// 반려는 사유 필수. 연장근무는 인정 시간을 줄여 승인할 수 있다(앞 단계 값 이하)
import { useMemo, useState } from 'react';
import { Drawer, Form, Input, InputNumber, Modal } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { approveStep, getHistoryPage, getInboxPage, historyKey, inboxKey, rejectStep } from '../../../api/approvals';
import { meKey } from '../../../api/auth';
import { fetchAllPages } from '../../../api/employees';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { PageHead } from '../../hr/parts';
import { Pill, Table } from '../../hr/attendance/shared';
import '../../hr/attendance/at.css';
import './approvals.css';

const KIND = {
  LEAVE: ['휴가 신청', 'info'],
  LEAVE_CANCEL: ['휴가 취소', 'warn'],
  OVERTIME: ['연장근무', 'purple'],
  BUSINESS_TRIP: ['출장', 'ok'],
  TRIP_EXPENSE: ['출장 경비', 'gray'],
};
const FILTERS = ['ALL', 'LEAVE', 'LEAVE_CANCEL', 'OVERTIME', 'BUSINESS_TRIP', 'TRIP_EXPENSE'];
const hm = (t) => (t ? dayjs(t).format('HH:mm') : '');
const range = (a, b) => (!a ? '–' : a === b ? a : `${a} ~ ${b}`);
const hhmm = (min) => (min == null ? '–' : `${Math.floor(min / 60)}시간${min % 60 ? ` ${min % 60}분` : ''}`);
const won = (n) => `${Number(n ?? 0).toLocaleString('ko-KR')}원`;

/** 한 줄 요약 칸 — 종류마다 내용 · 기간 · 일수 · 사유 */
function cells(it) {
  const d = it.details ?? {};
  switch (it.workType) {
    case 'LEAVE':
    case 'LEAVE_CANCEL':
      return { what: d.leaveTypeName, when: range(d.startDate, d.endDate), amount: d.days != null ? `${d.days}일` : '–', why: it.workType === 'LEAVE_CANCEL' ? d.cancelReason || d.reason : d.reason, remain: d.remaining };
    case 'OVERTIME':
      return { what: `${hm(d.plannedStart)}–${hm(d.plannedEnd)}`, when: d.workDate, amount: hhmm(it.requestedMinutes), why: d.reason };
    case 'BUSINESS_TRIP':
      return { what: `${labelOf('tripType', d.tripType)} · ${d.destination}`, when: range(d.startDate, d.endDate), amount: '–', why: d.purpose };
    case 'TRIP_EXPENSE':
      return { what: `${d.destination} 경비`, when: range(d.startDate, d.endDate), amount: won(d.totalAmount), why: `경비 ${d.lineCount ?? 0}건` };
    default:
      return { what: it.title, when: '–', amount: '–', why: '' };
  }
}

export default function ApprovalInboxPage() {
  const [tab, setTab] = useState('inbox');
  const [filter, setFilter] = useState('ALL');
  const [action, setAction] = useState(null); // { kind: 'approve' | 'reject', item }
  const [detail, setDetail] = useState(null);
  const inbox = useQuery({ queryKey: inboxKey, queryFn: () => fetchAllPages(getInboxPage, {}) });
  const history = useQuery({ queryKey: historyKey, queryFn: () => getHistoryPage({ size: 50 }), enabled: tab === 'history' });
  const all = useMemo(() => inbox.data ?? [], [inbox.data]);
  const rows = all.filter((it) => filter === 'ALL' || it.workType === filter);
  const count = (k) => (k === 'ALL' ? all.length : all.filter((it) => it.workType === k).length);

  return (
    <div className="hr at ap">
      <PageHead title="승인함" desc="내가 승인할 휴가 신청 · 취소 요청과 연장근무 · 출장 · 경비 신청을 확인하고 승인 또는 반려합니다." />

      <nav className="at-tabs" role="tablist" aria-label="승인함 탭">
        <button type="button" role="tab" aria-selected={tab === 'inbox'} className={tab === 'inbox' ? 'is-on' : undefined} onClick={() => setTab('inbox')}>
          승인 대기
          {all.length ? <em>{all.length}</em> : null}
        </button>
        <button type="button" role="tab" aria-selected={tab === 'history'} className={tab === 'history' ? 'is-on' : undefined} onClick={() => setTab('history')}>
          처리 내역
        </button>
      </nav>

      {tab === 'inbox' ? (
        <>
          <section className="at-card">
            <div className="at-card-head">
              <strong>{`승인 대기 ${rows.length}건`}</strong>
              <div className="at-chips ap-chips">
                {FILTERS.map((k) => (
                  <button key={k} type="button" className={filter === k ? 'is-on' : undefined} onClick={() => setFilter(k)}>
                    {`${k === 'ALL' ? '전체' : KIND[k][0]} ${count(k)}`}
                  </button>
                ))}
              </div>
            </div>
            <Table
              cols={[
                { key: 'who', label: '신청자', width: '6.5rem' },
                { key: 'org', label: '소속 부서', width: '8rem' },
                { key: 'kind', label: '구분', width: '6.5rem' },
                { key: 'what', label: '유형 · 내용', width: '9rem' },
                { key: 'when', label: '기간', width: '12rem' },
                { key: 'amount', label: '일수 · 시간', width: '6rem' },
                { key: 'why', label: '사유' },
                { key: 'remain', label: '잔여휴가', width: '5.5rem' },
                { key: 'step', label: '단계', width: '4.5rem' },
                { key: 'act', label: '처리', width: '9rem' },
              ]}
              loading={inbox.isLoading}
              error={inbox.error && errorMessage(inbox.error)}
              empty={rows.length === 0 && '승인할 신청이 없습니다'}
            >
              {rows.map((it) => {
                const c = cells(it);
                return (
                  <tr key={it.stepId} className="is-click" onClick={() => setDetail(it)}>
                    <td className="at-strong">{it.applicantName}</td>
                    <td>{it.applicantOrgUnitName}</td>
                    <td>
                      <Pill tone={KIND[it.workType]?.[1]}>{KIND[it.workType]?.[0] ?? it.workType}</Pill>
                    </td>
                    <td className="at-ellipsis" title={c.what}>{c.what}</td>
                    <td>{c.when}</td>
                    <td>{c.amount}</td>
                    <td className="at-ellipsis" title={c.why}>{c.why || '–'}</td>
                    <td className="ap-remain">{c.remain != null ? `${c.remain}일` : '–'}</td>
                    <td>{`${it.stepOrder}/${it.totalSteps}`}</td>
                    <td onClick={(e) => e.stopPropagation()}>
                      <span className="ap-acts">
                        <button type="button" className="ap-btn is-ok" onClick={() => setAction({ kind: 'approve', item: it })}>
                          승인
                        </button>
                        <button type="button" className="ap-btn is-no" onClick={() => setAction({ kind: 'reject', item: it })}>
                          반려
                        </button>
                      </span>
                    </td>
                  </tr>
                );
              })}
            </Table>
          </section>
          <p className="at-muted">※ 내가 현재 단계 승인자인 신청만 표시됩니다. 승인할 수 없는 건은 반려 시 사유를 입력합니다. 승인자는 회사 설정 › 승인선 기준으로 신청할 때 정해집니다.</p>
        </>
      ) : (
        <section className="at-card">
          <div className="at-card-head">
            <strong>처리 내역</strong>
            <span>최근 50건</span>
          </div>
          <Table
            cols={[
              { key: 'at', label: '처리일시', width: '9rem' },
              { key: 'who', label: '신청자', width: '6.5rem' },
              { key: 'kind', label: '구분', width: '6.5rem' },
              { key: 'title', label: '내용' },
              { key: 'st', label: '결과', width: '6rem' },
              { key: 'cmt', label: '의견' },
            ]}
            loading={history.isLoading}
            error={history.error && errorMessage(history.error)}
            empty={(history.data?.content ?? []).length === 0 && '처리한 신청이 없습니다'}
          >
            {(history.data?.content ?? []).map((h) => (
              <tr key={h.stepId}>
                <td>{h.actedAt ? dayjs(h.actedAt).format('YYYY-MM-DD HH:mm') : '–'}</td>
                <td className="at-strong">{h.applicantName}</td>
                <td>
                  <Pill tone={KIND[h.workType]?.[1]}>{KIND[h.workType]?.[0] ?? h.workType}</Pill>
                </td>
                <td className="at-ellipsis" title={h.title}>
                  {h.title}
                  {h.workType === 'OVERTIME' && h.approvedMinutes != null ? ` · 인정 ${hhmm(h.approvedMinutes)}` : ''}
                </td>
                <td>
                  <Pill tone={h.status === 'APPROVED' ? 'ok' : 'alert'}>{labelOf('approvalStepStatus', h.status)}</Pill>
                </td>
                <td className="at-ellipsis" title={h.comment ?? ''}>{h.comment || '–'}</td>
              </tr>
            ))}
          </Table>
        </section>
      )}

      {detail && <DetailDrawer item={detail} onClose={() => setDetail(null)} onAction={(kind) => { setAction({ kind, item: detail }); setDetail(null); }} />}
      {action && <DecisionModal action={action} onClose={() => setAction(null)} />}
    </div>
  );
}

/* ───── 상세 — 신청 내용 · 앞 단계 처리 내역 ───── */
function DetailDrawer({ item, onClose, onAction }) {
  const c = cells(item);
  const d = item.details ?? {};
  return (
    <Drawer open onClose={onClose} size="default" title={`${KIND[item.workType]?.[0] ?? ''} · ${item.applicantName}`} className="ap-drawer">
      <div className="ap-kv">
        {[
          ['신청자', `${item.applicantName} · ${item.applicantOrgUnitName}`],
          ['내용', c.what],
          ['기간', c.when],
          ['일수 · 시간', c.amount],
          ...(c.remain != null ? [['잔여휴가', `${c.remain}일`]] : []),
          [item.workType === 'BUSINESS_TRIP' ? '목적' : '사유', c.why || '–'],
          ...(item.workType === 'LEAVE_CANCEL' && d.reason ? [['원래 신청 사유', d.reason]] : []),
          ['현재 단계', `${item.stepOrder}/${item.totalSteps}단계`],
        ].map(([k, v]) => (
          <div key={k}>
            <span>{k}</span>
            <strong>{v}</strong>
          </div>
        ))}
      </div>
      <h4 className="ap-sub">앞 단계 처리 내역</h4>
      {(item.previousSteps ?? []).length === 0 ? (
        <p className="at-muted">첫 단계입니다.</p>
      ) : (
        <ol className="ap-steps">
          {item.previousSteps.map((s) => (
            <li key={s.stepId}>
              <strong>{`${s.stepOrder}단계 ${s.approverName ?? ''} · ${labelOf('approvalStepStatus', s.status)}`}</strong>
              <span>{[s.actedAt && dayjs(s.actedAt).format('YYYY-MM-DD HH:mm'), s.approvedMinutes != null && `인정 ${hhmm(s.approvedMinutes)}`, s.comment].filter(Boolean).join(' · ') || '–'}</span>
            </li>
          ))}
        </ol>
      )}
      <div className="ap-drawer-foot">
        <button type="button" className="ap-btn is-no is-lg" onClick={() => onAction('reject')}>
          반려
        </button>
        <button type="button" className="ap-btn is-ok is-lg" onClick={() => onAction('approve')}>
          승인
        </button>
      </div>
    </Drawer>
  );
}

/* ───── 승인 · 반려 창 — 반려는 사유 필수, 연장근무 승인은 인정 시간 ───── */
function DecisionModal({ action, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const { kind, item } = action;
  const approve = kind === 'approve';
  const overtime = approve && item.workType === 'OVERTIME';
  // 연장근무 인정 시간 상한 — 앞 단계가 인정한 값, 없으면 신청 시간
  const lastApproved = [...(item.previousSteps ?? [])].reverse().find((s) => s.approvedMinutes != null)?.approvedMinutes;
  const maxMinutes = lastApproved ?? item.requestedMinutes ?? 0;

  const save = useMutation({
    mutationFn: (v) =>
      approve
        ? approveStep(item.stepId, { comment: v.comment?.trim() || null, ...(overtime ? { approvedMinutes: v.hours * 60 + (v.minutes ?? 0) } : {}) })
        : rejectStep(item.stepId, v.comment.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['approvals'] });
      queryClient.invalidateQueries({ queryKey: meKey });
      queryClient.invalidateQueries({ queryKey: ['leave-requests'] });
      notify.success(`${item.applicantName}님의 ${KIND[item.workType]?.[0] ?? '신청'}을(를) ${approve ? '승인' : '반려'}했습니다`);
      onClose();
    },
    onError: (err) => notify.error(err),
  });

  return (
    <Modal open title={approve ? '승인' : '반려'} footer={null} onCancel={onClose} width={460} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{`${item.applicantName} · ${item.title}`}</p>
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{ hours: Math.floor(maxMinutes / 60), minutes: maxMinutes % 60 }}
        onFinish={(v) => {
          if (overtime && v.hours * 60 + (v.minutes ?? 0) > maxMinutes) {
            form.setFields([{ name: 'hours', errors: [`${hhmm(maxMinutes)} 이하로만 인정할 수 있습니다`] }]);
            return;
          }
          save.mutate(v);
        }}
      >
        {overtime && (
          <Form.Item
            label="인정 시간"
            extra={`신청 ${hhmm(item.requestedMinutes)}${lastApproved != null ? ` · 앞 단계 인정 ${hhmm(lastApproved)}` : ''} — 이 시간 이하로만 인정할 수 있습니다`}
          >
            <div className="ap-time">
              <Form.Item name="hours" noStyle rules={[{ required: true }]}>
                <InputNumber min={0} max={Math.floor(maxMinutes / 60)} precision={0} />
              </Form.Item>
              <span>시간</span>
              <Form.Item name="minutes" noStyle>
                <InputNumber min={0} max={59} step={10} precision={0} />
              </Form.Item>
              <span>분</span>
            </div>
          </Form.Item>
        )}
        <Form.Item
          name="comment"
          label={approve ? '의견 (선택)' : '반려 사유'}
          rules={approve ? [] : [{ required: true, whitespace: true, message: '반려 사유를 입력하세요' }]}
        >
          <Input.TextArea maxLength={500} autoSize={{ minRows: 3, maxRows: 5 }} placeholder={approve ? '예: 일정 확인했습니다' : '예: 해당 기간 팀 일정이 겹쳐 조정이 필요합니다'} />
        </Form.Item>
        <div className="hr-modal-foot">
          <button type="button" className="hr-btn is-secondary" onClick={onClose}>
            취소
          </button>
          <button type="submit" className={`hr-btn${approve ? '' : ' is-danger'}`} disabled={save.isPending}>
            {approve ? '승인' : '반려'}
          </button>
        </div>
      </Form>
    </Modal>
  );
}
