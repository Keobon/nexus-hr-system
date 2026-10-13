import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { App, Input, Modal, Result, Select, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { getWorkSchedules, workSchedulesKey } from '../../../api/attendance';
import { dashboardKey } from '../../../api/dashboard';
import {
  cancelLeave,
  getLeaveRequest,
  getLeaveTypes,
  getMyLeaveBalances,
  getMyLeaveRequests,
  getTeamLeaves,
  leaveRequestKey,
  leaveTypesKey,
  myLeaveBalancesKey,
  myLeaveRequestsKey,
  previewLeave,
  requestLeave,
  teamLeavesKey,
  withdrawLeave,
} from '../../../api/leaves';
import { useMe } from '../../../auth/useMe';
import ApprovalSteps from '../../../components/common/ApprovalSteps';
import { useNotify } from '../../../components/common/useNotify';
import { usePendingFeature } from '../../../components/common/usePendingFeature';
import { errorMessage } from '../../../constants/errors';
import iconChevron from '../../../assets/figma/icon-chevron.svg';
import iconPaperclip from '../../../assets/figma/icon-paperclip-sm.svg';
import './leave.css';

const STATUS_LABEL = { PENDING: '승인 대기', APPROVED: '결재 완료', REJECTED: '반려', CANCEL_REQUESTED: '취소 요청', CANCELLED: '취소 완료' };
const LIVE = ['PENDING', 'APPROVED', 'CANCEL_REQUESTED'];
const DRAFT_KEY = 'nexus.leaveDraft';
const PERIODS = [
  { value: 3, label: '최근 3개월' },
  { value: 6, label: '최근 6개월' },
  { value: 12, label: '최근 1년' },
  { value: 0, label: '전체' },
];

const dot = (d) => (d ? d.replaceAll('-', '.') : '-');
/** 기간 표시 — 하루면 날짜 하나, 같은 달이면 "2026.07.06–08" */
function period(start, end) {
  if (start === end) return dot(start);
  if (start.slice(0, 7) === end.slice(0, 7)) return `${dot(start)}–${end.slice(8)}`;
  return `${dot(start)}–${dot(end).slice(5)}`;
}
/** 결재선 단계 — 미리보기에 stepLabel(조직장 · 직책명 등)이 오면 "조직장(윤서연)", 없으면 이름만(노션 수정사항 95) */
const stepName = (s) => (s.stepLabel ? `${s.stepLabel}(${s.approverName})` : s.approverName);
const fmtDays = (n) => `${Number.isInteger(n) ? n : n.toFixed(1)}일`;

function readDraft() {
  try {
    return JSON.parse(localStorage.getItem(DRAFT_KEY) ?? 'null');
  } catch {
    return null;
  }
}

/** 입력이 멈추고 300ms 뒤의 값(미리보기 호출 줄이기 — 프론트 가이드 3.2) */
function useDebounced(value, ms = 300) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setV(value), ms);
    return () => clearTimeout(id);
  }, [value, ms]);
  return v;
}

/** 날짜 칸 어디를 눌러도 브라우저 달력을 연다(달력 아이콘 자리를 Figma 화살표가 대신함) */
function openPicker(e) {
  const input = e.currentTarget.querySelector("input");
  try {
    input.showPicker();
  } catch {
    input.focus();
  }
}

/* ───── 휴가 신청 폼 ───── */
/** 휴가 신청 폼 — [휴가 신청] 버튼으로 여는 창 안에 들어간다. 신청이 끝나면 onDone */
function RequestForm({ types, schedule, dailyHours, onDone }) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const pending = usePendingFeature();
  const draft = useMemo(readDraft, []);
  const [typeId, setTypeId] = useState(draft?.typeId ?? types[0]?.id);
  const [startDate, setStartDate] = useState(draft?.startDate ?? '');
  const [endDate, setEndDate] = useState(draft?.endDate ?? '');
  const [reason, setReason] = useState(draft?.reason ?? '');

  const params = useDebounced({ leaveTypeId: typeId, startDate, endDate });
  const ready = params.leaveTypeId && params.startDate && params.endDate;
  const preview = useQuery({
    queryKey: ['leave-preview', params],
    queryFn: () => previewLeave(params),
    enabled: !!ready,
    retry: false,
  });
  const p = ready ? preview.data : null;
  const errors = p?.errors ?? [];
  const type = types.find((t) => t.id === typeId);
  const primary = types.slice(0, 3);
  const others = types.slice(3);

  const submit = useMutation({
    mutationFn: () => requestLeave({ leaveTypeId: typeId, startDate, endDate, reason: reason.trim() || null }),
    onSuccess: (data) => {
      notify.success(data?.status === 'APPROVED' ? '휴가가 바로 승인되었습니다' : '결재를 요청했습니다', data);
      try {
        localStorage.removeItem(DRAFT_KEY);
      } catch {
        // 무시
      }
      setStartDate('');
      setEndDate('');
      setReason('');
      queryClient.invalidateQueries({ queryKey: myLeaveRequestsKey });
      queryClient.invalidateQueries({ queryKey: ['leave-balances'] });
      queryClient.invalidateQueries({ queryKey: dashboardKey });
      onDone?.();
    },
    onError: (err) => notify.error(err),
  });

  // 임시 저장 — 이 브라우저에만 남는 작성 중 내용
  const saveDraft = () => {
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify({ typeId, startDate, endDate, reason }));
      notify.success('작성 중인 내용을 이 브라우저에 임시 저장했습니다');
    } catch {
      notify.error({ message: '임시 저장을 할 수 없는 환경입니다' });
    }
  };

  const canSubmit = ready && p && errors.length === 0 && !submit.isPending && !preview.isFetching;
  const hours = p ? p.days * dailyHours : null;

  return (
    <div className="lv-form is-modal">
      <div className="lv-fields">
        <div className="lv-field">
          <span className="lv-label">휴가 종류</span>
          <div className="lv-types">
            {primary.map((t) => (
              <button key={t.id} type="button" className={`lv-type${t.id === typeId ? ' is-active' : ''}`} onClick={() => setTypeId(t.id)}>
                {t.name}
              </button>
            ))}
          </div>
          {others.length > 0 ? (
            <div className="lv-other">
              <span className="lv-muted">기타 휴가</span>
              <Select
                size="small"
                placeholder={others.map((t) => t.name).join(' · ')}
                value={others.some((t) => t.id === typeId) ? typeId : undefined}
                options={others.map((t) => ({ value: t.id, label: t.name }))}
                onChange={setTypeId}
              />
            </div>
          ) : (
            <span className="lv-muted">휴가 종류는 회사 설정에서 정한 것만 보입니다.</span>
          )}
        </div>

        <div className="lv-split">
          <div className="lv-field">
            <label htmlFor="lv-start">시작일</label>
            <div className="lv-select is-date" onClick={openPicker}>
              <input id="lv-start" type="date" value={startDate} onChange={(e) => { setStartDate(e.target.value); if (!endDate || e.target.value > endDate) setEndDate(e.target.value); }} />
              <img src={iconChevron} alt="" />
            </div>
          </div>
          <div className="lv-field">
            <label htmlFor="lv-end">종료일</label>
            <div className="lv-select is-date" onClick={openPicker}>
              <input id="lv-end" type="date" min={startDate || undefined} value={endDate} onChange={(e) => setEndDate(e.target.value)} />
              <img src={iconChevron} alt="" />
            </div>
          </div>
        </div>

        <div className="lv-field">
          <span className="lv-muted" style={{ fontWeight: 600 }}>{`시간 설정 (1일 = ${dailyHours}시간)`}</span>
          <div className="lv-times">
            <span className="lv-time">{schedule?.startTime ?? '--:--'}</span>
            <span className="lv-time">{schedule?.endTime ?? '--:--'}</span>
            <span className="lv-time is-total">{p ? `${fmtDays(p.days)} / ${hours}시간` : '- / -'}</span>
          </div>
          {p?.balance && type?.deductsBalance && (
            <span className="lv-hint">{`${p.balance.leaveTypeName} 잔여 ${p.balance.remaining}일 → 신청 후 ${p.balance.remainingAfter}일`}</span>
          )}
        </div>

        <div className="lv-field">
          <label htmlFor="lv-reason">신청 사유</label>
          <textarea
            id="lv-reason"
            className="lv-textarea"
            rows={2}
            maxLength={500}
            placeholder="사유를 입력해 주세요(선택)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </div>

        <div className="lv-field">
          <span className="lv-label">대직자 지정</span>
          <button type="button" className="lv-select is-disabled" onClick={() => pending('대직자 지정', '대직자 지정')}>
            <span>대직자 지정 준비 중</span>
            <img src={iconChevron} alt="" />
          </button>
        </div>

        <div className="lv-field">
          <span className="lv-label">증빙 서류 첨부</span>
          <button type="button" className="lv-file" onClick={() => pending('휴가 증빙 서류 첨부', '휴가 증빙 서류 첨부')}>
            <img src={iconPaperclip} alt="" />
            <span>파일을 선택해 주세요</span>
          </button>
          <p className="lv-hint">증빙 서류 첨부는 준비 중입니다.</p>
        </div>

        <div className="lv-field">
          <span className="lv-label">결재선 지정</span>
          <div className="lv-line">
            {!p && <span>기간을 고르면 결재선이 표시됩니다</span>}
            {p && p.steps.length === 0 && <span>결재 단계 없이 바로 승인됩니다</span>}
            {p?.steps.map((s, i) => (
              <span key={s.stepOrder}>
                {i > 0 && <span style={{ marginRight: '0.75rem' }}>→</span>}
                {s.skipped ? <s>{`${'①②③④⑤'[i]} ${stepName(s)}`}</s> : `${'①②③④⑤'[i]} ${stepName(s)}`}
              </span>
            ))}
          </div>
        </div>

        {errors.length > 0 && (
          <ul className="lv-errors">
            {errors.map((code) => (
              <li key={code}>{errorMessage({ code })}</li>
            ))}
          </ul>
        )}
      </div>

      <div className="lv-buttons">
        <button type="button" className="lv-btn" onClick={saveDraft}>
          임시 저장
        </button>
        <button type="button" className="lv-btn is-primary" disabled={!canSubmit} onClick={() => submit.mutate()}>
          {submit.isPending ? '요청 중…' : '결재 요청'}
        </button>
      </div>
      <p className="lv-muted" style={{ margin: 0 }}>신청하면 승인 대기 일수만큼 잔여에서 바로 빠지고, 반려 · 철회되면 돌아옵니다.</p>
    </div>
  );
}

/* ───── 상세 · 철회 · 취소 요청 ───── */
function DetailModal({ id, onClose }) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const { modal } = App.useApp();
  const [cancelReason, setCancelReason] = useState('');
  const [asking, setAsking] = useState(false);
  const q = useQuery({ queryKey: leaveRequestKey(id), queryFn: () => getLeaveRequest(id), enabled: !!id });
  const d = q.data;
  const today = dayjs().format('YYYY-MM-DD');

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: myLeaveRequestsKey });
    queryClient.invalidateQueries({ queryKey: ['leave-balances'] });
    queryClient.invalidateQueries({ queryKey: leaveRequestKey(id) });
    queryClient.invalidateQueries({ queryKey: dashboardKey });
  };
  const withdraw = useMutation({
    mutationFn: () => withdrawLeave(id),
    onSuccess: () => {
      notify.success('신청을 취소했습니다');
      refresh();
      onClose();
    },
    onError: (err) => notify.error(err),
  });
  const cancel = useMutation({
    mutationFn: () => cancelLeave(id, cancelReason.trim()),
    onSuccess: (data) => {
      notify.success(data?.status === 'CANCELLED' ? '휴가를 취소했습니다' : '취소를 요청했습니다');
      refresh();
      onClose();
    },
    onError: (err) => notify.error(err),
  });

  const footer = [];
  if (d?.status === 'PENDING') {
    footer.push(
      <button
        key="w"
        type="button"
        className="lv-btn"
        style={{ flex: 'none', padding: '0 1rem' }}
        onClick={() => modal.confirm({ title: '신청을 취소할까요?', content: '승인 대기 중인 신청을 철회합니다.', okText: '신청 취소', cancelText: '닫기', onOk: () => withdraw.mutateAsync() })}
      >
        신청 취소
      </button>,
    );
  }
  if (d?.status === 'APPROVED' && d.startDate > today) {
    footer.push(
      <button key="c" type="button" className="lv-btn is-primary" style={{ flex: 'none', padding: '0 1rem' }} onClick={() => setAsking(true)}>
        취소 요청
      </button>,
    );
  }

  return (
    <Modal open={!!id} onCancel={onClose} title="휴가 신청 상세" footer={footer.length ? <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>{footer}</div> : null}>
      {q.isLoading || !d ? (
        <Spin />
      ) : (
        <div className="lv-fields" style={{ gap: '0.75rem' }}>
          <div className="lv-tr" style={{ gridTemplateColumns: '1fr auto' }}>
            <span>{`${period(d.startDate, d.endDate)} · ${d.leaveTypeName} ${fmtDays(d.days)}`}</span>
            <span className={`lv-status ${d.status}`}>{STATUS_LABEL[d.status]}</span>
          </div>
          <span className="lv-muted">{`사유: ${d.reason || '-'}`}</span>
          {d.cancelReason && <span className="lv-muted">{`취소 사유: ${d.cancelReason}`}</span>}
          <ApprovalSteps steps={d.approvalSteps} />
          {asking && (
            <div className="lv-field">
              <label htmlFor="lv-cancel">취소 사유</label>
              <span className="lv-muted">날짜를 바꾸려면 취소 요청이 승인된 뒤 새로 신청해 주세요.</span>
              <Input.TextArea id="lv-cancel" rows={2} maxLength={500} value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} />
              <div className="lv-buttons">
                <button type="button" className="lv-btn" onClick={() => setAsking(false)}>
                  닫기
                </button>
                <button
                  type="button"
                  className="lv-btn is-primary"
                  disabled={!cancelReason.trim() || cancel.isPending}
                  onClick={() => cancel.mutate()}
                >
                  취소 요청 보내기
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}

/* ───── 신청 내역 ───── */
function HistoryCard({ requests, onOpen, onApply }) {
  const [months, setMonths] = useState(3);
  const [tab, setTab] = useState('ALL');
  const from = months ? dayjs().subtract(months, 'month').format('YYYY-MM-DD') : null;
  const inPeriod = requests.filter((r) => !from || r.endDate >= from);
  const count = (s) => inPeriod.filter((r) => r.status === s).length;
  const rows = inPeriod.filter((r) => tab === 'ALL' || r.status === tab);
  const today = dayjs().format('YYYY-MM-DD');

  const action = (r) => {
    if (r.status === 'PENDING') return '신청 취소';
    if (r.status === 'APPROVED' && r.startDate > today) return '취소 요청';
    if (r.status === 'REJECTED') return '사유 보기';
    return '상세 보기';
  };

  return (
    <section className="lv-card lv-history">
      <div className="lv-history-head">
        <h2 className="lv-title">휴가 신청 내역</h2>
        <div className="lv-history-actions">
          <Select value={months} options={PERIODS} onChange={setMonths} />
          <button type="button" className="lv-btn is-primary lv-apply" onClick={onApply}>
            휴가 신청
          </button>
        </div>
      </div>
      <div className="lv-tabs">
        {[
          ['ALL', `전체 ${inPeriod.length}`],
          ['PENDING', `승인 대기 ${count('PENDING')}`],
          ['APPROVED', `결재 완료 ${count('APPROVED')}`],
          ['REJECTED', `반려 ${count('REJECTED')}`],
        ].map(([key, label]) => (
          <button key={key} type="button" className={`lv-tab${tab === key ? ' is-active' : ''}`} onClick={() => setTab(key)}>
            {label}
          </button>
        ))}
      </div>
      <div className="lv-table">
        <div className="lv-tr is-head">
          <span>휴가 기간</span>
          <span>종류 / 일수</span>
          <span>신청 사유</span>
          <span>결재 상태</span>
          <span>관리</span>
        </div>
        {rows.length === 0 && <p className="lv-empty">해당하는 휴가 신청이 없습니다.</p>}
        {rows.map((r) => (
          <div key={r.id} className="lv-tr is-click" role="button" tabIndex={0} onClick={() => onOpen(r.id)} onKeyDown={(e) => e.key === 'Enter' && onOpen(r.id)}>
            <span>{period(r.startDate, r.endDate)}</span>
            <span>{`${r.leaveTypeName} · ${fmtDays(r.days)}`}</span>
            <span className={`lv-reason${r.status === 'REJECTED' ? ' is-red' : ''}`}>{(r.status === 'REJECTED' && r.rejectReason) || r.reason || '-'}</span>
            <span>
              <span className={`lv-status ${r.status}`}>{STATUS_LABEL[r.status]}</span>
            </span>
            <span className="lv-link">{action(r)}</span>
          </div>
        ))}
      </div>
      <p className="lv-muted" style={{ margin: 0 }}>승인된 휴가는 바꿀 수 없어요. 변경은 취소 요청 후 다시 신청해 주세요.</p>
    </section>
  );
}

/* ───── 사용 이력 및 통계 ───── */
function StatsCard({ requests, dailyHours, leaveYear }) {
  const pending = usePendingFeature();
  const [year, setYear] = useState(leaveYear);
  const [month, setMonth] = useState(dayjs().month() + 1);
  const used = requests
    .filter((r) => (r.status === 'APPROVED' || r.status === 'CANCEL_REQUESTED') && r.leaveYear === year)
    .sort((a, b) => b.startDate.localeCompare(a.startDate));
  const yearDays = used.reduce((a, r) => a + r.days, 0);
  const ym = `${year}-${String(month).padStart(2, '0')}`;
  const monthDays = used.filter((r) => r.startDate.slice(0, 7) === ym).reduce((a, r) => a + r.days, 0);
  const years = [leaveYear, leaveYear - 1, leaveYear - 2];

  return (
    <section className="lv-card lv-stats">
      <h2 className="lv-title is-18">휴가 사용 이력 및 통계</h2>
      <div className="lv-pickers">
        <Select size="small" variant="borderless" value={year} options={years.map((y) => ({ value: y, label: `${y}년` }))} onChange={setYear} />
        <Select size="small" variant="borderless" value={month} options={Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: `${i + 1}월` }))} onChange={setMonth} />
      </div>
      <div className="lv-usage">
        <div>
          <span className="lv-muted">연간 사용</span>
          <strong>{`${fmtDays(yearDays)} / ${yearDays * dailyHours}시간`}</strong>
        </div>
        <div>
          <span className="lv-muted">{`${month}월 사용`}</span>
          <strong className="is-blue">{`${fmtDays(monthDays)} / ${monthDays * dailyHours}시간`}</strong>
        </div>
      </div>
      <div className="lv-mini">
        <div className="lv-mini-tr is-head">
          <span>사용일</span>
          <span>종류</span>
          <span>일수</span>
        </div>
        {used.length === 0 && <p className="lv-empty">사용한 휴가가 없습니다.</p>}
        {used.slice(0, 3).map((r) => (
          <div key={r.id} className="lv-mini-tr">
            <span>{period(r.startDate, r.endDate)}</span>
            <span className="lv-reason">{r.leaveTypeName}</span>
            <span>{fmtDays(r.days)}</span>
          </div>
        ))}
      </div>
      <p className="lv-sub-title">연차 사용 촉진 안내</p>
      <button type="button" className="lv-link" onClick={() => pending('연차 사용 촉진 안내', '연차 사용 촉진')}>
        {'안내 내역 확인  →'}
      </button>
      <button type="button" className="lv-link" style={{ color: '#172235', fontSize: '0.8125rem' }} onClick={() => pending('미사용 수당 정산 내역', '연차 미사용 수당 정산')}>
        {'미사용 수당 정산 내역  →'}
      </button>
      <span className="lv-muted">정산 내역은 준비 중입니다.</span>
    </section>
  );
}

/**
 * 팀원 휴가를 볼 수 있는 만큼만 — 휴가 조회 권한(팀은 조직장)이 있으면 전부, 아니면 승인된 휴가만 종류 없이 "휴가"로.
 * 승인 전 신청 · 병가 · 경조사 같은 종류는 팀장만 본다(10/12 결정, 노션 2.3 N-103 — 서버도 같은 규칙으로 바꿀 예정)
 */
function visibleTeamLeaves(rows, meId, full) {
  const others = (rows ?? []).filter((r) => r.employeeId !== meId);
  if (full) return others;
  // 취소요청 중인 휴가도 취소가 승인되기 전까지는 승인된 휴가(노션 수정사항 103)
  return others
    .filter((r) => r.status === 'APPROVED' || r.status === 'CANCEL_REQUESTED')
    .map((r) => ({ ...r, status: 'APPROVED', leaveTypeName: '휴가' }));
}

/** 다가오는 휴가와 겹치는 팀원 휴가(GET /me/team-leaves) */
function TeamOverlap({ leave, meId, full }) {
  const q = useQuery({
    queryKey: teamLeavesKey(leave?.startDate, leave?.endDate),
    queryFn: () => getTeamLeaves(leave.startDate, leave.endDate),
    enabled: !!leave,
  });
  const names = [...new Set(visibleTeamLeaves(q.data, meId, full).map((r) => r.employeeName))];
  let text = '팀 일정과 겹침 여부를 확인하세요';
  if (leave && q.data) text = names.length ? `팀원 ${names.length}명과 겹침 · ${names.slice(0, 2).join(' · ')}${names.length > 2 ? ' 외' : ''}` : '겹치는 팀원 휴가가 없습니다';
  return (
    <span className={`lv-muted lv-ellipsis${names.length ? ' lv-amber' : ''}`} style={{ fontWeight: 400, marginTop: '0.3125rem' }}>
      {text}
    </span>
  );
}

/* ───── 휴가 캘린더 ───── */
function CalendarCard({ requests, me, full }) {
  const [month, setMonth] = useState(dayjs().format('YYYY-MM'));
  const [view, setView] = useState('mine');
  const m = dayjs(`${month}-01`);
  const from = m.format('YYYY-MM-DD');
  const to = m.endOf('month').format('YYYY-MM-DD');
  const team = useQuery({ queryKey: teamLeavesKey(from, to), queryFn: () => getTeamLeaves(from, to), enabled: view === 'team' });
  const today = dayjs().format('YYYY-MM-DD');

  const events = useMemo(() => {
    const mine = requests
      .filter((r) => LIVE.includes(r.status) && r.endDate >= from && r.startDate <= to)
      .map((r) => ({ ...r, kind: r.status === 'PENDING' ? 'pending' : 'approved', who: me.employee.name }));
    if (view !== 'team') return mine.sort((x, y) => x.startDate.localeCompare(y.startDate));
    const others = visibleTeamLeaves(team.data, me.employee.id, full).map((r) => ({ ...r, kind: 'team', who: r.employeeName }));
    return [...mine, ...others].sort((x, y) => x.startDate.localeCompare(y.startDate));
  }, [requests, team.data, view, from, to, me, full]);

  const kindOf = (date) => {
    const hit = events.filter((e) => e.startDate <= date && e.endDate >= date);
    if (hit.some((e) => e.kind === 'pending')) return 'is-pending';
    if (hit.some((e) => e.kind === 'approved')) return 'is-approved';
    if (hit.length) return 'is-team';
    return '';
  };

  const start = m.subtract(m.day(), 'day');
  const end = m.endOf('month');
  const cells = [];
  for (let d = start; !d.isAfter(end.add(6 - end.day(), 'day'), 'day'); d = d.add(1, 'day')) cells.push(d);
  const label = { pending: '승인 대기', approved: '승인', team: '팀 일정' };

  return (
    <section className="lv-card lv-cal">
      <h2 className="lv-title is-18">휴가 캘린더</h2>
      <div className="lv-cal-month">
        <button type="button" aria-label="이전 달" onClick={() => setMonth(m.subtract(1, 'month').format('YYYY-MM'))}>
          ‹
        </button>
        <span>{m.format('YYYY년 M월')}</span>
        <button type="button" aria-label="다음 달" onClick={() => setMonth(m.add(1, 'month').format('YYYY-MM'))}>
          ›
        </button>
      </div>
      <div className="lv-cal-tabs">
        <button type="button" className={`lv-cal-tab${view === 'mine' ? ' is-active' : ''}`} onClick={() => setView('mine')}>
          개인 일정
        </button>
        <button
          type="button"
          className={`lv-cal-tab${view === 'team' ? ' is-active' : ''}`}
          onClick={() => setView('team')}
        >
          팀/부서 일정
        </button>
      </div>
      <div className="lv-cal-grid">
        {['일', '월', '화', '수', '목', '금', '토'].map((w, i) => (
          <span key={w} className={`is-dow${i === 0 ? ' is-sun' : ''}`}>
            {w}
          </span>
        ))}
        {cells.map((d) => {
          const date = d.format('YYYY-MM-DD');
          const out = d.format('YYYY-MM') !== month;
          const cls = out ? 'is-out' : kindOf(date) || (d.day() === 0 ? 'is-sun' : '');
          return (
            <span key={date} className={`${cls}${date === today ? ' is-today' : ''}`}>
              {d.date()}
            </span>
          );
        })}
      </div>
      <p className="lv-legend">
        <span style={{ color: '#1684af' }}>● 승인 대기</span>
        <span style={{ color: '#15805a' }}>{'   ● 승인'}</span>
        <span style={{ color: '#7361af' }}>{'   ● 팀 휴가'}</span>
      </p>
      <div className="lv-events">
        {events.length === 0 && <span className="is-team">이 달의 휴가 일정이 없습니다.</span>}
        {events.slice(0, 3).map((e) => (
          <span key={`${e.kind}-${e.id ?? `${e.employeeId}-${e.startDate}`}`} className={`is-${e.kind}`}>
            {`${e.startDate.slice(5).replace('-', '/')}${e.startDate !== e.endDate ? `–${e.endDate.slice(8)}` : ''}  ${e.who} · ${e.leaveTypeName} (${e.kind === 'team' && e.status === 'PENDING' ? '팀 · 승인 대기' : label[e.kind]})`}
          </span>
        ))}
      </div>
    </section>
  );
}

/** 휴가 신청 및 조회(F-LEAVE-03·04·05·06) — Figma EMP_휴가 */
export default function MyLeavePage() {
  const { me, can } = useMe();
  const fullTeam = can('LEAVE_READ'); // 휴가 조회 권한(팀 범위는 조직장만 — makeCan)
  const pending = usePendingFeature();
  const [openId, setOpenId] = useState(null);
  // 휴가 신청 창 — 대시보드 [휴가 신청]은 ?apply=1 로 들어와 바로 연다
  const [params, setParams] = useSearchParams();
  const [applyOpen, setApplyOpen] = useState(params.get('apply') === '1');
  const closeApply = () => {
    setApplyOpen(false);
    if (params.get('apply')) setParams({}, { replace: true });
  };
  const types = useQuery({ queryKey: leaveTypesKey, queryFn: getLeaveTypes });
  const balances = useQuery({ queryKey: myLeaveBalancesKey(), queryFn: () => getMyLeaveBalances() });
  const requests = useQuery({ queryKey: myLeaveRequestsKey, queryFn: getMyLeaveRequests });
  const schedules = useQuery({ queryKey: workSchedulesKey, queryFn: getWorkSchedules });

  if (types.isLoading || balances.isLoading || requests.isLoading || schedules.isLoading) {
    return (
      <div className="page-center" style={{ minHeight: '25rem' }}>
        <Spin size="large" />
      </div>
    );
  }
  const error = types.error ?? balances.error ?? requests.error;
  if (error) return <Result status="warning" title="휴가 정보를 불러오지 못했습니다" subTitle={errorMessage(error)} />;

  const schedule = schedules.data?.current;
  const dailyHours = schedule ? schedule.dailyStandardMinutes / 60 : 8;
  const b = balances.data;
  const main = b.balances[0];
  const extra = b.balances.slice(1, 4);
  const nonDeduct = types.data.filter((t) => !t.deductsBalance);
  const rows = requests.data?.content ?? [];
  const today = dayjs().format('YYYY-MM-DD');
  const upcoming = rows.filter((r) => LIVE.includes(r.status) && r.endDate >= today).sort((x, y) => x.startDate.localeCompare(y.startDate))[0];

  return (
    <div className="lv">
      <header className="lv-head">
        <div>
          <h1>휴가 신청 및 조회</h1>
          <p>나의 휴가 잔여 현황을 확인하고, 휴가 신청과 결재 진행 상황을 관리하세요.</p>
        </div>
        <span className="lv-crumb">{'직원 서비스  /  휴가 관리'}</span>
      </header>

      <div className="lv-summary">
        <div className="lv-card lv-sum">
          <span className="lv-muted">{`${main?.leaveTypeName ?? '연차'} 잔여 현황`}</span>
          <div className="lv-sum-cols">
            {[
              ['총 발생', main?.granted, ''],
              ['사용', main?.used, ''],
              ['잔여', main?.remaining, ' is-blue'],
            ].map(([label, days, cls]) => (
              <div key={label}>
                <span className="lv-muted">{label}</span>
                <p className={`lv-sum-big${cls}`}>{days != null ? `${days}일` : '-'}</p>
                <span className="lv-muted">{days != null ? `${days * dailyHours}시간` : ''}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="lv-card lv-sum">
          <span className="lv-muted">특별·경조 휴가 보유</span>
          <div className="lv-sum-cols">
            {extra.length > 0
              ? extra.map((x) => (
                  <div key={x.leaveTypeId}>
                    <span className="lv-muted lv-ellipsis" style={{ display: 'block' }}>{x.leaveTypeName}</span>
                    <p className="lv-sum-big is-19">{`${x.remaining}일`}</p>
                  </div>
                ))
              : nonDeduct.slice(0, 3).map((t) => (
                  <div key={t.id}>
                    <span className="lv-muted lv-ellipsis" style={{ display: 'block' }}>{t.name}</span>
                    <p className="lv-sum-big is-19">사유 시</p>
                  </div>
                ))}
          </div>
          <span className="lv-muted lv-ellipsis">{nonDeduct.length ? `${nonDeduct.map((t) => t.name).join(' · ')} · 사유 발생 시 규정에 따라 사용` : '보유한 특별 휴가가 없습니다'}</span>
        </div>

        <div className="lv-card lv-sum">
          <span className="lv-muted">휴가 소멸 예정일</span>
          <p className="lv-sum-big is-amber" style={{ marginTop: '0.375rem' }}>{dot(b.endDate)}</p>
          <span className="lv-muted lv-ellipsis">{main ? `잔여 ${main.leaveTypeName} ${main.remaining}일 · 사용 계획을 세워 주세요` : '부여된 휴가가 없습니다'}</span>
          <button type="button" className="lv-link" style={{ marginTop: '0.25rem' }} onClick={() => pending('연차 사용 촉진 안내', '연차 사용 촉진')}>
            {'연차 사용 촉진 안내 확인  →'}
          </button>
        </div>

        <div className="lv-card lv-sum">
          <span className="lv-muted">다가오는 휴가</span>
          <p className="lv-sum-big is-18 lv-ellipsis" style={{ marginTop: '0.375rem' }}>
            {upcoming ? `${dayjs(upcoming.startDate).format('M월 D일')} · ${upcoming.leaveTypeName} ${fmtDays(upcoming.days)}` : '예정된 휴가 없음'}
          </p>
          <span className={`lv-muted lv-ellipsis${upcoming?.status === 'PENDING' ? ' lv-amber' : ''}`}>
            {upcoming?.status === 'PENDING' && upcoming.currentStep
              ? `결재 중 · ${upcoming.currentStep.approverName}(${upcoming.currentStep.stepOrder}/${upcoming.currentStep.totalSteps}단계)`
              : upcoming
                ? STATUS_LABEL[upcoming.status]
                : '[휴가 신청]으로 새로 신청할 수 있습니다'}
          </span>
          <TeamOverlap leave={upcoming} meId={me.employee.id} full={fullTeam} />
        </div>
      </div>

      {/* 신청 폼은 창으로 — 신청 내역은 위 요약 카드 줄과 같은 폭 */}
      <HistoryCard requests={rows} onOpen={setOpenId} onApply={() => setApplyOpen(true)} />
      <div className="lv-bottom">
        <StatsCard requests={rows} dailyHours={dailyHours} leaveYear={b.leaveYear} />
        <CalendarCard requests={rows} me={me} full={fullTeam} />
      </div>

      <Modal open={applyOpen} title="휴가 신청" footer={null} onCancel={closeApply} width={520}>
        <RequestForm types={types.data} schedule={schedule} dailyHours={dailyHours} onDone={closeApply} />
      </Modal>

      {openId && <DetailModal id={openId} onClose={() => setOpenId(null)} />}
    </div>
  );
}
