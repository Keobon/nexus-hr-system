import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { myProfileKey, updateMyProfile } from '../../../api/profile';
import { meKey } from '../../../api/auth';
import { useNotify } from '../../../components/common/useNotify';
import { InfoRow, Note } from './parts';

const PHONE_RE = /^[0-9+\-\s()]{0,20}$/;

/** "윤서연(배우자) 010-1234-5678" — 비상연락처 세 값을 한 줄로 */
function emergencyText(p) {
  if (!p.emergencyName && !p.emergencyPhone) return null;
  const who = p.emergencyName ? `${p.emergencyName}${p.emergencyRelation ? `(${p.emergencyRelation})` : ''}` : '';
  return [who, p.emergencyPhone].filter(Boolean).join(' ');
}

function useProfileSave(successText) {
  const queryClient = useQueryClient();
  const notify = useNotify();
  return useMutation({
    mutationFn: updateMyProfile,
    onSuccess: (data) => {
      queryClient.setQueryData(myProfileKey, data);
      queryClient.invalidateQueries({ queryKey: meKey });
      notify.success(successText);
    },
    onError: (err) => notify.error(err),
  });
}

/** 연락처 및 주소 탭 — Figma EMP_연락처및주소. 휴대폰 · 비상연락처 · 주소는 본인이 저장하면 즉시 반영(추가사항 ①) */
export default function ContactTab({ profile }) {
  const [phone, setPhone] = useState(profile.phone ?? '');
  const [emergencyPhone, setEmergencyPhone] = useState(profile.emergencyPhone ?? '');
  const [address, setAddress] = useState(profile.address ?? '');
  const [contactError, setContactError] = useState(null);
  const contactSave = useProfileSave('연락처를 저장했습니다');
  const addressSave = useProfileSave('주소를 저장했습니다');

  const saveContact = () => {
    if (!PHONE_RE.test(phone) || !PHONE_RE.test(emergencyPhone)) {
      setContactError('숫자와 하이픈(-)으로 20자 이내로 입력해 주세요');
      return;
    }
    setContactError(null);
    // PATCH 는 보낸 필드만 바꾸고 null 이면 비운다(API 1.1)
    contactSave.mutate({ phone: phone.trim() || null, emergencyPhone: emergencyPhone.trim() || null });
  };

  return (
    <div className="mi-stack">
      <div className="mi-row">
        <section className="mi-card is-tall">
          <h2 className="mi-card-title">연락처</h2>
          <div className="mi-rows">
            <InfoRow label="휴대폰 번호">{profile.phone}</InfoRow>
            <InfoRow label="사내 이메일">{profile.email}</InfoRow>
            <InfoRow label="비상 연락처">{emergencyText(profile)}</InfoRow>
          </div>
          <p className="mi-text">휴대폰 번호와 비상 연락처는 별도 승인 없이 저장 즉시 반영됩니다.</p>
          <div className="mi-edit">
            <div className="mi-field">
              <label htmlFor="mi-phone">휴대폰 번호</label>
              <input
                id="mi-phone"
                className="mi-input"
                type="tel"
                placeholder="미등록"
                maxLength={20}
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
              />
            </div>
            <div className="mi-field">
              <label htmlFor="mi-emergency">비상 연락처</label>
              <input
                id="mi-emergency"
                className="mi-input"
                type="tel"
                placeholder="미등록"
                maxLength={20}
                value={emergencyPhone}
                onChange={(e) => setEmergencyPhone(e.target.value)}
              />
            </div>
            {contactError && <p className="mi-error">{contactError}</p>}
            <Note>휴대폰 번호와 비상 연락처는 본인이 직접 수정할 수 있으며, 저장 후 즉시 반영됩니다.</Note>
            <button type="button" className="mi-btn is-block" disabled={contactSave.isPending} onClick={saveContact}>
              저장
            </button>
          </div>
        </section>

        <section className="mi-card is-tall">
          <h2 className="mi-card-title">거주지 주소</h2>
          <InfoRow label="거주지 주소">{profile.address}</InfoRow>
          {!profile.address && (
            <div className="mi-box is-borderless" style={{ minHeight: 70, justifyContent: 'center', gap: 0 }}>
              <p className="mi-text">등록된 거주지 주소가 없습니다.</p>
              <p className="mi-text">아래에 주소를 입력하고 저장해 주세요.</p>
            </div>
          )}
          <div className="mi-edit">
            <div className="mi-field">
              <label htmlFor="mi-address">거주지 주소</label>
              <input
                id="mi-address"
                className="mi-input"
                placeholder="미등록"
                maxLength={255}
                value={address}
                onChange={(e) => setAddress(e.target.value)}
              />
            </div>
            <Note>거주지 주소는 본인이 직접 수정할 수 있으며, 저장 후 즉시 반영됩니다.</Note>
            <button
              type="button"
              className="mi-btn is-block"
              disabled={addressSave.isPending}
              onClick={() => addressSave.mutate({ address: address.trim() || null })}
            >
              저장
            </button>
          </div>
        </section>
      </div>

      <Note variant="gray">
        휴대폰 번호·비상 연락처·거주지 주소는 본인이 직접 수정할 수 있으며 저장 즉시 반영됩니다. 사내 이메일은 조회 전용입니다.
      </Note>
    </div>
  );
}
