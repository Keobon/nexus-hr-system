import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { employeeFieldsKey, getEmployeeFields, myProfileKey, updateMyFieldValues } from '../../../api/profile';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { Badge, Note } from './parts';
import { rem } from '../../../utils/size';

/**
 * 학력 · 경력 · 자격증 · 어학 · 보유 기술은 회사가 만든 "추가 항목"(여러 건 허용)으로 받고, 본인 수정 가능 항목이면 바로 고친다.
 * 노션 수정사항(10/12) — 전용 테이블 · 승인 절차 없이 PUT /me/field-values(교수님 요구 #2의 예시와 같은 방식).
 * 회사에 그 이름의 항목이 없으면 "회사 설정에서 만들면 입력할 수 있다"고 안내한다.
 */
const SECTIONS = [
  { key: 'education', title: '학력', fieldNames: ['학력'], placeholder: '예: 한국대학교 · 컴퓨터공학 · 졸업' },
  { key: 'career', title: '이전 직장 경력', fieldNames: ['이전 직장 경력', '경력'], placeholder: '예: OO전자 · 2020.03–2023.02 · 백엔드 개발' },
  { key: 'license', title: '자격증', fieldNames: ['자격증'], placeholder: '예: 정보처리기사 · 한국산업인력공단 · 2021.06' },
  { key: 'language', title: '어학 점수', fieldNames: ['어학 점수', '어학'], placeholder: '예: TOEIC · 900 · 2024.05' },
  { key: 'skills', title: '보유 기술', fieldNames: ['보유 기술', '기술 스택'], placeholder: '예: React', chips: true },
];
const KNOWN = SECTIONS.flatMap((s) => s.fieldNames);

/** 값 입력칸 — 항목 타입(텍스트 · 긴 텍스트 · 숫자 · 날짜 · 선택)에 맞춰 */
function ValueInput({ def, value, onChange, placeholder, id }) {
  const common = { id, className: 'mi-input is-40', value, onChange: (e) => onChange(e.target.value) };
  if (def.fieldType === 'SELECT') {
    return (
      <select {...common}>
        <option value="">선택</option>
        {(def.options ?? []).map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
    );
  }
  if (def.fieldType === 'DATE') return <input {...common} type="date" />;
  if (def.fieldType === 'NUMBER') return <input {...common} type="number" />;
  return <input {...common} maxLength={def.fieldType === 'LONG_TEXT' ? 1000 : 200} placeholder={placeholder} />;
}

/**
 * 본인 수정 항목 저장 — 서버가 본인 수정 항목 전체를 교체하므로 다른 항목 값도 같이 보낸다.
 * save(defId, ['값1', '값2']) → 그 항목만 바꾼 전체 목록을 PUT
 */
function useSaveField(profile, defs) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const editable = new Set(defs.filter((d) => d.isSelfEditable).map((d) => d.id));
  const m = useMutation({
    mutationFn: ({ defId, values }) => {
      const others = (profile.fieldValues ?? [])
        .filter((v) => editable.has(v.fieldDefId) && v.fieldDefId !== defId)
        .map((v) => ({ fieldDefId: v.fieldDefId, seq: v.seq, value: v.value }));
      const mine = values.map((value, i) => ({ fieldDefId: defId, seq: i + 1, value }));
      return updateMyFieldValues([...others, ...mine]);
    },
    onSuccess: () => {
      notify.success('저장했습니다');
      queryClient.invalidateQueries({ queryKey: myProfileKey });
    },
  });
  return m;
}

function FieldSection({ section, def, profile, save }) {
  const rows = def ? (profile.fieldValues ?? []).filter((v) => v.fieldDefId === def.id).map((v) => v.value) : [];
  const [draft, setDraft] = useState('');
  const [editing, setEditing] = useState(null); // { index, value }
  const [error, setError] = useState(null);
  const canEdit = def?.isSelfEditable;
  const canAdd = canEdit && (def.isMultiple || rows.length === 0);

  const commit = (values, after) => {
    setError(null);
    save.mutate({ defId: def.id, values }, { onSuccess: after, onError: (err) => setError(errorMessage(err)) });
  };
  const add = () => {
    if (!draft.trim()) return setError('내용을 입력해 주세요');
    return commit([...rows, draft.trim()], () => setDraft(''));
  };
  const update = () => {
    if (!editing.value.trim()) return setError('내용을 입력해 주세요');
    return commit(rows.map((r, i) => (i === editing.index ? editing.value.trim() : r)), () => setEditing(null));
  };
  const remove = (index) => commit(rows.filter((_, i) => i !== index));

  let badge = <Badge>회사 항목 없음</Badge>;
  if (def) badge = canEdit ? <Badge tone="green">본인 수정</Badge> : <Badge>인사팀 수정</Badge>;

  return (
    <section className="mi-card">
      <div className="mi-card-head">
        <h2 className="mi-card-title">{section.title}</h2>
        <div className="mi-badges">
          {badge}
          {def && <Badge>{rows.length ? `등록 ${rows.length}건` : '미등록'}</Badge>}
        </div>
      </div>

      {!def && (
        <p className="mi-list-empty">
          {`회사 설정 › 직원 정보 항목에 "${section.fieldNames[0]}" 항목이 만들어지면 여기서 바로 입력할 수 있습니다.`}
        </p>
      )}

      {def && section.chips && (
        <div className="mi-chips">
          {rows.length === 0 && <p className="mi-list-empty" style={{ width: '100%' }}>{`등록된 ${section.title} 정보가 없습니다.`}</p>}
          {rows.map((r, i) => (
            <span key={`${r}-${i}`} className="mi-badge" style={{ gap: rem(6) }}>
              {r}
              {canEdit && (
                <button type="button" className="mi-link" aria-label={`${r} 삭제`} disabled={save.isPending} onClick={() => remove(i)}>
                  ✕
                </button>
              )}
            </span>
          ))}
        </div>
      )}

      {def && !section.chips && (
        <div>
          {rows.length === 0 && <p className="mi-list-empty">{`등록된 ${section.title} 정보가 없습니다.`}</p>}
          {rows.map((r, i) =>
            editing?.index === i ? (
              <div key={`edit-${i}`} className="mi-list-row" style={{ display: 'flex', gap: rem(8), alignItems: 'center' }}>
                <ValueInput def={def} value={editing.value} onChange={(value) => setEditing({ index: i, value })} placeholder={section.placeholder} id={`${section.key}-edit`} />
                <button type="button" className="mi-btn" style={{ flex: 'none', width: rem(72) }} disabled={save.isPending} onClick={update}>
                  저장
                </button>
                <button type="button" className="mi-btn is-secondary" style={{ flex: 'none', width: rem(72) }} onClick={() => setEditing(null)}>
                  취소
                </button>
              </div>
            ) : (
              <div key={`${r}-${i}`} className="mi-list-row" style={{ display: 'flex', justifyContent: 'space-between', gap: rem(12) }}>
                <span style={{ minWidth: 0, wordBreak: 'break-word' }}>{r}</span>
                {canEdit && (
                  <span className="mi-cert-links" style={{ flex: 'none' }}>
                    <button type="button" className="mi-link" onClick={() => setEditing({ index: i, value: r })}>
                      수정
                    </button>
                    <button type="button" className="mi-link" style={{ color: '#ef4444' }} disabled={save.isPending} onClick={() => remove(i)}>
                      삭제
                    </button>
                  </span>
                )}
              </div>
            ),
          )}
        </div>
      )}

      {canAdd && (
        <div className="mi-field is-small">
          <label htmlFor={`${section.key}-new`}>{`${section.title} 추가`}</label>
          <div style={{ display: 'flex', gap: rem(8) }}>
            <ValueInput def={def} value={draft} onChange={setDraft} placeholder={section.placeholder} id={`${section.key}-new`} />
            <button type="button" className="mi-btn" style={{ flex: 'none', width: rem(96) }} disabled={save.isPending} onClick={add}>
              추가
            </button>
          </div>
        </div>
      )}
      {error && <p className="mi-error">{error}</p>}
    </section>
  );
}

/** 학력 및 경력 탭 — Figma EMP_학력및경력 */
export default function CareerTab({ profile }) {
  const fields = useQuery({ queryKey: employeeFieldsKey, queryFn: getEmployeeFields });
  const defs = fields.data ?? [];
  const save = useSaveField(profile, defs);
  const defOf = (names) => defs.find((d) => names.includes(d.name));
  // 위 다섯 칸에 없는 회사 추가 항목도 같은 방식으로 아래에 보여 준다
  const others = defs.filter((d) => !KNOWN.includes(d.name));

  return (
    <div className="mi-stack">
      {SECTIONS.map((s) => (
        <FieldSection key={s.key} section={s} def={defOf(s.fieldNames)} profile={profile} save={save} />
      ))}
      {others.map((d) => (
        <FieldSection key={d.id} section={{ key: `f${d.id}`, title: d.name, fieldNames: [d.name], placeholder: '' }} def={d} profile={profile} save={save} />
      ))}

      <Note variant="page">
        <p>
          • <strong>직원 본인 수정 항목</strong>: 휴대폰 번호, 비상연락처, 거주지 주소, 사내 프로필 사진, 비밀번호, 급여 계좌, 그리고 회사가
          &quot;본인 수정 가능&quot;으로 정한 학력 · 경력 · 자격증 같은 항목은 수정하면 바로 반영됩니다.
        </p>
        <p>
          • <strong>인사팀 처리 항목</strong>: 개명(이름 변경)은 인사팀에 문의해 주세요. 인사 담당이 직원 정보를 고쳐 반영합니다.
        </p>
        <p>
          • <strong>인사팀 전결 항목</strong>: 사번, 부서, 직급/직책, 입사일, 계약 형태 등은 인사발령이나 인사이동을 통해서만 변경됩니다.
        </p>
      </Note>
    </div>
  );
}
