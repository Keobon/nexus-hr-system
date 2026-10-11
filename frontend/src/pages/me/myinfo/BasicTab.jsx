import { forwardRef, useState } from 'react';
import { labelOf } from '../../../constants/labels';
import { formatDate } from '../../../utils/format';
import { Badge, EMPTY, EvidencePicker, InfoRow, usePendingFeature } from './parts';

const CERTIFICATES = ['재직증명서', '경력증명서', '원천징수영수증'];

/** 개명 — 증빙 제출 후 승인(추가사항 ②). 승인 전까지 기존 이름 유지 */
const RenameCard = forwardRef(function RenameCard({ currentName }, ref) {
  const pending = usePendingFeature();
  const [name, setName] = useState('');
  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);

  const submit = () => {
    if (!name.trim()) return setError('변경할 이름을 입력해 주세요');
    if (name.trim() === currentName) return setError('현재 이름과 같습니다');
    if (!file) return setError('개명 증빙 서류를 첨부해 주세요');
    setError(null);
    return pending('개명 승인 요청');
  };

  return (
    <section ref={ref} className="mi-card" style={{ gap: 16 }}>
      <h2 className="mi-card-title is-regular">이름 변경 · 증빙 제출 후 승인</h2>
      <p className="mi-text is-14 is-dark" style={{ whiteSpace: 'pre-wrap' }}>{`현재 이름  ${currentName}`}</p>
      <label className="mi-text is-14" htmlFor="rename-input">
        변경할 이름
      </label>
      <input
        id="rename-input"
        className="mi-input"
        style={{ height: 47 }}
        placeholder="변경할 이름을 입력해 주세요"
        maxLength={50}
        value={name}
        onChange={(e) => setName(e.target.value)}
      />
      <p className="mi-text is-14">개명 증빙 서류 · 필수</p>
      <EvidencePicker value={file} onChange={setFile} />
      <p className="mi-text">개명 사실을 확인할 수 있는 증빙 서류를 첨부해 주세요.</p>
      {error && <p className="mi-error">{error}</p>}
      <button type="button" className="mi-btn is-block-regular" onClick={submit}>
        인사팀 / 관리자 승인 요청
      </button>
      <p className="mi-text">승인 전까지 기존 이름이 유지됩니다. 승인 후 변경 이름이 반영됩니다.</p>
    </section>
  );
});

/** 기본정보 탭 — Figma EMP_기본정보. 사번 · 직무 정보는 조회 전용(추가사항 ③) */
const BasicTab = forwardRef(function BasicTab({ profile }, renameRef) {
  const pending = usePendingFeature();

  return (
    <div className="mi-row">
      <div className="mi-stack">
        <section className="mi-card">
          <div className="mi-card-head">
            <h2 className="mi-card-title">기본 인적사항</h2>
          </div>
          <InfoRow label="성명">{profile.name}</InfoRow>
          <div className="mi-info-row">
            <span>사번</span>
            <div className="mi-inline">
              <span>{profile.employeeNo ?? EMPTY}</span>
              <Badge>조회 전용</Badge>
            </div>
          </div>
          <InfoRow label="생년월일">{profile.birthDate ? formatDate(profile.birthDate, false) : null}</InfoRow>
          <InfoRow label="성별">{profile.gender ? labelOf('gender', profile.gender) : null}</InfoRow>
        </section>

        <section className="mi-card">
          <div className="mi-card-head">
            <h2 className="mi-card-title">핵심 직무 정보</h2>
            <Badge>인사팀 전결</Badge>
          </div>
          <InfoRow label="부서">{profile.orgUnitName}</InfoRow>
          <InfoRow label="직급">{profile.jobGradeName}</InfoRow>
          <InfoRow label="직책">{profile.jobTitleName}</InfoRow>
          <InfoRow label="입사일">{profile.hireDate ? formatDate(profile.hireDate, false) : null}</InfoRow>
          <InfoRow label="계약 형태">{profile.employmentTypeName}</InfoRow>
          <p className="mi-text">조회 전용 · 인사발령/인사이동을 통해서만 변경됩니다.</p>
        </section>

        <RenameCard ref={renameRef} currentName={profile.name} />
      </div>

      <div className="mi-side">
        <section className="mi-card">
          <div className="mi-card-head">
            <h2 className="mi-card-title">증명서 발급 / 신청</h2>
          </div>
          <div>
            {CERTIFICATES.map((name) => (
              <div key={name} className="mi-cert">
                <span>{name}</span>
                <span className="mi-cert-links">
                  <button type="button" className="mi-link" onClick={() => pending(`${name} 신청`, '증명서 발급')}>
                    신청하기
                  </button>
                  <button type="button" className="mi-link" onClick={() => pending(`${name} 출력`, '증명서 발급')}>
                    출력하기
                  </button>
                </span>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
});

export default BasicTab;
