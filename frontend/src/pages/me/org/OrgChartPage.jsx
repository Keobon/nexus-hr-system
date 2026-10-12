import { useEffect, useMemo, useRef, useState } from 'react';
import { Result, Spin } from 'antd';
import { useQueries, useQuery } from '@tanstack/react-query';
import { getOrgMembers, getOrgTree, orgMembersKey, orgTreeKey } from '../../../api/org';
import { useMe } from '../../../auth/useMe';
import { useFileUrl } from '../../../components/common/useFileUrl';
import { usePendingFeature } from '../../../components/common/usePendingFeature';
import { errorMessage } from '../../../constants/errors';
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

/** 트리를 [{ node, depth, parentId }] 로 펼친다(정렬 순서대로) */
function flatten(node, depth = 0, parentId = null, out = []) {
  out.push({ node, depth, parentId });
  [...(node.children ?? [])].sort((a, b) => a.sortOrder - b.sortOrder).forEach((c) => flatten(c, depth + 1, node.id, out));
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

function Avatar({ member, big }) {
  const url = useFileUrl(member.profileFileId);
  const color = AVATAR_COLORS[member.id % AVATAR_COLORS.length];
  return (
    <span className={`org-avatar${big ? ' is-big' : ''}`} style={url ? undefined : { background: color }}>
      {url ? <img src={url} alt="" /> : member.name.slice(0, 1)}
    </span>
  );
}

/* ───── 회사 조직 구조 트리 ───── */
function TreeCard({ flat, open, toggle, selectedId, onSelect, total }) {
  const visible = flat.filter((f) => pathTo(flat, f.node.id).slice(0, -1).every((id) => open.has(id)));
  const depts = flat.length - 1;
  return (
    <section className="org-card" id="org-tree">
      <div className="org-card-head">
        <div>
          <h2>회사 조직 구조 트리</h2>
          <p>회사 → 본부 → 팀 계층 구조</p>
        </div>
      </div>
      <div className="org-tree">
        <div className="org-tree-top">
          <strong>조직 구조</strong>
          <span>{`${depts}개 부서`}</span>
        </div>
        {visible.map(({ node, depth }) => {
          const hasKids = node.children?.length > 0;
          const isOpen = open.has(node.id);
          return (
            <button
              key={node.id}
              type="button"
              className={`org-node${hasKids && isOpen ? ' is-open' : ''}${selectedId === node.id ? ' is-selected' : ''}`}
              style={{ paddingLeft: `${0.625 + depth * 0.75}rem` }}
              onClick={() => onSelect(node.id)}
            >
              <span
                className="org-toggle"
                role={hasKids ? 'button' : undefined}
                aria-label={hasKids ? (isOpen ? '접기' : '펼치기') : undefined}
                onClick={(e) => {
                  if (!hasKids) return;
                  e.stopPropagation();
                  toggle(node.id);
                }}
              >
                {hasKids && <img src={isOpen ? iconChevronDown : iconChevronRight} alt="" />}
              </span>
              <span className="org-name">{node.name}</span>
              <span className="org-count">{node.totalCount}</span>
            </button>
          );
        })}
      </div>
      <p className="org-tree-foot">{`총 ${total}명 · ${depts}개 부서`}</p>
    </section>
  );
}

/* ───── 부서 정보 · 팀원 구성 ───── */
function DeptCard({ node, members, loading, includeSub, setIncludeSub, selectedMemberId, onMember }) {
  return (
    <section className="org-card is-tight" id="org-dept">
      <div className="org-card-head">
        <div>
          <h2>부서 정보 · 팀원 구성</h2>
          <p>{`${node.name} · ${node.levelName ?? ''}${node.lead ? ` · 조직장 ${node.lead.name}` : ' · 조직장 없음'}`}</p>
        </div>
        <span className="org-badge">{`${node.totalCount}명`}</span>
      </div>
      {node.children?.length > 0 && (
        <label className="org-muted" style={{ display: 'flex', gap: 6, alignItems: 'center', margin: 0 }}>
          <input type="checkbox" checked={includeSub} onChange={(e) => setIncludeSub(e.target.checked)} />
          하위 조직 직원도 보기
        </label>
      )}
      <div className="org-members">
        {loading && <Spin />}
        {!loading && members.length === 0 && <p className="org-empty">이 조직에 바로 소속된 직원이 없습니다.</p>}
        {members.map((m) => (
          <button key={m.id} type="button" className={`org-member${selectedMemberId === m.id ? ' is-selected' : ''}`} onClick={() => onMember(m)}>
            <Avatar member={m} />
            <span>
              <strong>{m.name}</strong>
              <em>{`${m.orgUnitName ?? node.name} · ${gradeTitle(m)}`}</em>
            </span>
            {node.lead?.id === m.id && <span className="org-lead">조직장</span>}
          </button>
        ))}
      </div>
    </section>
  );
}

/* ───── 임직원 프로필 카드 ───── */
function ProfileCard({ member, deptName }) {
  return (
    <section className="org-card is-tight">
      <div className="org-card-head">
        <div>
          <h2>임직원 프로필 카드</h2>
          <p>동료 찾기 / 직원 기본 정보</p>
        </div>
      </div>
      {!member && <p className="org-empty">부서원이나 검색 결과에서 직원을 고르면 기본 정보가 보입니다.</p>}
      {member && (
        <>
          <div className="org-profile">
            <Avatar member={member} big />
            <div>
              <strong>{member.name}</strong>
              <span>{`${member.orgUnitName ?? deptName} · ${gradeTitle(member)}`}</span>
            </div>
          </div>
          <div className="org-rows">
            <div className="org-row">
              <span>소속</span>
              <span>{member.orgUnitName ?? deptName}</span>
            </div>
            <div className="org-row">
              <span>직급 / 직책</span>
              <span>{gradeTitle(member)}</span>
            </div>
            <div className="org-row">
              <span>연락처 · 이메일 · 좌석</span>
              <span className="is-muted">준비 중</span>
            </div>
          </div>
        </>
      )}
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

  // 동료 찾기 — 조직마다 직속 직원을 받아 소속 이름을 붙인다(검색할 때만)
  const term = q.trim().toLowerCase();
  const all = useQueries({
    queries: flat.map((f) => ({
      queryKey: orgMembersKey(f.node.id, false),
      queryFn: () => getOrgMembers(f.node.id, false),
      enabled: term.length > 0,
      staleTime: 60_000,
    })),
  });
  const people = term
    ? all.flatMap((r, i) => (r.data ?? []).map((m) => ({ ...m, orgUnitId: flat[i].node.id, orgUnitName: flat[i].node.name })))
    : [];
  const hits = people.filter((m) => [m.name, m.jobGradeName, m.jobTitleName, m.orgUnitName].some((v) => v?.toLowerCase().includes(term)));
  const deptHits = term ? flat.filter((f) => f.node.name.toLowerCase().includes(term)) : [];
  const searching = all.some((r) => r.isLoading);

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
  // 조직장을 맨 위로
  const memberList = (members.data ?? [])
    .map((m) => (includeSub ? m : { ...m, orgUnitName: node?.name }))
    .sort((x, y) => (y.id === node?.lead?.id) - (x.id === node?.lead?.id));
  const shownMember = member ?? memberList.find((m) => m.id === node?.lead?.id) ?? memberList[0] ?? null;

  return (
    <div className="org">
      <header className="org-head">
        <h1>조직도</h1>
        <p>{`${tree.data.name} 부서별 조직 구성을 조회합니다.`}</p>
      </header>

      <nav className="org-menu" aria-label="조직도 메뉴">
        {MENU.map(([k, l]) => (
          <button key={k} type="button" className={menu === k ? 'is-active' : undefined} onClick={() => go(k)}>
            {l}
          </button>
        ))}
      </nav>

      <div className="org-search-row">
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
      </div>

      <div className="org-body">
        <TreeCard flat={flat} open={open} toggle={toggle} selectedId={selectedId} onSelect={select} total={tree.data.totalCount} />

        <div className="org-right">
          {term ? (
            <section className="org-card is-tight" id="org-dept">
              <div className="org-card-head">
                <div>
                  <h2>검색 결과</h2>
                  <p>{`"${q.trim()}" · 부서 ${deptHits.length}곳 · 직원 ${hits.length}명`}</p>
                </div>
                <button type="button" className="org-btn" onClick={() => setQ('')}>
                  검색 지우기
                </button>
              </div>
              <div className="org-members">
                {searching && <Spin />}
                {deptHits.map(({ node: n }) => (
                  <button key={`d${n.id}`} type="button" className="org-member" onClick={() => { select(n.id); setQ(''); }}>
                    <span className="org-avatar" style={{ background: '#94a3b8' }}>부</span>
                    <span>
                      <strong>{n.name}</strong>
                      <em>{`${n.levelName ?? '조직'} · ${n.totalCount}명`}</em>
                    </span>
                  </button>
                ))}
                {hits.map((m) => (
                  <button
                    key={`m${m.id}`}
                    type="button"
                    className={`org-member${shownMember?.id === m.id ? ' is-selected' : ''}`}
                    onClick={() => setMember(m)}
                  >
                    <Avatar member={m} />
                    <span>
                      <strong>{m.name}</strong>
                      <em>{`${m.orgUnitName} · ${gradeTitle(m)}`}</em>
                    </span>
                  </button>
                ))}
                {!searching && !deptHits.length && !hits.length && <p className="org-empty">검색 결과가 없습니다.</p>}
              </div>
            </section>
          ) : (
            node && (
              <DeptCard
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

          <ProfileCard member={term ? member : shownMember} deptName={node?.name} />

          <section className="org-card is-tight" id="org-contact">
            <div className="org-card-head">
              <div>
                <h2>연락·소통</h2>
                <p>메일 · 메신저 · 알림 발송</p>
              </div>
            </div>
            <div className="org-btns">
              {['메일 보내기', '메신저 연결', '알림 발송'].map((t, i) => (
                <button key={t} type="button" className={`org-btn${i === 0 ? ' is-primary' : ''}`} onClick={() => pending(t, '조직도 연락 · 소통')}>
                  {t}
                </button>
              ))}
              <span className="org-muted" style={{ margin: 0 }}>
                메일 · 메신저 · 알림 발송은 준비 중입니다.
              </span>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
