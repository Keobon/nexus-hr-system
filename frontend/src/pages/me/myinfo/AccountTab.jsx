import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getMyBankAccount, getMyFamily, myBankAccountKey, myFamilyKey } from '../../../api/profile';
import { labelOf } from '../../../constants/labels';
import { formatDate } from '../../../utils/format';
import { EvidencePicker, InfoRow, Note, usePendingFeature } from './parts';
import { rem } from '../../../utils/size';

// 은행 목록은 화면 고정(역할 분담 2.3 H 58번)
const BANKS = ['KB국민은행', '신한은행', '우리은행', '하나은행', 'NH농협은행', 'IBK기업은행', 'SC제일은행', '카카오뱅크', '토스뱅크', '케이뱅크', '부산은행', '대구은행'];
const ACCOUNT_RE = /^[0-9-]+$/;

/** 급여 수령 계좌 — 증빙(통장 사본) 제출 후 승인(추가사항 ②). 승인 전까지 기존 계좌 유지 */
function BankCard({ account, holderDefault }) {
  const pending = usePendingFeature();
  const [bankName, setBankName] = useState('');
  const [accountNo, setAccountNo] = useState('');
  const [holder, setHolder] = useState(holderDefault);
  const [file, setFile] = useState(null);
  const [error, setError] = useState(null);

  const submit = () => {
    const digits = accountNo.replace(/-/g, '');
    if (!bankName) return setError('은행을 선택해 주세요');
    if (!ACCOUNT_RE.test(accountNo) || digits.length < 6 || digits.length > 20) {
      return setError('계좌번호는 숫자와 하이픈으로, 숫자 6–20자리로 입력해 주세요');
    }
    if (!holder.trim()) return setError('예금주를 입력해 주세요');
    if (!file) return setError('통장 사본을 첨부해 주세요');
    setError(null);
    return pending('계좌 변경 승인 요청');
  };

  return (
    <section className="mi-card" style={{ minHeight: rem(278) }}>
      <div className="mi-section" style={{ gap: rem(8) }}>
        <h2 className="mi-card-title">급여 수령 계좌</h2>
        <p className="mi-text">증빙 필수 · 인사팀/관리자 승인 후 반영</p>
      </div>

      <div className="mi-section">
        <h3 className="mi-subhead">기존 계좌</h3>
        <div className="mi-box">
          <div className="mi-rows is-tight">
            <InfoRow label="은행명">{account?.bankName}</InfoRow>
            <InfoRow label="계좌번호">{account?.accountNoMasked}</InfoRow>
            <InfoRow label="예금주">{account?.holder ?? holderDefault}</InfoRow>
          </div>
        </div>
      </div>

      <div className="mi-section">
        <h3 className="mi-subhead">변경 요청</h3>
        <div className="mi-box" style={{ gap: rem(16) }}>
          <div className="mi-rows is-tight">
            <label className="mi-stacked">
              <span>은행명</span>
              <select value={bankName} onChange={(e) => setBankName(e.target.value)}>
                <option value="">변경할 은행명 입력</option>
                {BANKS.map((b) => (
                  <option key={b} value={b}>
                    {b}
                  </option>
                ))}
              </select>
            </label>
            <label className="mi-stacked">
              <span>계좌번호</span>
              <input
                inputMode="numeric"
                placeholder="변경할 계좌번호 입력"
                maxLength={30}
                value={accountNo}
                onChange={(e) => setAccountNo(e.target.value)}
              />
            </label>
            <label className="mi-stacked">
              <span>예금주</span>
              <input maxLength={50} value={holder} onChange={(e) => setHolder(e.target.value)} />
            </label>
          </div>
          <div className="mi-section" style={{ gap: rem(8) }}>
            <h3 className="mi-subhead">통장 사본 첨부</h3>
            <EvidencePicker value={file} onChange={setFile} variant="center" title="통장 사본 PDF 또는 JPG를 업로드해주세요" />
          </div>
        </div>
      </div>

      <div className="mi-box">
        <h3>변경 신청 안내</h3>
        <p className="mi-text is-14">
          급여 수령 계좌는 법적·재무적 증빙이 필요한 항목으로, 변경 내용과 통장 사본 증빙을 함께 업로드해야 합니다. 인사팀/관리자 승인 후 기존 계좌가 변경됩니다.
        </p>
      </div>

      {error && <p className="mi-error">{error}</p>}
      <div className="mi-actions is-divided">
        <button type="button" className="mi-btn" style={{ width: rem(132) }} onClick={submit}>
          승인 요청
        </button>
        <button type="button" className="mi-btn is-secondary" style={{ width: rem(132) }} onClick={() => pending('요청 내역')}>
          요청 내역
        </button>
      </div>
    </section>
  );
}

/** 계좌 및 정산 탭 — Figma EMP_계좌및정산 */
export default function AccountTab({ profile }) {
  const pending = usePendingFeature();
  const bank = useQuery({ queryKey: myBankAccountKey, queryFn: getMyBankAccount });
  const family = useQuery({ queryKey: myFamilyKey, queryFn: getMyFamily });
  const members = family.data?.members ?? [];

  return (
    <div className="mi-stack">
      <div className="mi-row">
        <BankCard account={bank.data} holderDefault={profile.name} />

        <section className="mi-card" style={{ minHeight: rem(278) }}>
          <h2 className="mi-card-title">연말정산 기본 인적 정보</h2>
          <div className="mi-rows">
            <InfoRow label="성명">{profile.name}</InfoRow>
            <InfoRow label="생년월일">{profile.birthDate ? formatDate(profile.birthDate, false) : null}</InfoRow>
            {/* 주민등록번호는 받지 않는 항목이라(기능명세서 2.3) 저장된 값이 없다 */}
            <InfoRow label="주민등록번호">{null}</InfoRow>
            <InfoRow label="거주자 구분">확인 필요</InfoRow>
          </div>
          <div className="mi-box">
            <h3>변경 신청 안내</h3>
            <p className="mi-text is-14">기본 인적 정보의 변경은 기존 담당자 확인 절차에 따라 신청해 주세요.</p>
          </div>
          <div className="mi-actions is-divided">
            <button type="button" className="mi-btn" style={{ width: rem(132) }} onClick={() => pending('정보 변경 신청')}>
              정보 변경 신청
            </button>
          </div>
        </section>
      </div>

      <section className="mi-card">
        <h2 className="mi-card-title">부양가족</h2>
        <div>
          <div className="mi-list-head">
            <span>성명</span>
            <span>관계</span>
            <span>공제 여부</span>
          </div>
          {members.length === 0 ? (
            <p className="mi-list-empty">등록된 부양가족 정보가 없습니다.</p>
          ) : (
            members.map((m) => (
              <div key={m.id} className="mi-list-row">
                <span>{m.name}</span>
                <span>{labelOf('familyRelation', m.relation)}</span>
                <span>{m.isTaxDependent ? '공제' : '-'}</span>
              </div>
            ))
          )}
        </div>
        <div className="mi-actions is-divided">
          <button type="button" className="mi-btn" style={{ width: rem(158) }} onClick={() => pending('부양가족 등록 신청')}>
            부양가족 등록 신청
          </button>
          <button type="button" className="mi-btn is-secondary" style={{ width: rem(158) }} onClick={() => pending('부양가족 변경 신청')}>
            부양가족 변경 신청
          </button>
        </div>
      </section>

      <Note variant="page">
        본인 관리 항목은 직원이 수정하면 즉시 반영되고, 증빙 제출이 필요한 항목은 변경 내용과 증빙 서류를 업로드한 뒤 인사팀/관리자 승인 후 반영됩니다. 기존 계좌는 승인 전까지 유지되며, 승인 완료 후 변경 내용이 반영됩니다.
      </Note>
    </div>
  );
}

