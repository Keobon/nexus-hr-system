import { useEffect, useMemo, useRef, useState } from 'react';
import { Result, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getOrgMembers, getOrgTree, orgMembersKey, orgTreeKey } from '../../../api/org';
import { useMe } from '../../../auth/useMe';
import { useFileUrl } from '../../../components/common/useFileUrl';
import { usePendingFeature } from '../../../components/common/usePendingFeature';
import { errorMessage } from '../../../constants/errors';
import { LABELS } from '../../../constants/labels';
import { officePhone } from './officePhone';
import iconChevron from '../../../assets/figma/icon-chevron.svg';
import iconChevronDown from '../../../assets/figma/icon-chevron-down.svg';
import iconChevronRight from '../../../assets/figma/icon-chevron-right.svg';
import iconSearch from '../../../assets/figma/icon-search.svg';
import './org.css';

const MENU = [
  ['tree', '조직 구조'],
  ['search', '동료 찾기'],
  ['dept', '부서 정보'],
  ['contact', '연락·소통'],
];
const AVATAR_COLORS = ['#0ea5e9', '#2563eb', '#7c3aed', '#059669', '#d97706', '#db2777'];
const PREVIEW = 3; // 부서원 카드 미리보기 수(Figma "12명 중 3명 미리보기")

const sorted = (node) => [...(node.children ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);

/** 트리를 [{ node, depth, parentId }] 로 펼친다(정렬 순서대로) */
function flatten(node, depth = 0, parentId = null, out = []) {
  out.push({ node, depth, parentId });
  sorted(node).forEach((c) => flatten(c, depth + 1, node.id, out));
  return out;
}

/** 루트부터 그 조직까지의 ID 들(펼쳐 둘 경로) */
function pathTo(flat, id) {
  const byId = Object.fromEntries(flat.map((f) => [f.node.id, f]));
  const path = [];
  for (let cur = byId[id]; cur; cur = byId[cur.parentId]) path.unshift(cur.node.id);
  return path;
}

const gradeTitle = (m) => [m.jobGradeName, m.jobTitleName].filter(Boolean).join(' / ') || '-';
const STATUS_FILTER = [['ALL', '전체 상태'], ...Object.entries(LABELS.orgWorkStatus).map(([k, v]) => [k, v.label]), ['NONE', '근무 위치 없음']];

/** 오늘 근무 위치 배지 — 출근 전 · 퇴근 · 휴직은 서버가 null 로 준다(표시 안 함) */
function WorkBadge({ status }) {
  const item = LABELS.orgWorkStatus[status];
  if (!item) return null;
  return <span className={`org-work is-${status.toLowerCase()}`}>{item.label}</span>;
}

function Avatar({ member, size = 'md' }) {
  const url = useFileUrl(member.profileFileId);
  const color = AVATAR_COLORS[member.id % AVATAR_COLORS.length];
  return (
    <span className={`org-avatar is-${size}`} style={url ? undefined : { background: color }}>
      {url ? <img src={url} alt="" /> : member.name.slice(0, 1)}
    </span>
  );
}

/* ───── 회사 조직 구조 트리 — 위에서 아래로 뻗는 조직도(Figma TreeCard) ───── */
function ChartNode({ node, isRoot, open, toggle, selectedId, onSelect }) {
  const kids = sorted(node);
  const isOpen = open.has(node.id);
  const cls = isRoot ? ' is-root' : selectedId === node.id ? ' is-selected' : '';
  return (
    <li>
      <button type="button" className={`org-box${cls}`} onClick={() => onSelect(node.id)} aria-pressed={selectedId === node.id}>
        {kids.length > 0 && (
          <span
            className="org-box-toggle"
            role="button"
            aria-label={isOpen ? '접기' : '펼치기'}
            onClick={(e) => {
              e.stopPropagation();
              toggle(node.id);
            }}
          >
            <img src={isOpen ? iconChevronDown : iconChevronRight} alt="" />
          </span>
        )}
        <strong>{node.name}</strong>
        <span>{node.totalCount}</span>
      </button>
      {kids.length > 0 && isOpen && (
        <ul>
          {kids.map((c) => (
            <ChartNode key={c.id} node={c} open={open} toggle={toggle} selectedId={selectedId} onSelect={onSelect} />
          ))}
        </ul>
      )}
    </li>
  );
}

function TreeCard({ root, depts, open, toggle, selectedId, onSelect }) {
  // 좁은 화면 — 고른 부서 박스가 가로 스크롤 가운데 오게
  const scrollRef = useRef(null);
  useEffect(() => {
    const box = scrollRef.current;
    const target = box?.querySelector('.org-box.is-selected') ?? box?.querySelector('.org-box.is-root');
    if (!box || !target || box.scrollWidth <= box.clientWidth) return;
    const r = target.getBoundingClientRect();
    const b = box.getBoundingClientRect();
    box.scrollLeft += r.left - b.left - (b.width - r.width) / 2;
  }, [selectedId, open]);
  return (
    <section className="org-card" id="org-tree">
      <div className="org-card-head">
        <div>
          <h2>회사 조직 구조 트리</h2>
          <p>회사 → 본부 → 팀 계층 구조</p>
        </div>
        <span className="org-muted">{`총 ${root.totalCount}명 · ${depts}개 부서`}</span>
      </div>
      <div className="org-chart">
        <div className="org-chart-top">
          <strong>조직 구조</strong>
          <span>{`${depts}개 부서`}</span>
        </div>
        <div className="org-chart-scroll" ref={scrollRef}>
          <ul className="org-chart-tree">
            <ChartNode node={root} isRoot open={open} toggle={toggle} selectedId={selectedId} onSelect={onSelect} />
          </ul>
        </div>
      </div>
      <p className="org-muted">{`상태: ${Object.values(LABELS.orgWorkStatus)
        .map((v) => v.label)
        .join(' · ')}`}</p>
    </section>
  );
}

/* ───── 부서원 카드 ───── */
function MemberCard({ member, selected, onClick }) {
  return (
    <button type="button" className={`org-mcard${selected ? ' is-selected' : ''}`} onClick={onClick}>
      <Avatar member={member} />
      <strong>{member.name}</strong>
      <em>{[member.orgUnitName, gradeTitle(member)].filter(Boolean).join(' · ')}</em>
      <WorkBadge status={member.workStatus} />
    </button>
  );
}

/* ───── 부서 정보 · 팀원 구성 ───── */
function DeptCard({ node, members, loading, includeSub, setIncludeSub, selectedMemberId, onMember }) {
  const [all, setAll] = useState(false);
  const shown = all ? members : members.slice(0, PREVIEW);
  const title = all || members.length <= PREVIEW ? `${node.name} · ${members.length}명` : `${node.name} · ${members.length}명 중 ${PREVIEW}명 미리보기`;
  return (
    <section className="org-card org-dept" id="org-dept">
      <div className="org-card-head">
        <div>
          <span className="org-eyebrow">부서 정보 · 팀원 구성</span>
          <h2 className="is-big">{title}</h2>
          <p>{`${node.levelName ?? '조직'}${node.lead ? ` · 조직장 ${node.lead.name}` : ' · 조직장 없음'}`}</p>
        </div>
        <span className="org-badge">{`${node.totalCount}명`}</span>
      </div>
      {node.children?.length > 0 && (
        <label className="org-check">
          <input type="checkbox" checked={includeSub} onChange={(e) => setIncludeSub(e.target.checked)} />
          하위 조직 직원도 보기
        </label>
      )}
      {loading && <Spin />}
      {!loading && members.length === 0 && <p className="org-empty">이 조직에 바로 소속된 직원이 없습니다.</p>}
      <div className="org-mcards">
        {shown.map((m) => (
          <MemberCard key={m.id} member={{ ...m, orgUnitName: m.orgUnitName ?? node.name }} selected={selectedMemberId === m.id} onClick={() => onMember(m)} />
        ))}
      </div>
      {members.length > PREVIEW && (
        <button type="button" className="org-more" onClick={() => setAll((v) => !v)}>
          {all ? '미리보기로 접기' : `부서원 전체 보기 (${members.length}명)`}
        </button>
      )}
      <div className="org-rr">
        <strong>부서 R&amp;R</strong>
        <span>부서 R&amp;R(역할 · 책임) 정보는 준비 중입니다.</span>
      </div>
    </section>
  );
}

/* ───── 검색 결과 — 부서 정보 자리에 ───── */
function SearchCard({ q, term, status, deptHits, hits, searching, selectedMemberId, onDept, onMember, onClear }) {
  return (
    <section className="org-card org-dept" id="org-dept">
      <div className="org-card-head">
        <div>
          <span className="org-eyebrow">동료 찾기</span>
          <h2 className="is-big">검색 결과</h2>
          <p>
            {[term && `"${q.trim()}"`, status !== 'ALL' && STATUS_FILTER.find(([k]) => k === status)[1], `부서 ${deptHits.length}곳 · 직원 ${hits.length}명`]
              .filter(Boolean)
              .join(' · ')}
          </p>
        </div>
        <button type="button" className="org-btn" onClick={onClear}>
          검색 지우기
        </button>
      </div>
      {searching && <Spin />}
      {deptHits.length > 0 && (
        <div className="org-chips">
          {deptHits.map(({ node: n }) => (
            <button key={n.id} type="button" className="org-chip" onClick={() => onDept(n.id)}>
              {`${n.name} · ${n.totalCount}명`}
            </button>
          ))}
        </div>
      )}
      <div className="org-mcards">
        {hits.map((m) => (
          <MemberCard key={m.id} member={m} selected={selectedMemberId === m.id} onClick={() => onMember(m)} />
        ))}
      </div>
      {!searching && !deptHits.length && !hits.length && <p className="org-empty">검색 결과가 없습니다.</p>}
    </section>
  );
}

/**
 * 임직원 프로필 카드 + 연락 · 소통(Figma ProfileCard 안 CommunicationCard).
 * [메일 보내기]는 회사 메일(email)로 메일 앱의 새 메일 창을 연다(mailto, 서버 발송 아님).
 * 수신: "부서 전체 선택"이면 그 부서원(나 제외), 아니면 프로필 카드의 직원. 메신저 · 알림은 준비 중
 */
function ProfileCard({ member, deptName, node, members, meId, onPending }) {
  const [whole, setWhole] = useState(false);
  const deptMails = members.filter((m) => m.email && m.id !== meId).map((m) => m.email);
  const to = whole ? deptMails : member?.email ? [member.email] : [];
  let target = '선택 직원 또는 부서 전체';
  if (whole) target = `${node?.name ?? '부서'} ${deptMails.length}명`;
  else if (member) target = member.email ? `${member.name} (${member.email})` : `${member.name} · 메일 주소 없음`;

  return (
    <section className="org-card org-profile-card">
      <div className="org-card-head">
        <div>
          <h2>임직원 프로필 카드</h2>
          <p>동료 찾기 / 직원 기본 정보</p>
        </div>
        {member && <WorkBadge status={member.workStatus} />}
      </div>
      {!member && <p className="org-empty">부서원이나 검색 결과에서 직원을 고르면 기본 정보가 보입니다.</p>}
      {member && (
        <>
          <div className="org-profile">
            <Avatar member={member} size="lg" />
            <div>
              <strong>{member.name}</strong>
              <span>{`${member.orgUnitName ?? deptName} · ${gradeTitle(member)}`}</span>
            </div>
          </div>
          <div className="org-rows">
            <div className="org-row">
              <span>사내 전화번호</span>
              <strong>{officePhone(member.id)}</strong>
            </div>
            <div className="org-row">
              <span>이메일</span>
              {member.email ? (
                <a href={`mailto:${member.email}`} className="org-mail">
                  {member.email}
                </a>
              ) : (
                <strong className="is-muted">-</strong>
              )}
            </div>
          </div>
        </>
      )}
      <div className="org-divider" />
      <div className="org-contact" id="org-contact">
        <div className="org-contact-head">
          <h3>연락·소통</h3>
          {node && (
            <label className="org-check">
              <input type="checkbox" checked={whole} onChange={(e) => setWhole(e.target.checked)} />
              {`${node.name} 전체 선택 (${deptMails.length}명)`}
            </label>
          )}
        </div>
        <div className="org-btns">
          <button
            type="button"
            className="org-btn is-primary"
            disabled={to.length === 0}
            onClick={() => {
              window.location.href = `mailto:${to.join(',')}`;
            }}
          >
            메일 보내기
          </button>
          {['메신저 연결', '알림 발송'].map((t) => (
            <button key={t} type="button" className="org-btn" onClick={() => onPending(t)}>
              {t}
            </button>
          ))}
        </div>
        <p className="org-muted">{`수신 대상: ${target}`}</p>
      </div>
    </section>
  );
}

/** 조직도(F-ORG-01 조회) — Figma COMMON_org_chart. 로그인만 하면 본다 */
export default function OrgChartPage() {
  const { me } = useMe();
  const pending = usePendingFeature();
  const searchRef = useRef(null);
  const tree = useQuery({ queryKey: orgTreeKey, queryFn: getOrgTree });
  const flat = useMemo(() => (tree.data ? flatten(tree.data) : []), [tree.data]);
  const [menu, setMenu] = useState('tree');
  const [selectedId, setSelectedId] = useState(null);
  const [open, setOpen] = useState(new Set());
  const [includeSub, setIncludeSub] = useState(false);
  const [member, setMember] = useState(null);
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('ALL');

  // 처음에는 내 조직을 고르고 그 경로를 펼친다
  useEffect(() => {
    if (!flat.length || selectedId != null) return;
    const mine = flat.some((f) => f.node.id === me?.employee?.orgUnitId) ? me.employee.orgUnitId : flat[0].node.id;
    setSelectedId(mine);
    setOpen(new Set(pathTo(flat, mine)));
  }, [flat, selectedId, me]);

  const node = flat.find((f) => f.node.id === selectedId)?.node;
  const members = useQuery({
    queryKey: orgMembersKey(selectedId, includeSub),
    queryFn: () => getOrgMembers(selectedId, includeSub),
    enabled: selectedId != null,
  });

  // 동료 찾기 — 루트 조직 + 하위 포함 한 번(행에 소속 이름이 온다, R-116). 검색 · 상태 필터를 쓸 때만
  const term = q.trim().toLowerCase();
  const rootId = flat[0]?.node.id;
  const filtering = term.length > 0 || status !== 'ALL';
  const all = useQuery({
    queryKey: orgMembersKey(rootId, true),
    queryFn: () => getOrgMembers(rootId, true),
    enabled: filtering && rootId != null,
    staleTime: 60_000,
  });
  const byStatus = (m) => status === 'ALL' || (status === 'NONE' ? !m.workStatus : m.workStatus === status);
  const hits = filtering
    ? (all.data ?? []).filter((m) => byStatus(m) && (!term || [m.name, m.jobGradeName, m.jobTitleName, m.orgUnitName, m.email].some((v) => v?.toLowerCase().includes(term))))
    : [];
  const deptHits = term ? flat.filter((f) => f.node.name.toLowerCase().includes(term)) : [];
  const searching = all.isLoading && filtering;

  if (tree.isLoading) {
    return (
      <div className="page-center" style={{ minHeight: '25rem' }}>
        <Spin size="large" />
      </div>
    );
  }
  if (tree.error) return <Result status="warning" title="조직도를 불러오지 못했습니다" subTitle={errorMessage(tree.error)} />;

  const select = (id) => {
    setSelectedId(id);
    setIncludeSub(false);
    setMember(null);
    setOpen((prev) => new Set([...prev, ...pathTo(flat, id)]));
  };
  const toggle = (id) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const go = (key) => {
    setMenu(key);
    if (key === 'search') searchRef.current?.focus();
    else document.getElementById(`org-${key}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };
  // 조직장을 맨 앞으로
  const memberList = [...(members.data ?? [])].sort((x, y) => (y.id === node?.lead?.id) - (x.id === node?.lead?.id));
  const shownMember = filtering ? member : (member ?? memberList.find((m) => m.id === node?.lead?.id) ?? memberList[0] ?? null);

  return (
    <div className="org">
      <header className="org-head">
        <h1>조직도</h1>
        <p>{`${tree.data.name} 부서별 조직 구성을 조회합니다.`}</p>
      </header>

      <div className="org-tabs-row">
        <nav className="org-tabs" aria-label="조직도 메뉴">
          {MENU.map(([k, l]) => (
            <button key={k} type="button" className={menu === k ? 'is-active' : undefined} onClick={() => go(k)}>
              {l}
            </button>
          ))}
        </nav>
        <div className="org-controls">
          <label className="org-search">
            <img src={iconSearch} alt="" />
            <input ref={searchRef} value={q} onChange={(e) => setQ(e.target.value)} placeholder="부서명, 직원명, 직급 검색" aria-label="조직도 검색" />
          </label>
          <span className="org-select">
            <select aria-label="부서 선택" value={selectedId ?? ''} onChange={(e) => select(Number(e.target.value))}>
              {flat.map(({ node: n, depth }) => (
                <option key={n.id} value={n.id}>
                  {`${'　'.repeat(depth)}${n.name}`}
                </option>
              ))}
            </select>
            <img src={iconChevron} alt="" />
          </span>
          <span className="org-select">
            <select aria-label="근무 상태" value={status} onChange={(e) => setStatus(e.target.value)}>
              {STATUS_FILTER.map(([k, l]) => (
                <option key={k} value={k}>
                  {l}
                </option>
              ))}
            </select>
            <img src={iconChevron} alt="" />
          </span>
        </div>
      </div>

      <TreeCard root={tree.data} depts={flat.length - 1} open={open} toggle={toggle} selectedId={selectedId} onSelect={select} />

      <div className="org-main">
        {filtering ? (
          <SearchCard
            q={q}
            term={term}
            status={status}
            deptHits={deptHits}
            hits={hits}
            searching={searching}
            selectedMemberId={shownMember?.id}
            onDept={(id) => {
              select(id);
              setQ('');
            }}
            onMember={setMember}
            onClear={() => {
              setQ('');
              setStatus('ALL');
            }}
          />
        ) : (
          node && (
            <DeptCard
              key={`${node.id}-${includeSub}`}
              node={node}
              members={memberList}
              loading={members.isLoading}
              includeSub={includeSub}
              setIncludeSub={setIncludeSub}
              selectedMemberId={shownMember?.id}
              onMember={setMember}
            />
          )
        )}
        <ProfileCard
          member={shownMember}
          deptName={node?.name}
          node={node}
          members={memberList}
          meId={me?.employee?.id}
          onPending={(t) => pending(t, '조직도 연락 · 소통')}
        />
      </div>
    </div>
  );
}
