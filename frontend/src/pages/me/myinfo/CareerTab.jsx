import { useState } from 'react';
import { Badge, EvidencePicker, Note, usePendingFeature } from './parts';
import { rem } from '../../../utils/size';

/**
 * 학력 · 이전 직장 경력 · 자격증은 증빙 제출 후 승인(추가사항 ②) — 백엔드 API 가 생기기 전이라 보내기만 막는다.
 * 지금 보여 줄 값: 회사가 만든 "추가 항목"(GET /me/profile 의 fieldValues) 중 이름이 맞는 것(예: 데모 회사 '학력' · '기술 스택').
 */
const SECTIONS = [
  {
    key: 'education',
    title: '학력',
    fieldNames: ['학력'],
    columns: ['학교명', '전공', '졸업여부'],
    empty: '등록된 학력 정보가 없습니다.',
    requestTitle: '학력 변경 신청',
    fields: [
      { name: 'school', label: '학교명', placeholder: '학교명 입력' },
      { name: 'major', label: '전공', placeholder: '전공 입력' },
      { name: 'graduated', label: '졸업여부', type: 'select', placeholder: '졸업 여부 선택', options: ['졸업', '졸업예정', '재학', '휴학', '중퇴', '수료'] },
    ],
    evidenceTitle: '학력 증빙 파일을 첨부해주세요',
    evidenceHint: '졸업증명서, 성적증명서, 재학증명서 등 PDF 또는 JPG 파일',
    buttons: ['학력 등록 승인 요청', '학력 변경 승인 요청'],
    buttonWidth: 132,
  },
  {
    key: 'career',
    title: '이전 직장 경력',
    fieldNames: ['이전 직장 경력', '경력'],
    columns: ['회사명', '근무기간', '담당업무'],
    empty: '등록된 이전 직장 경력 정보가 없습니다.',
    requestTitle: '이전 직장 경력 변경 신청',
    fields: [
      { name: 'company', label: '회사명', placeholder: '회사명 입력' },
      { name: 'period', label: '근무기간', type: 'period' },
      { name: 'duty', label: '담당업무', placeholder: '담당업무 입력' },
    ],
    evidenceTitle: '경력 증빙 파일을 첨부해주세요',
    evidenceHint: '재직증명서, 경력증명서, 퇴사확인서 등 PDF 또는 JPG 파일',
    buttons: ['이전 직장 경력 등록 승인 요청', '이전 직장 경력 변경 승인 요청'],
    buttonWidth: 210,
  },
  {
    key: 'license',
    title: '자격증',
    fieldNames: ['자격증'],
    columns: ['자격증명', '발급기관', '취득일'],
    empty: '등록된 자격증 정보가 없습니다.',
    requestTitle: '자격증 변경 신청',
    fields: [
      { name: 'licenseName', label: '자격증명', placeholder: '자격증명 입력' },
      { name: 'issuer', label: '발급기관', placeholder: '발급기관 입력' },
      { name: 'acquiredOn', label: '취득일', type: 'date' },
    ],
    evidenceTitle: '자격증 증빙 파일을 첨부해주세요',
    evidenceHint: '자격증 사본, 발급 확인서 등 PDF 또는 JPG 파일',
    buttons: ['자격증 등록 승인 요청', '자격증 변경 승인 요청'],
    buttonWidth: 145,
  },
];

function valuesOf(profile, names) {
  return (profile.fieldValues ?? []).filter((v) => names.includes(v.name) && v.value);
}

function RequestForm({ section }) {
  const pending = usePendingFeature();
  const [values, setValues] = useState({});
  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);
  const set = (name, value) => setValues((v) => ({ ...v, [name]: value }));

  const submit = (title) => {
    const missing = section.fields.find((f) =>
      f.type === 'period' ? !values.periodFrom || !values.periodTo : !String(values[f.name] ?? '').trim(),
    );
    if (missing) return setError(`${missing.label}을(를) 입력해 주세요`);
    if (section.fields.some((f) => f.type === 'period') && values.periodFrom > values.periodTo) {
      return setError('종료일이 시작일보다 빠릅니다');
    }
    if (!file) return setError('증빙 파일을 첨부해 주세요');
    setError(null);
    return pending(title);
  };

  return (
    <div className="mi-box is-request">
      <div className="mi-card-head">
        <h3 className="mi-subhead">{section.requestTitle}</h3>
        <span className="mi-text" style={{ fontSize: rem(12) }}>
          변경 내용과 증빙을 첨부한 뒤 승인 요청합니다.
        </span>
      </div>
      <div className="mi-section">
        {section.fields.map((f) => (
          <div key={f.name} className="mi-field is-small">
            <label htmlFor={`${section.key}-${f.name}`}>{f.label}</label>
            {f.type === 'select' && (
              <select
                id={`${section.key}-${f.name}`}
                className="mi-input is-40"
                value={values[f.name] ?? ''}
                onChange={(e) => set(f.name, e.target.value)}
              >
                <option value="">{f.placeholder}</option>
                {f.options.map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </select>
            )}
            {f.type === 'period' && (
              <div className="mi-split">
                <input
                  id={`${section.key}-${f.name}`}
                  className="mi-input is-40"
                  type="date"
                  aria-label="시작일 선택"
                  value={values.periodFrom ?? ''}
                  onChange={(e) => set('periodFrom', e.target.value)}
                />
                <input
                  className="mi-input is-40"
                  type="date"
                  aria-label="종료일 선택"
                  value={values.periodTo ?? ''}
                  onChange={(e) => set('periodTo', e.target.value)}
                />
              </div>
            )}
            {f.type === 'date' && (
              <input
                id={`${section.key}-${f.name}`}
                className="mi-input is-40"
                type="date"
                value={values[f.name] ?? ''}
                onChange={(e) => set(f.name, e.target.value)}
              />
            )}
            {!f.type && (
              <input
                id={`${section.key}-${f.name}`}
                className="mi-input is-40"
                placeholder={f.placeholder}
                maxLength={100}
                value={values[f.name] ?? ''}
                onChange={(e) => set(f.name, e.target.value)}
              />
            )}
          </div>
        ))}
        <div className="mi-field is-small">
          <label>증빙 업로드</label>
          <EvidencePicker value={file} onChange={setFile} variant="tall" title={section.evidenceTitle} hint={section.evidenceHint} />
        </div>
      </div>
      {error && <p className="mi-error">{error}</p>}
      <div className="mi-actions">
        <button type="button" className="mi-btn" style={{ minWidth: rem(section.buttonWidth) }} onClick={() => submit(section.buttons[0])}>
          {section.buttons[0]}
        </button>
        <button
          type="button"
          className="mi-btn is-secondary"
          style={{ minWidth: rem(section.buttonWidth) }}
          onClick={() => submit(section.buttons[1])}
        >
          {section.buttons[1]}
        </button>
      </div>
      <Note variant="plain">
        변경 내용과 증빙을 첨부한 뒤 승인 요청하면 기존 공식 정보는 유지되며, 인사팀/관리자 승인 후에 반영됩니다.
      </Note>
    </div>
  );
}

function ApprovalSection({ section, profile }) {
  const rows = valuesOf(profile, section.fieldNames);
  return (
    <section className="mi-card">
      <div className="mi-card-head">
        <h2 className="mi-card-title">{section.title}</h2>
        <div className="mi-badges">
          <Badge tone="green">증빙 승인</Badge>
          <Badge>{rows.length ? `등록 ${rows.length}건` : '미등록'}</Badge>
        </div>
      </div>
      <div>
        <div className="mi-list-head">
          {section.columns.map((c) => (
            <span key={c}>{c}</span>
          ))}
        </div>
        {rows.length === 0 ? (
          <p className="mi-list-empty">{section.empty}</p>
        ) : (
          // 추가 항목은 글자 한 칸이라 첫 칸에 그대로 보여 준다
          rows.map((r) => (
            <div key={`${r.fieldDefId}-${r.seq}`} className="mi-list-row">
              <span style={{ gridColumn: '1 / -1' }}>{r.value}</span>
            </div>
          ))
        )}
      </div>
      <RequestForm section={section} />
    </section>
  );
}

/** 학력 및 경력 탭 — Figma EMP_학력및경력 */
export default function CareerTab({ profile }) {
  const pending = usePendingFeature();
  const skills = valuesOf(profile, ['보유 기술', '기술 스택']);

  return (
    <div className="mi-stack">
      {SECTIONS.map((s) => (
        <ApprovalSection key={s.key} section={s} profile={profile} />
      ))}

      <section className="mi-card">
        <h2 className="mi-card-title">어학 점수</h2>
        <div>
          <div className="mi-list-head">
            <span>시험명</span>
            <span>점수</span>
            <span>취득일</span>
          </div>
          <p className="mi-list-empty">등록된 어학 점수 정보가 없습니다.</p>
        </div>
        <div className="mi-actions is-divided">
          <button type="button" className="mi-btn" style={{ width: rem(171) }} onClick={() => pending('어학 점수 등록 신청')}>
            어학 점수 등록 신청
          </button>
          <button type="button" className="mi-btn is-secondary" style={{ width: rem(171) }} onClick={() => pending('어학 점수 변경 신청')}>
            어학 점수 변경 신청
          </button>
        </div>
      </section>

      <section className="mi-card">
        <h2 className="mi-card-title">보유 기술</h2>
        {skills.length === 0 ? (
          <div className="mi-box is-borderless" style={{ padding: rem(24) }}>
            <p className="mi-list-empty" style={{ padding: 0 }}>
              등록된 보유 기술 정보가 없습니다.
            </p>
          </div>
        ) : (
          <div className="mi-chips">
            {skills.map((s) => (
              <Badge key={`${s.fieldDefId}-${s.seq}`}>{s.value}</Badge>
            ))}
          </div>
        )}
        <div className="mi-actions is-divided">
          <button type="button" className="mi-btn" style={{ width: rem(171) }} onClick={() => pending('보유 기술 등록 신청')}>
            보유 기술 등록 신청
          </button>
          <button type="button" className="mi-btn is-secondary" style={{ width: rem(171) }} onClick={() => pending('보유 기술 변경 신청')}>
            보유 기술 변경 신청
          </button>
        </div>
      </section>

      <Note variant="page">
        <p>
          • <strong>직원 본인 수정 항목</strong>: 휴대폰 번호, 비상연락처, 거주지 주소, 사내 프로필 사진, 비밀번호 등은 직원이 수정하면 즉시 반영됩니다.
        </p>
        <p>
          • <strong>증빙 제출 항목</strong>: 학력, 이전 직장 경력, 자격증 등은 변경 내용과 증빙 서류를 업로드한 뒤 인사팀/관리자 승인 후 반영됩니다.
        </p>
        <p>
          • <strong>인사팀 전결 항목</strong>: 사번, 부서, 직급/직책, 입사일, 계약 형태 등은 인사발령이나 인사이동을 통해서만 변경됩니다.
        </p>
      </Note>
    </div>
  );
}
