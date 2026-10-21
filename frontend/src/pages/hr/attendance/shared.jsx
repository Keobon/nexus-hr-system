// 근태 현황 탭들이 같이 쓰는 것 — 조회 조건 줄 · 목록 카드 · 상태 알약 · 시간 표기
import { useMemo, useState } from 'react';
import { DatePicker, Input, Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { fetchAllPages, getEmployees } from '../../../api/employees';
import { useOrgOptions } from '../parts';

const WEEK = ['일', '월', '화', '수', '목', '금', '토'];
export const hm = (t) => (t ? dayjs(t).format('HH:mm') : '–');
/** 분 → "168:30"(Figma 근태 집계 표기) */
export const hhmm = (min) => (min == null ? '–' : `${Math.floor(min / 60)}:${String(min % 60).padStart(2, '0')}`);
export const dot = (d) => (d ? d.slice(0, 10).replaceAll('-', '.') : '–');
export const dotDay = (d) => `${dot(d)} (${WEEK[dayjs(d).day()]})`;
export const range = (a, b) => (a === b ? dot(a) : `${dot(a)}–${dot(b).slice(5)}`);
/** 이름 · 사번 검색어 — 띄어쓰기 무시 */
export const matches = (q, ...fields) => !q.trim() || fields.some((f) => (f ?? '').replace(/\s/g, '').includes(q.replace(/\s/g, '')));

/** 상태 알약 — tone: ok · warn · info · alert · gray · purple · cyan */
export function Pill({ tone = 'gray', children }) {
  return <span className={`at-pill is-${tone}`}>{children}</span>;
}

/** 조회 조건 — 입력값은 바로 반영하지 않고 [조회]를 눌러야 바뀐다(Figma 조회 버튼) */
export function useApplied(initial) {
  const [draft, setDraft] = useState(initial);
  const [applied, setApplied] = useState(initial);
  return { draft, applied, set: (patch) => setDraft((d) => ({ ...d, ...patch })), apply: () => setApplied(draft) };
}

export function Field({ label, width, children }) {
  return (
    <label className="at-field" style={width ? { width } : undefined}>
      <span>{label}</span>
      {children}
    </label>
  );
}

export function MonthField({ value, onChange }) {
  return (
    <Field label="월" width="11.25rem">
      <DatePicker picker="month" value={dayjs(`${value}-01`)} allowClear={false} format="YYYY년 MM월" onChange={(v) => v && onChange(v.format('YYYY-MM'))} />
    </Field>
  );
}

export function OrgField({ value, onChange }) {
  const org = useOrgOptions();
  return (
    <Field label="조직 (하위 포함)" width="12.5rem">
      <Select value={value ?? 'ALL'} onChange={(v) => onChange(v === 'ALL' ? null : v)} options={[{ value: 'ALL', label: '전체 조직' }, ...org.options]} showSearch optionFilterProp="label" />
    </Field>
  );
}

export function SearchField({ value, onChange, onEnter }) {
  return (
    <Field label="직원" width="13.75rem">
      <Input value={value} placeholder="이름 또는 사번 검색" allowClear onChange={(e) => onChange(e.target.value)} onPressEnter={onEnter} />
    </Field>
  );
}

export function SearchButton({ onClick }) {
  return (
    <button type="button" className="at-btn" onClick={onClick}>
      조회
    </button>
  );
}

/** 목록 카드 — 머리(제목 · 오른쪽 안내) + 표 */
export function ListCard({ title, note, children }) {
  return (
    <section className="at-card">
      <div className="at-card-head">
        <strong>{title}</strong>
        {note && <span>{note}</span>}
      </div>
      {children}
    </section>
  );
}

/** 표 — cols: [{ key, label, width?, align? }] · 행은 children 으로 <tr> */
export function Table({ cols, children, empty, loading, error, colSpan }) {
  return (
    <div className="at-table-wrap">
      <table className="at-table">
        <colgroup>
          {cols.map((c) => (
            <col key={c.key} style={c.width ? { width: c.width } : undefined} />
          ))}
        </colgroup>
        <thead>
          <tr>
            {cols.map((c) => (
              <th key={c.key} className={c.align ? `is-${c.align}` : undefined}>
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading || error || empty ? (
            <tr className="is-empty">
              <td colSpan={colSpan ?? cols.length}>{loading ? '불러오는 중…' : error ?? empty}</td>
            </tr>
          ) : (
            children
          )}
        </tbody>
      </table>
    </div>
  );
}

/** 신청자 칸 — 이름 굵게 + 소속 */
export function Person({ name, org }) {
  return (
    <div className="at-person">
      <strong>{name}</strong>
      <span>{org}</span>
    </div>
  );
}

/** 직원 ID → 사번 — 오늘 근태 · 연장 · 출장 행에는 사번이 없어 직원 목록으로 채운다(못 읽으면 빈 값) */
export function useEmployeeNos() {
  const q = useQuery({ queryKey: ['employees', 'all-nos'], queryFn: () => fetchAllPages(getEmployees, {}), staleTime: 5 * 60 * 1000 });
  return useMemo(() => Object.fromEntries((q.data ?? []).map((e) => [e.id, e.employeeNo])), [q.data]);
}
