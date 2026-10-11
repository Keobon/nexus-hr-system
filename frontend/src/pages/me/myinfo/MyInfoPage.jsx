import { useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Result, Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meKey } from '../../../api/auth';
import { FILE_PURPOSES, MAX_FILE_BYTES, uploadFile } from '../../../api/files';
import { getMyProfile, myProfileKey, updateMyProfile } from '../../../api/profile';
import { useFileUrl } from '../../../components/common/useFileUrl';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { labelOf } from '../../../constants/labels';
import iconUser from '../../../assets/figma/icon-user.svg';
import AccountTab from './AccountTab';
import BasicTab from './BasicTab';
import CareerTab from './CareerTab';
import ContactTab from './ContactTab';
import SecurityTab from './SecurityTab';
import { Badge } from './parts';
import '../myinfo.css';
import { rem } from '../../../utils/size';

const TABS = [
  { key: 'basic', label: '기본정보' },
  { key: 'contact', label: '연락처 및 주소' },
  { key: 'account', label: '계좌 및 정산' },
  { key: 'career', label: '학력 및 경력' },
  { key: 'security', label: '계정 및 보안' },
];

// 기본정보 탭만 문장으로, 나머지 탭은 세 가지 수정 방식 요약(Figma 그대로)
const SUBTITLE = {
  basic: '본인 관리 항목은 즉시 반영되며, 증빙 제출 항목은 승인 후 반영되고, 인사팀 전결 항목은 조회 전용입니다.',
  other: '본인 관리: 즉시 반영  ·  증빙 제출: 승인 후 반영  ·  인사팀 전결: 조회 전용',
};

/** 프로필 사진 변경 — 본인 관리 항목이라 바로 반영(추가사항 ①): POST /files(PROFILE) → PATCH /me/profile */
function useProfilePhoto() {
  const queryClient = useQueryClient();
  const notify = useNotify();
  return useMutation({
    mutationFn: async (file) => {
      const uploaded = await uploadFile(file, 'PROFILE');
      return updateMyProfile({ profileFileId: uploaded.id });
    },
    onSuccess: (data) => {
      queryClient.setQueryData(myProfileKey, data);
      queryClient.invalidateQueries({ queryKey: meKey });
      notify.success('프로필 사진을 바꿨습니다');
    },
    onError: (err) => notify.error(err),
  });
}

function Summary({ profile, isBasic, onRename }) {
  const photoInput = useRef(null);
  const photoUrl = useFileUrl(profile.profileFileId);
  const photo = useProfilePhoto();
  const notify = useNotify();

  const pickPhoto = (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!FILE_PURPOSES.PROFILE.includes(file.type)) return notify.error({ message: 'jpg, png만 올릴 수 있습니다' });
    if (file.size > MAX_FILE_BYTES) return notify.error({ code: 'FILE_TOO_LARGE' });
    return photo.mutate(file);
  };

  const sub = [profile.jobGradeName, profile.orgUnitName].filter(Boolean).join(' / ');

  return (
    <section className="mi-summary">
      <div className="mi-avatar">
        {photoUrl ? (
          <img className="photo" src={photoUrl} alt="프로필 사진" />
        ) : (
          <>
            <img src={iconUser} alt="" />
            <span>사진 미등록</span>
          </>
        )}
      </div>
      <div className="mi-summary-text" style={{ gap: rem(isBasic ? 12 : 10) }}>
        <div>
          <p className="mi-summary-name">{profile.name}</p>
          <p className="mi-summary-sub" style={{ marginTop: rem(isBasic ? 4 : 10) }}>
            {profile.jobTitleName && !isBasic ? `${sub} · ${profile.jobTitleName}` : sub}
          </p>
        </div>
        <div className="mi-status-row">
          <Badge>{labelOf('employmentStatus', profile.status)}</Badge>
          {isBasic && <span>본인 관리 항목은 즉시 반영됩니다.</span>}
        </div>
      </div>
      {isBasic && (
        <div className="mi-summary-actions">
          <input ref={photoInput} type="file" accept=".jpg,.jpeg,.png" hidden onChange={pickPhoto} />
          <button type="button" className="mi-btn" disabled={photo.isPending} onClick={() => photoInput.current?.click()}>
            {photo.isPending ? '올리는 중…' : '프로필 사진 변경'}
          </button>
          <button type="button" className="mi-btn is-gray" onClick={onRename}>
            개명 신청
          </button>
        </div>
      )}
    </section>
  );
}

/** 내 정보(F-EMP-05 · F-EMP-03 본인 범위) — Figma EMP_기본정보 외 4개 탭. 탭은 주소(?tab=)에 남긴다 */
export default function MyInfoPage() {
  const [params, setParams] = useSearchParams();
  const tab = TABS.some((t) => t.key === params.get('tab')) ? params.get('tab') : 'basic';
  const profile = useQuery({ queryKey: myProfileKey, queryFn: getMyProfile });
  const renameRef = useRef(null);

  if (profile.isLoading) {
    return (
      <div className="page-center" style={{ minHeight: rem(400) }}>
        <Spin size="large" />
      </div>
    );
  }
  if (profile.error) {
    return <Result status="warning" title="내 정보를 불러오지 못했습니다" subTitle={errorMessage(profile.error)} />;
  }

  const data = profile.data;
  const isBasic = tab === 'basic';

  return (
    <div className="mi">
      <header className="mi-head">
        <h1>내 정보(My Profile)</h1>
        <p>{isBasic ? SUBTITLE.basic : SUBTITLE.other}</p>
      </header>

      <Summary
        profile={data}
        isBasic={isBasic}
        onRename={() => renameRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
      />

      <nav className="mi-tabs" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            className={`mi-tab${tab === t.key ? ' is-active' : ''}`}
            onClick={() => setParams(t.key === 'basic' ? {} : { tab: t.key }, { replace: true })}
          >
            {t.label}
          </button>
        ))}
      </nav>

      {tab === 'basic' && <BasicTab ref={renameRef} profile={data} />}
      {tab === 'contact' && <ContactTab profile={data} />}
      {tab === 'account' && <AccountTab profile={data} />}
      {tab === 'career' && <CareerTab profile={data} />}
      {tab === 'security' && <SecurityTab />}
    </div>
  );
}
