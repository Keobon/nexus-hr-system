// 전사 인사발령 관리(F-ASSIGN-02 · 03) — Figma HR_assignments
// Figma 의 "부서" 필터는 발령 목록 API(GET /assignments)에 조건이 없어 뺐다(직원 · 유형으로 거른다).
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Pagination, Select, Spin } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { assignmentsKey, currentAssignmentsKey, getAssignments, getCurrentAssignments } from '../../../api/assignments';
import { useMe } from '../../../auth/useMe';
import { errorMessage } from '../../../constants/errors';
import { optionsOf } from '../../../constants/labels';
import { PageHead } from '../parts';
import { changeText, correctionText, typeBadge } from './shared';

const PAGE_SIZE = 20;

export default function AssignmentListPage() {
  const navigate = useNavigate();
  const { can } = useMe();
  const [sp, setSp] = useSearchParams();
  const employeeId = sp.get('employeeId') ? Number(sp.get('employeeId')) : undefined;
  const type = sp.get('type') ?? undefined;
  const page = Number(sp.get('page') ?? 0);
  const [selectedId, setSelectedId] = useState(null);

  const people = useQuery({ queryKey: currentAssignmentsKey({ size: 100 }), queryFn: () => getCurrentAssignments({ size: 100 }) });
  const params = { employeeId, type, page, size: PAGE_SIZE };
  const list = useQuery({ queryKey: assignmentsKey(params), queryFn: () => getAssignments(params), placeholderData: keepPreviousData });
  const rows = useMemo(() => list.data?.content ?? [], [list.data]);
  const currentOrg = useMemo(() => new Map((people.data?.content ?? []).map((p) => [p.employeeId, p.orgUnitName])), [people.data]);

  useEffect(() => {
    if (rows.length && !rows.some((a) => a.id === selectedId)) setSelectedId(rows[0].id);
  }, [rows, selectedId]);
  const sel = rows.find((a) => a.id === selectedId);

  const setFilter = (patch) => {
    const next = { employeeId, type, page: 0, ...patch };
    setSp(Object.fromEntries(Object.entries(next).filter(([k, v]) => v !== undefined && v !== null && v !== '' && !(k === 'page' && v === 0))));
  };

  const manage = can('ASSIGNMENT_MANAGE');
  const canCorrect = sel && !sel.correctedById;

  return (
    <div className="hr">
      <PageHead title="전사 인사발령 관리" desc="전 직원의 인사발령 이력을 조회하고 발령을 등록·정정합니다.">
        {manage && (
          <>
            <button type="button" className="hr-btn is-secondary is-14" disabled={!canCorrect}
              title={sel?.correctedById ? '이미 정정된 발령입니다. 가장 최근 정정본을 정정하세요' : undefined}
              onClick={() => navigate(`/assignments/${sel.id}/correct`, { state: { assignment: sel } })}>
              정정 발령
            </button>
            <button type="button" className="hr-btn is-14" onClick={() => navigate('/assignments/new')}>발령 등록</button>
          </>
        )}
      </PageHead>

      <div className="hr-filter">
        <div className="hr-field">
          <label>직원</label>
          <Select className="hr-select" popupMatchSelectWidth={false} allowClear showSearch optionFilterProp="label" placeholder="전체 직원"
            value={employeeId} onChange={(v) => setFilter({ employeeId: v })}
            options={(people.data?.content ?? []).map((p) => ({ value: p.employeeId, label: p.name }))} />
        </div>
        <div className="hr-field">
          <label>발령 유형</label>
          <Select className="hr-select" popupMatchSelectWidth={false} allowClear placeholder="전체 유형" value={type}
            onChange={(v) => setFilter({ type: v })} options={optionsOf('assignmentType')} />
        </div>
      </div>

      <div className="hr-split">
        <div className="hr-table">
          <div className="hr-tr is-head" style={{ gap: '1rem' }}>
            <span style={{ width: '5.625rem' }}>대상자</span>
            <span style={{ width: '5.625rem' }}>유형</span>
            <span style={{ width: '5.625rem' }}>발효일</span>
            <span style={{ width: '15.625rem' }}>변경 내용</span>
          </div>
          {list.isLoading ? (
            <div className="hr-tr is-empty"><Spin /></div>
          ) : list.error ? (
            <div className="hr-tr is-empty">{errorMessage(list.error)}</div>
          ) : rows.length === 0 ? (
            <div className="hr-tr is-empty">발령 이력이 없습니다</div>
          ) : (
            rows.map((a) => {
              const b = typeBadge(a);
              return (
                <div key={a.id} className={`hr-tr is-click${a.id === selectedId ? ' is-selected' : ''}${a.correctedById ? ' is-struck' : ''}`}
                  style={{ gap: '1rem', fontSize: '0.875rem' }} onClick={() => setSelectedId(a.id)}>
                  <span className="hr-strong" style={{ width: '5.625rem' }}>{a.employeeName}</span>
                  <span style={{ width: '5.625rem' }}><span className={`hr-badge ${b.tone}`}>{b.label}</span></span>
                  <span className="hr-struck" style={{ width: '5.625rem' }}>{a.effectiveDate}</span>
                  <span className="hr-struck" style={{ width: '15.625rem' }}>{changeText(a)}</span>
                </div>
              );
            })
          )}
        </div>

        <div className="hr-card hr-info">
          <p className="hr-card-title">발령 상세</p>
          {sel ? (
            <>
              <Row k="대상자" v={currentOrg.get(sel.employeeId) ? `${sel.employeeName} (${currentOrg.get(sel.employeeId)})` : sel.employeeName} />
              <Row k="발령 유형" v={typeBadge(sel).label} />
              <Row k="변경 전" v={changeText(sel).split(' → ')[0]} />
              <Row k="변경 후" v={changeText(sel).split(' → ')[1]} />
              <Row k="발효일" v={sel.effectiveDate} />
              <Row k="사유" v={sel.reason || '-'} />
              <Row k="등록자" v={sel.createdByName} />
              <Row k="정정 여부" v={correctionText(sel)} />
            </>
          ) : (
            <p className="hr-note">왼쪽 목록에서 발령을 고르세요.</p>
          )}
        </div>
      </div>

      {(list.data?.totalPages ?? 0) > 1 && (
        <div className="hr-pager" style={{ justifyContent: 'flex-start', width: '41.25rem' }}>
          <Pagination current={page + 1} pageSize={PAGE_SIZE} total={list.data.totalElements} showSizeChanger={false}
            onChange={(p) => setFilter({ page: p - 1 })} />
        </div>
      )}
    </div>
  );
}

function Row({ k, v }) {
  return (
    <div className="hr-kv2">
      <span className="k">{k}</span>
      <span className="v">{v}</span>
    </div>
  );
}
