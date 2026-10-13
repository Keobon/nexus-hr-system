// 직원 목록(F-EMP-02) — Figma HR_employee_list
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Pagination, Select, Spin } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { employeesKey, getEmployees } from '../../../api/employees';
import { jobGradesApi } from '../../../api/orgSettings';
import { useMe } from '../../../auth/useMe';
import { errorMessage } from '../../../constants/errors';
import { optionsOf } from '../../../constants/labels';
import { EmpStatusBadge, PageHead, useOrgOptions } from '../parts';

const PAGE_SIZE = 20;
// "전체 상태"는 퇴직자까지 — status 를 비우면 서버는 재직 · 휴직만 준다
const ALL_STATUS = ['ACTIVE', 'ON_LEAVE', 'RESIGNED'];

export default function EmployeeListPage() {
  const navigate = useNavigate();
  const { can } = useMe();
  const [sp, setSp] = useSearchParams();
  const orgUnitId = sp.get('orgUnitId') ? Number(sp.get('orgUnitId')) : undefined;
  const jobGradeId = sp.get('jobGradeId') ? Number(sp.get('jobGradeId')) : undefined;
  const status = sp.get('status') ?? undefined;
  const page = Number(sp.get('page') ?? 0);

  const org = useOrgOptions();
  const grades = useQuery({ queryKey: jobGradesApi.key(true), queryFn: () => jobGradesApi.list(true) });
  const params = { orgUnitId, jobGradeId, status: status ? [status] : ALL_STATUS, page, size: PAGE_SIZE };
  const list = useQuery({ queryKey: employeesKey(params), queryFn: () => getEmployees(params), placeholderData: keepPreviousData });

  const setFilter = (patch) => {
    const next = { orgUnitId, jobGradeId, status, page: 0, ...patch };
    // 빈 값과 첫 페이지(0)는 주소에서 뺀다
    setSp(Object.fromEntries(Object.entries(next).filter(([k, v]) => v !== undefined && v !== null && v !== '' && !(k === 'page' && v === 0))));
  };

  const rows = list.data?.content ?? [];

  return (
    <div className="hr">
      <PageHead title="전직원 목록" desc="부서·직급·재직상태로 직원을 조회하고 상세 정보를 관리합니다.">
        {can('EMPLOYEE_MANAGE') && <button type="button" className="hr-btn" onClick={() => navigate('/employees/new')}>직원 등록</button>}
      </PageHead>

      <div className="hr-filter">
        <div className="hr-field">
          <label>부서</label>
          <Select className="hr-select" popupMatchSelectWidth={false} allowClear placeholder="전체 부서" value={orgUnitId}
            onChange={(v) => setFilter({ orgUnitId: v })} options={org.options} />
        </div>
        <div className="hr-field">
          <label>직급</label>
          <Select className="hr-select" popupMatchSelectWidth={false} allowClear placeholder="전체 직급" value={jobGradeId}
            onChange={(v) => setFilter({ jobGradeId: v })} options={(grades.data ?? []).map((g) => ({ value: g.id, label: g.name }))} />
        </div>
        <div className="hr-field">
          <label>재직상태</label>
          <Select className="hr-select" popupMatchSelectWidth={false} allowClear placeholder="전체 상태" value={status}
            onChange={(v) => setFilter({ status: v })} options={optionsOf('employmentStatus')} />
        </div>
      </div>

      <div className="hr-table">
        <div className="hr-toolbar">총 {list.data?.totalElements ?? 0}명</div>
        <div className="hr-tr is-head">
          <span style={{ width: '15rem' }}>이름</span>
          <span style={{ width: '18.75rem' }}>부서</span>
          <span style={{ width: '11.25rem' }}>직급</span>
          <span style={{ width: '11.25rem' }}>재직상태</span>
          <span>상세</span>
        </div>
        {list.isLoading ? (
          <div className="hr-tr is-empty"><Spin /></div>
        ) : list.error ? (
          <div className="hr-tr is-empty">{errorMessage(list.error)}</div>
        ) : rows.length === 0 ? (
          <div className="hr-tr is-empty">조건에 맞는 직원이 없습니다</div>
        ) : (
          rows.map((e) => (
            <div key={e.id} className="hr-tr is-click" onClick={() => navigate(`/employees/${e.id}`)}>
              <span className="hr-strong" style={{ width: '15rem' }}>{e.name}</span>
              <span style={{ width: '18.75rem' }}>{e.orgUnitName}</span>
              <span style={{ width: '11.25rem' }}>{e.jobGradeName ?? '-'}</span>
              <span style={{ width: '11.25rem' }}><EmpStatusBadge status={e.status} /></span>
              <button type="button" className="hr-link" onClick={(ev) => { ev.stopPropagation(); navigate(`/employees/${e.id}`); }}>상세 보기 →</button>
            </div>
          ))
        )}
      </div>

      {(list.data?.totalPages ?? 0) > 1 && (
        <div className="hr-pager">
          <Pagination current={page + 1} pageSize={PAGE_SIZE} total={list.data.totalElements} showSizeChanger={false}
            onChange={(p) => setFilter({ page: p - 1 })} />
        </div>
      )}
    </div>
  );
}
