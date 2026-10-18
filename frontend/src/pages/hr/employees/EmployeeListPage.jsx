// 전직원 목록(F-EMP-02) — Figma HR_employee_list(445:8343). 직원 목록 · 연락처 두 탭, 고르면 메일 아이콘 버튼으로 메일 보내기.
// 연락처 탭은 "속성 고르기" — 고른 속성(전화번호 등)만 이름 · 직급 옆에 보여 준다(10/15 교수님 요청)
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { App, Checkbox, Input, Pagination, Result, Select, Skeleton, Tooltip } from 'antd';
import { MailOutlined } from '@ant-design/icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { employeeContactsKey, employeesKey, fetchAllPages, getEmployeeContacts, getEmployees } from '../../../api/employees';
import { employmentTypesApi, jobGradesApi, jobTitlesApi } from '../../../api/orgSettings';
import { useMe } from '../../../auth/useMe';
import { errorMessage } from '../../../constants/errors';
import { labelOf, optionsOf } from '../../../constants/labels';
import { EmpStatusBadge, PageHead, useOrgOptions } from '../parts';

const PAGE_SIZE = 20;
// "전체 상태"는 퇴직자까지 — status 를 비우면 서버는 재직 · 휴직만 준다
const ALL_STATUS = ['ACTIVE', 'ON_LEAVE', 'RESIGNED'];
const TABS = [
  ['list', '직원 목록'],
  ['contacts', '연락처'],
];
const FILTER_KEYS = ['orgUnitId', 'jobGradeId', 'jobTitleId', 'employmentTypeId', 'status', 'keyword'];
const MAILTO_LIMIT = 1800; // 이보다 길면 메일 앱이 못 여는 경우가 있어 주소를 복사해 준다

const dot = (d) => (d ? d.replaceAll('-', '.') : '-');
const gradeTitle = (r) => [r.jobGradeName, r.jobTitleName].filter(Boolean).join(' · ') || '-';
const nameCell = (r) => (
  <span className="hr-name">
    <span className="hr-avatar">{r.name?.slice(0, 1)}</span>
    <strong>{r.name}</strong>
  </span>
);

/** 직원 목록 칸 — w 는 Figma px ÷ 16(rem). 고용형태 · 오늘 상태도 기본으로 보인다(10/18 설계), 연봉은 상세에서 */
const LIST_COLUMNS = [
  { key: 'employeeNo', title: '사원번호', w: 6.5, text: (r) => r.employeeNo },
  { key: 'name', title: '이름', w: 8, cell: nameCell },
  { key: 'org', title: '소속 조직', w: 8.5, text: (r) => r.orgUnitName },
  { key: 'grade', title: '직급 · 직책', w: 7.5, text: gradeTitle },
  { key: 'employmentType', title: '고용형태', w: 5, text: (r) => r.employmentTypeName },
  { key: 'phone', title: '전화번호', w: 7.5, text: (r) => r.phone },
  { key: 'email', title: '이메일', w: 11, text: (r) => r.email },
  { key: 'hireDate', title: '입사일', w: 6, text: (r) => dot(r.hireDate) },
  { key: 'todayStatus', title: '오늘 상태', w: 5, text: (r) => r.todayStatus && labelOf('todayStatus', r.todayStatus) },
  { key: 'status', title: '재직상태', w: 5, cell: (r) => <EmpStatusBadge status={r.status} /> },
];

/** 연락처 탭 — 이름 · 직급은 늘 보이고, 나머지는 "속성"으로 고른 것만. 고른 속성은 이 브라우저에 기억 */
const CONTACT_BASE = [
  { key: 'name', title: '이름', w: 8, cell: nameCell },
  { key: 'grade', title: '직급', w: 4.5, text: (r) => r.jobGradeName },
];
const CONTACT_PROPS = [
  { key: 'phone', title: '전화번호', w: 7.8125, text: (r) => r.phone },
  { key: 'email', title: '이메일', w: 12.5, text: (r) => r.email },
  { key: 'address', title: '주소', w: 16, text: (r) => r.address },
  { key: 'org', title: '소속', w: 8.5, text: (r) => r.orgUnitName },
  { key: 'title', title: '직책', w: 4.5, text: (r) => r.jobTitleName },
  { key: 'employeeNo', title: '사원번호', w: 6.5, text: (r) => r.employeeNo },
];
const PROPS_STORE = 'hr.employees.contactProps';
const DEFAULT_PROPS = ['phone', 'email'];
function loadProps() {
  try {
    const saved = JSON.parse(localStorage.getItem(PROPS_STORE));
    return Array.isArray(saved) ? saved : DEFAULT_PROPS;
  } catch {
    return DEFAULT_PROPS;
  }
}

/** 연락처 행에서 직원 목록에 붙일 칸만 */
const contactPart = (c) => (c ? { phone: c.phone, email: c.email } : {});

export default function EmployeeListPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const { can } = useMe();
  const [sp, setSp] = useSearchParams();
  const tab = sp.get('tab') === 'contacts' ? 'contacts' : 'list';
  const isList = tab === 'list';
  const num = (k) => (sp.get(k) ? Number(sp.get(k)) : undefined);
  const filters = {
    orgUnitId: num('orgUnitId'),
    jobGradeId: num('jobGradeId'),
    jobTitleId: num('jobTitleId'),
    employmentTypeId: num('employmentTypeId'),
    status: sp.get('status') ?? undefined,
    keyword: sp.get('keyword') ?? undefined,
  };
  const page = Number(sp.get('page') ?? 0);

  // 주소(URL)에 탭 · 필터 · 검색어 · 페이지 — 빈 값 · 첫 페이지 · 기본 탭은 뺀다
  const write = (next) =>
    setSp(
      Object.fromEntries(
        Object.entries(next).filter(([k, v]) => v !== undefined && v !== null && v !== '' && !(k === 'page' && v === 0) && !(k === 'tab' && v === 'list')),
      ),
    );
  const setFilter = (patch) => write({ tab, ...filters, page: 0, ...patch });

  // 검색어 — 입력이 멈추고 0.3초 뒤 주소로
  const [kw, setKw] = useState(filters.keyword ?? '');
  useEffect(() => setKw(filters.keyword ?? ''), [filters.keyword]);
  useEffect(() => {
    const t = setTimeout(() => {
      if ((kw.trim() || undefined) !== filters.keyword) setFilter({ keyword: kw.trim() || undefined });
    }, 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kw]);

  const org = useOrgOptions();
  const grades = useQuery({ queryKey: jobGradesApi.key(true), queryFn: () => jobGradesApi.list(true) });
  const titles = useQuery({ queryKey: jobTitlesApi.key(true), queryFn: () => jobTitlesApi.list(true) });
  const types = useQuery({ queryKey: employmentTypesApi.key(true), queryFn: () => employmentTypesApi.list(true) });

  // 요청 — 직원 목록은 필터 전부, 연락처는 조직 · 검색어만(퇴직자는 서버가 이미 뺀다)
  const baseParams = isList
    ? {
        orgUnitId: filters.orgUnitId,
        jobGradeId: filters.jobGradeId,
        jobTitleId: filters.jobTitleId,
        employmentTypeId: filters.employmentTypeId,
        status: filters.status ? [filters.status] : ALL_STATUS,
        keyword: filters.keyword,
      }
    : { orgUnitId: filters.orgUnitId, keyword: filters.keyword };
  const fetchPage = isList ? getEmployees : getEmployeeContacts;
  const params = { ...baseParams, page, size: PAGE_SIZE };
  const q = useQuery({
    queryKey: isList ? employeesKey(params) : employeeContactsKey(params),
    queryFn: () => fetchPage(params),
    placeholderData: keepPreviousData,
  });

  // 목록 API 에는 전화 · 이메일이 없다 — 연락처는 연락처 API(재직 · 휴직, 인사 · 조직장 전사)로 보기로 했다(백엔드 10/18).
  // 그래서 한 번 받아 id 로 채운다(퇴직자는 "-"). 주소는 목록에 두지 않고 상세에서만 본다
  const contactAll = useQuery({
    queryKey: employeeContactsKey({ all: true }),
    queryFn: () => fetchAllPages(getEmployeeContacts, {}),
    enabled: isList,
    staleTime: 60_000,
  });
  const contactById = useMemo(() => Object.fromEntries((contactAll.data ?? []).map((c) => [c.id, c])), [contactAll.data]);
  const withContact = (r) => (isList ? { ...r, ...contactPart(contactById[r.id]) } : r);
  const rows = (q.data?.content ?? []).map(withContact);
  const total = q.data?.totalElements ?? 0;

  // 연락처 속성 고르기 — 고른 순서가 아니라 CONTACT_PROPS 순서로 칸을 둔다
  const [props, setProps] = useState(loadProps);
  const setPropsSaved = (next) => {
    setProps(next);
    try {
      localStorage.setItem(PROPS_STORE, JSON.stringify(next));
    } catch {
      // 저장이 막힌 브라우저 — 이번 화면에서만 적용
    }
  };
  const columns = isList ? LIST_COLUMNS : [...CONTACT_BASE, ...CONTACT_PROPS.filter((c) => props.includes(c.key))];

  // 선택 — 머리 체크박스는 이 페이지만. 탭 · 필터 · 검색어가 바뀌면 비운다
  const scopeKey = JSON.stringify([tab, baseParams]);
  const [picked, setPicked] = useState({}); // id → 행
  const [allPicked, setAllPicked] = useState(false); // 검색 결과 전체
  useEffect(() => {
    setPicked({});
    setAllPicked(false);
  }, [scopeKey]);
  const onPage = rows.filter((r) => picked[r.id]).length;
  const pickedTotal = Object.keys(picked).length;
  const pageAll = rows.length > 0 && onPage === rows.length;
  const togglePage = (on) =>
    setPicked((prev) => {
      const next = { ...prev };
      rows.forEach((r) => (on ? (next[r.id] = r) : delete next[r.id]));
      return next;
    });
  const toggleRow = (r, on) =>
    setPicked((prev) => {
      const next = { ...prev };
      if (on) next[r.id] = r;
      else delete next[r.id];
      return next;
    });
  const clearPick = () => {
    setPicked({});
    setAllPicked(false);
  };
  const hasPick = allPicked || pickedTotal > 0;

  // 보낼 대상 — 전체 선택이면 같은 조건으로 size=100 씩 모든 페이지를 받아 모은다
  const [busy, setBusy] = useState(false);
  const run = async (fn) => {
    setBusy(true);
    try {
      const list = allPicked ? (await fetchAllPages(fetchPage, baseParams)).map(withContact) : Object.values(picked);
      await fn(list);
    } catch (err) {
      message.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };
  const sendMail = () =>
    run(async (list) => {
      const mails = list.map((r) => r.email).filter(Boolean);
      const missing = list.length - mails.length;
      if (missing > 0) message.info(`${missing}명은 이메일이 없어 제외됐습니다`);
      if (!mails.length) return;
      const href = `mailto:?bcc=${mails.join(',')}`; // 받는 사람끼리 서로 안 보이게 숨은 참조
      if (href.length > MAILTO_LIMIT) {
        await navigator.clipboard.writeText(mails.join(', '));
        message.success(`이메일 주소 ${mails.length}개를 복사했습니다. 메일 앱의 숨은 참조에 붙여넣어 보내세요`);
        return;
      }
      window.location.href = href;
    });

  if (q.error?.status === 403) return <Result status="403" title="권한 없음" subTitle="이 화면을 볼 권한이 없습니다." />;

  const minWidth = 2.25 + columns.reduce((a, c) => a + c.w, 0) + 2.5;
  const filterOn = FILTER_KEYS.some((k) => filters[k] !== undefined);
  const resetFilters = () => {
    setKw('');
    write({ tab });
  };

  return (
    <div className="hr">
      <div className="hr-titles">
        <PageHead title="전직원 목록" desc="부서·직급·재직상태로 직원을 조회하고 상세 정보를 관리합니다." />
        <nav className="hr-tabs" role="tablist" aria-label="전직원 목록 탭">
          {TABS.map(([k, l]) => (
            <button
              key={k}
              type="button"
              role="tab"
              aria-selected={tab === k}
              className={tab === k ? 'is-on' : undefined}
              onClick={() => write({ tab: k, orgUnitId: filters.orgUnitId, keyword: filters.keyword })}
            >
              {l}
            </button>
          ))}
        </nav>
        {can('EMPLOYEE_MANAGE') && (
          <div className="hr-titles-actions">
            <button type="button" className="hr-btn" onClick={() => navigate('/employees/new')}>
              직원 등록
            </button>
          </div>
        )}
      </div>

      <div className="hr-filter is-card">
        <div className="hr-field is-search">
          <label htmlFor="emp-keyword">검색</label>
          <Input id="emp-keyword" allowClear value={kw} onChange={(e) => setKw(e.target.value)} placeholder="이름 · 사원번호 검색" />
        </div>
        <div className="hr-field">
          <label>부서</label>
          <Select className="hr-select" popupMatchSelectWidth={false} allowClear placeholder="전체 부서" value={filters.orgUnitId} onChange={(v) => setFilter({ orgUnitId: v })} options={org.options} />
        </div>
        {!isList && (
          <div className="hr-field is-props">
            <label>보여 줄 속성</label>
            <Checkbox.Group value={props} onChange={setPropsSaved} options={CONTACT_PROPS.map((c) => ({ value: c.key, label: c.title }))} />
            <span className="hr-props-note">이름 · 직급은 늘 보입니다</span>
          </div>
        )}
        {isList && (
          <>
            <div className="hr-field">
              <label>직급</label>
              <Select className="hr-select" popupMatchSelectWidth={false} allowClear placeholder="전체 직급" value={filters.jobGradeId} onChange={(v) => setFilter({ jobGradeId: v })} options={(grades.data ?? []).map((g) => ({ value: g.id, label: g.name }))} />
            </div>
            <div className="hr-field">
              <label>재직상태</label>
              <Select className="hr-select" popupMatchSelectWidth={false} allowClear placeholder="전체 상태" value={filters.status} onChange={(v) => setFilter({ status: v })} options={optionsOf('employmentStatus')} />
            </div>
            <div className="hr-field">
              <label>직책</label>
              <Select className="hr-select" popupMatchSelectWidth={false} allowClear placeholder="전체 직책" value={filters.jobTitleId} onChange={(v) => setFilter({ jobTitleId: v })} options={(titles.data ?? []).map((g) => ({ value: g.id, label: g.name }))} />
            </div>
            <div className="hr-field">
              <label>고용형태</label>
              <Select className="hr-select" popupMatchSelectWidth={false} allowClear placeholder="전체 형태" value={filters.employmentTypeId} onChange={(v) => setFilter({ employmentTypeId: v })} options={(types.data ?? []).map((g) => ({ value: g.id, label: g.name }))} />
            </div>
          </>
        )}
      </div>

      <div className="hr-table is-card">
        <div className="hr-toolbar">
          <span>{`총 ${total}명`}</span>
          <div className="hr-toolbar-actions">
            {hasPick && (
              <Tooltip title={allPicked ? `검색 결과 ${total}명에게 메일 보내기` : `선택한 ${pickedTotal}명에게 메일 보내기`}>
                <button type="button" className="hr-mail-btn" aria-label="선택한 직원에게 메일 보내기" disabled={busy} onClick={sendMail}>
                  <MailOutlined />
                </button>
              </Tooltip>
            )}
          </div>
        </div>

        {hasPick && (
          <div className="hr-pickbar">
            <span>
              {allPicked ? (
                <strong>{`검색 결과 ${total}명 전체 선택됨`}</strong>
              ) : (
                <>
                  <strong>{`이 페이지 ${onPage}명 선택됨`}</strong>
                  {pickedTotal !== onPage && ` (다른 페이지 포함 ${pickedTotal}명)`}
                  {total > rows.length && (
                    <>
                      {' · '}
                      <button type="button" className="hr-link" onClick={() => setAllPicked(true)}>
                        {`검색 결과 ${total}명 전체 선택`}
                      </button>
                    </>
                  )}
                </>
              )}
              {' · '}
              <button type="button" className="hr-link is-gray" onClick={clearPick}>
                선택 해제
              </button>
            </span>
          </div>
        )}

        <div className="hr-scroll">
          <div style={{ minWidth: `${minWidth}rem` }}>
            <div className="hr-tr is-head">
              <span className="hr-check">
                <Checkbox
                  checked={allPicked || pageAll}
                  indeterminate={!allPicked && !pageAll && onPage > 0}
                  disabled={!rows.length}
                  onChange={(e) => (allPicked ? clearPick() : togglePage(e.target.checked))}
                  aria-label="이 페이지 모두 선택"
                />
              </span>
              {columns.map((c) => (
                <span key={c.key} style={{ flex: `${c.w} 0 ${c.w}rem` }}>
                  {c.title}
                </span>
              ))}
            </div>
            {q.isLoading ? (
              [0, 1, 2, 3, 4].map((i) => (
                <div key={i} className="hr-tr is-row">
                  <Skeleton active title={false} paragraph={{ rows: 1, width: '100%' }} />
                </div>
              ))
            ) : q.error ? (
              <div className="hr-tr is-empty is-col">
                <span>{errorMessage(q.error)}</span>
                <button type="button" className="hr-btn is-secondary" onClick={() => q.refetch()}>
                  다시 시도
                </button>
              </div>
            ) : rows.length === 0 ? (
              <div className="hr-tr is-empty is-col">
                <span>조건에 맞는 직원이 없습니다</span>
                {filterOn && (
                  <button type="button" className="hr-btn is-secondary" onClick={resetFilters}>
                    필터 초기화
                  </button>
                )}
              </div>
            ) : (
              rows.map((r) => (
                <div
                  key={r.id}
                  className={`hr-tr is-row${isList ? ' is-click' : ''}${allPicked || picked[r.id] ? ' is-selected' : ''}`}
                  onClick={isList ? () => navigate(`/employees/${r.id}`) : undefined}
                >
                  {/* 체크박스를 누를 때는 상세로 가지 않는다 */}
                  <span className="hr-check" onClick={(e) => e.stopPropagation()}>
                    <Checkbox checked={allPicked || !!picked[r.id]} disabled={allPicked} onChange={(e) => toggleRow(r, e.target.checked)} aria-label={`${r.name} 선택`} />
                  </span>
                  {columns.map((c) => {
                    const v = c.text ? c.text(r) || '-' : null;
                    return (
                      <span key={c.key} style={{ flex: `${c.w} 0 ${c.w}rem` }} title={v && v !== '-' ? v : undefined}>
                        {c.cell ? c.cell(r) : v}
                      </span>
                    );
                  })}
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {(q.data?.totalPages ?? 0) > 1 && (
        <div className="hr-pager">
          <Pagination current={page + 1} pageSize={PAGE_SIZE} total={total} showSizeChanger={false} onChange={(p) => write({ tab, ...filters, page: p - 1 })} />
        </div>
      )}
    </div>
  );
}
