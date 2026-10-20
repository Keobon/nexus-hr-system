// 회사 설정 › 감사 로그(F-COMP-06) — Figma 없음. 조회형(C): 필터 바 · 페이징 목록 · 행을 누르면 변경 전·후 비교(드로어)
// 전·후 비교는 목록 행의 before · after 로 바로 그린다(상세 API 없음). 수정 · 삭제 없음
import { useState } from 'react';
import { DatePicker, Drawer, Pagination, Select, Spin } from 'antd';
import dayjs from 'dayjs';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { auditLogsKey, getAuditLogs } from '../../../../api/auditLogs';
import { errorMessage } from '../../../../constants/errors';
import { LABELS, labelOf, optionsOf } from '../../../../constants/labels';
import { formatDateTime } from '../../../../utils/format';
import { PageHead } from '../../../hr/parts';
import EmployeeSelect from '../EmployeeSelect';
import { ymd } from '../parts';
import '../admin.css';

const PAGE_SIZE = 20;
const ACTION_TONE = { CREATE: 'is-ok', UPDATE: 'is-info', DELETE: 'is-alert', EXECUTE: 'is-warn', VIEW: 'is-gray' };
const targetLabel = (t) => LABELS.auditTargetType[t]?.label ?? t;

export default function AuditLogPage() {
  const [range, setRange] = useState([dayjs().subtract(6, 'day'), dayjs()]);
  const [actorId, setActorId] = useState();
  const [targetType, setTargetType] = useState();
  const [action, setAction] = useState();
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(null);

  const params = { from: ymd(range?.[0]), to: ymd(range?.[1]), actorId, targetType, action, page, size: PAGE_SIZE };
  const list = useQuery({ queryKey: auditLogsKey(params), queryFn: () => getAuditLogs(params), placeholderData: keepPreviousData });
  const rows = list.data?.content ?? [];
  const reset = (fn) => (v) => { setPage(0); fn(v); };

  return (
    <div className="hr">
      <PageHead title="감사 로그" desc="누가 언제 무엇을 바꿨는지 기록입니다. 기록은 고치거나 지울 수 없습니다." />

      <div className="hr-filter">
        <div className="hr-field">
          <label>기간</label>
          <DatePicker.RangePicker value={range} onChange={reset(setRange)} allowClear={false} style={{ height: '2.25rem' }} />
        </div>
        <div className="hr-field">
          <label>실행자</label>
          <EmployeeSelect className="hr-select" style={{ width: '13rem' }} allowClear value={actorId} onChange={reset(setActorId)} placeholder="전체" />
        </div>
        <div className="hr-field">
          <label>대상 종류</label>
          <Select className="hr-select" style={{ width: '11rem' }} allowClear showSearch optionFilterProp="label" placeholder="전체" value={targetType}
            onChange={reset(setTargetType)} options={optionsOf('auditTargetType')} />
        </div>
        <div className="hr-field">
          <label>행위</label>
          <Select className="hr-select" style={{ width: '8rem' }} allowClear placeholder="전체" value={action} onChange={reset(setAction)}
            options={optionsOf('auditAction')} />
        </div>
        <span className="ad-total">전체 <strong>{list.data?.totalElements ?? 0}</strong>건</span>
      </div>

      <div className="hr-table">
        <div className="hr-tr is-head">
          <span style={{ width: '10rem' }}>시각</span>
          <span style={{ width: '8rem' }}>실행자</span>
          <span style={{ width: '10rem' }}>대상 종류</span>
          <span style={{ width: '6rem' }}>대상 ID</span>
          <span style={{ width: '6rem' }}>행위</span>
          <span style={{ flex: '1 0 0' }}>바뀐 항목</span>
        </div>
        {list.isLoading ? <div className="hr-tr is-empty"><Spin /></div>
          : list.error ? <div className="hr-tr is-empty">{errorMessage(list.error)}</div>
            : rows.length === 0 ? <div className="hr-tr is-empty">이 기간의 기록이 없습니다</div>
              : rows.map((r) => (
                <div key={r.id} className={`hr-tr is-click${open?.id === r.id ? ' is-selected' : ''}`} onClick={() => setOpen(r)}>
                  <span style={{ width: '10rem' }}>{formatDateTime(r.createdAt)}</span>
                  <span className="hr-strong" style={{ width: '8rem' }}>{r.actorName ?? '시스템'}</span>
                  <span style={{ width: '10rem' }}>{targetLabel(r.targetType)}</span>
                  <span style={{ width: '6rem', color: '#64748b' }}>{r.targetId ?? '-'}</span>
                  <span style={{ width: '6rem' }}><span className={`hr-badge ${ACTION_TONE[r.action] ?? 'is-gray'}`}>{labelOf('auditAction', r.action)}</span></span>
                  <span style={{ flex: '1 0 0', color: '#64748b' }}>{changedKeys(r).join(', ') || '-'}</span>
                </div>
              ))}
      </div>
      {(list.data?.totalPages ?? 0) > 1 && (
        <div className="hr-pager">
          <Pagination current={page + 1} pageSize={PAGE_SIZE} total={list.data.totalElements} showSizeChanger={false} onChange={(p) => setPage(p - 1)} />
        </div>
      )}

      <Drawer open={!!open} onClose={() => setOpen(null)} size={560} title={open ? `${targetLabel(open.targetType)} ${labelOf('auditAction', open.action)}` : ''}>
        {open && <Diff log={open} />}
      </Drawer>
    </div>
  );
}

const isObj = (v) => v && typeof v === 'object' && !Array.isArray(v);
const show = (v) => (v === undefined ? '' : v === null ? 'null' : typeof v === 'object' ? JSON.stringify(v) : String(v));

/** 전·후에서 값이 다른 키 */
function changedKeys(r) {
  const b = isObj(r.before) ? r.before : {};
  const a = isObj(r.after) ? r.after : {};
  return [...new Set([...Object.keys(b), ...Object.keys(a)])].filter((k) => show(b[k]) !== show(a[k]));
}

function Diff({ log }) {
  const b = isObj(log.before) ? log.before : null;
  const a = isObj(log.after) ? log.after : null;
  const keys = [...new Set([...Object.keys(b ?? {}), ...Object.keys(a ?? {})])];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div className="hr-readonly">
        <div className="values" style={{ fontSize: '0.8125rem' }}>
          <span>{`시각 ${formatDateTime(log.createdAt)}`}</span>
          <span>{`실행자 ${log.actorName ?? '시스템'}`}</span>
          <span>{`대상 ${log.targetType}${log.targetId ? ` #${log.targetId}` : ''}`}</span>
        </div>
      </div>
      {keys.length === 0 ? (
        <p className="hr-note">
          {log.before === null && log.after === null ? '기록된 값이 없습니다.' : (
            <>변경 전 <code>{show(log.before)}</code> → 변경 후 <code>{show(log.after)}</code></>
          )}
        </p>
      ) : (
        <div className="ad-diff">
          <div className="h">항목</div><div className="h">변경 전</div><div className="h">변경 후</div>
          {keys.map((k) => {
            const changed = show(b?.[k]) !== show(a?.[k]);
            return (
              <div key={k} style={{ display: 'contents' }}>
                <div className={`k${changed ? ' is-changed' : ''}`}>{k}</div>
                <div className={`b${changed ? ' is-changed' : ''}`}>{b ? show(b[k]) || '-' : '-'}</div>
                <div className={`a${changed ? ' is-changed' : ''}`}>{a ? show(a[k]) || '-' : '-'}</div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
