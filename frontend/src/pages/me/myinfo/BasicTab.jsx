import { forwardRef } from 'react';
import { labelOf } from '../../../constants/labels';
import { formatDate } from '../../../utils/format';
import { Badge, EMPTY, InfoRow, usePendingFeature } from './parts';
import { rem } from '../../../utils/size';

const CERTIFICATES = ['재직증명서', '경력증명서', '원천징수영수증'];

/** 개명 — 인사 담당이 직원 수정으로 처리한다(노션 수정사항 10/12). 화면은 문의 안내만 */
const RenameCard = forwardRef(function RenameCard({ currentName }, ref) {
  return (
    <section ref={ref} className="mi-card" style={{ gap: rem(16) }}>
      <h2 className="mi-card-title is-regular">이름 변경 · 인사팀 문의</h2>
      <p className="mi-text is-14 is-dark" style={{ whiteSpace: 'pre-wrap' }}>{`현재 이름  ${currentName}`}</p>
      <div className="mi-box">
        <p className="mi-text is-14">
          개명(이름 변경)은 인사 담당이 직원 정보를 고쳐 반영합니다. 개명 사실을 확인할 수 있는 서류(기본증명서 등)를 준비해 인사팀에 문의해 주세요.
        </p>
      </div>
      <p className="mi-text">반영되기 전까지는 지금 이름이 그대로 쓰입니다.</p>
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
