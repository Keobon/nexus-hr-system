import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getMyBankAccount, getMyFamily, myBankAccountKey, myFamilyKey, updateMyBankAccount } from '../../../api/profile';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import { formatDate } from '../../../utils/format';
import { InfoRow, Note } from './parts';
import { rem } from '../../../utils/size';

// 은행 목록은 화면 고정(역할 분담 2.3 H 58번)
const BANKS = ['KB국민은행', '신한은행', '우리은행', '하나은행', 'NH농협은행', 'IBK기업은행', 'SC제일은행', '카카오뱅크', '토스뱅크', '케이뱅크', '부산은행', '대구은행'];
const ACCOUNT_RE = /^[0-9-]+$/;

/** 급여 수령 계좌 — 본인이 바로 수정(F-PAY-03, 노션 수정사항 10/12 — 승인 절차 없음) */
function BankCard({ account, holderDefault }) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  const [bankName, setBankName] = useState('');
  const [accountNo, setAccountNo] = useState('');
  const [holder, setHolder] = useState(holderDefault);
  const [error, setError] = useState(null);
  const save = useMutation({
    mutationFn: () => updateMyBankAccount({ bankName, accountNo: accountNo.trim(), holder: holder.trim() }),
    onSuccess: () => {
      notify.success('계좌를 저장했습니다');
      setBankName('');
      setAccountNo('');
      queryClient.invalidateQueries({ queryKey: myBankAccountKey });
    },
    onError: (err) => {
      // 필드 오류는 한 줄로 모아 보여 준다(이 카드는 AntD Form 이 아님)
      if (err?.code === 'VALIDATION_ERROR' && err.fields) return setError(Object.values(err.fields).join(' · '));
      return setError(errorMessage(err));
    },
  });

  const submit = () => {
    const digits = accountNo.replace(/-/g, '');
    if (!bankName) return setError('은행을 선택해 주세요');
    if (!ACCOUNT_RE.test(accountNo) || digits.length < 6 || digits.length > 20) {
      return setError('계좌번호는 숫자와 하이픈으로, 숫자 6–20자리로 입력해 주세요');
    }
    if (!holder.trim()) return setError('예금주를 입력해 주세요');
    setError(null);
    return save.mutate();
  };

  return (
    <section className="mi-card" style={{ minHeight: rem(278) }}>
      <div className="mi-section" style={{ gap: rem(8) }}>
        <h2 className="mi-card-title">급여 수령 계좌</h2>
        <p className="mi-text">본인 관리 · 저장하면 바로 반영</p>
      </div>

      <div className="mi-section">
        <h3 className="mi-subhead">현재 계좌</h3>
        <div className="mi-box">
          <div className="mi-rows is-tight">
            <InfoRow label="은행명">{account?.bankName}</InfoRow>
            <InfoRow label="계좌번호">{account?.accountNoMasked}</InfoRow>
            <InfoRow label="예금주">{account?.holder ?? holderDefault}</InfoRow>
          </div>
        </div>
      </div>

      <div className="mi-section">
        <h3 className="mi-subhead">{account ? '계좌 변경' : '계좌 등록'}</h3>
        <div className="mi-box" style={{ gap: rem(16) }}>
          <div className="mi-rows is-tight">
            <label className="mi-stacked">
              <span>은행명</span>
              <select value={bankName} onChange={(e) => setBankName(e.target.value)}>
                <option value="">은행 선택</option>
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
                placeholder="숫자와 하이픈(-)으로 입력"
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
        </div>
      </div>

      <div className="mi-box">
        <h3>계좌 변경 안내</h3>
        <p className="mi-text is-14">
          저장하면 다음 급여부터 새 계좌로 지급됩니다. 이미 확정된 달의 명세서 계좌는 바뀌지 않습니다. 계좌번호는 뒤 4자리만 보여 드립니다.
        </p>
      </div>

      {error && <p className="mi-error">{error}</p>}
      <div className="mi-actions is-divided">
        <button type="button" className="mi-btn" style={{ width: rem(132) }} disabled={save.isPending} onClick={submit}>
          {save.isPending ? '저장 중…' : '저장'}
        </button>
      </div>
    </section>
  );
}

/** 계좌 및 정산 탭 — Figma EMP_계좌및정산 */
export default function AccountTab({ profile }) {
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
            <h3>변경 안내</h3>
            <p className="mi-text is-14">성명 · 생년월일 같은 기본 인적 정보는 인사팀이 직원 정보를 고쳐 반영합니다. 바꿀 내용이 있으면 인사팀에 문의해 주세요.</p>
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
        <p className="mi-text" style={{ margin: 0 }}>
          부양가족 등록 · 변경은 인사팀에서 처리합니다(기능명세서 F-EMP-09). 바뀐 내용이 있으면 인사팀에 문의해 주세요.
        </p>
      </section>

      <Note variant="page">
        급여 계좌는 본인이 저장하면 바로 반영됩니다. 성명 · 생년월일 · 부양가족은 인사팀이 직원 정보를 고쳐 반영하니 인사팀에 문의해 주세요.
      </Note>
    </div>
  );
}

