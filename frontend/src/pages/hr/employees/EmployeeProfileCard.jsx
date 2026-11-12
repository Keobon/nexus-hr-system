// 직원 상세 맨 위 프로필 — 사진 · 이름 · 사번 · 소속 · 직급 · 직책 · 재직상태.
// 사진은 같은 회사 사람이면 누구나 볼 수 있다(서버 ProfileLogoFileAccessChecker). 인사 담당(EMPLOYEE_MANAGE)은 바꾸거나 지울 수 있다:
// POST /files(PROFILE) → PATCH /employees/{id} { profileFileId } — null 이면 사진 삭제. 본인은 내 정보에서 바꾼다
import { useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { employeeKey, updateEmployee } from '../../../api/employees';
import { FILE_PURPOSES, MAX_FILE_BYTES, uploadFile } from '../../../api/files';
import { useFileUrl } from '../../../components/common/useFileUrl';
import { useNotify } from '../../../components/common/useNotify';
import { EmpStatusBadge } from '../parts';

export default function EmployeeProfileCard({ employee: e, canEdit }) {
  const input = useRef(null);
  const notify = useNotify();
  const queryClient = useQueryClient();
  const photoUrl = useFileUrl(e.profileFileId);

  const save = useMutation({
    mutationFn: async (file) => {
      const profileFileId = file ? (await uploadFile(file, 'PROFILE')).id : null;
      return updateEmployee(e.id, { profileFileId });
    },
    onSuccess: (data, file) => {
      queryClient.setQueryData(employeeKey(e.id), data);
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['org-units'] });
      notify.success(file ? '프로필 사진을 바꿨습니다' : '프로필 사진을 지웠습니다');
    },
    onError: (err) => notify.error(err),
  });

  const pick = (ev) => {
    const file = ev.target.files?.[0];
    ev.target.value = '';
    if (!file) return;
    if (!FILE_PURPOSES.PROFILE.includes(file.type)) return notify.error({ message: 'jpg, png만 올릴 수 있습니다' });
    if (file.size > MAX_FILE_BYTES) return notify.error({ code: 'FILE_TOO_LARGE' });
    return save.mutate(file);
  };

  const sub = [e.orgUnitName, e.jobGradeName, e.jobTitleName].filter(Boolean).join(' · ');

  return (
    <div className="hr-card ep-card">
      <div className="ep-avatar">
        {photoUrl ? <img src={photoUrl} alt={`${e.name} 프로필 사진`} /> : <span aria-label="사진 미등록">{e.name?.slice(0, 1) ?? '?'}</span>}
      </div>
      <div className="ep-text">
        <div className="ep-name">
          <strong>{e.name}</strong>
          <EmpStatusBadge status={e.status} />
        </div>
        <span className="ep-no">{e.employeeNo}</span>
        {sub && <span className="ep-sub">{sub}</span>}
        {!e.profileFileId && <span className="ep-hint">등록된 프로필 사진이 없습니다.</span>}
      </div>
      {canEdit && (
        <div className="ep-actions">
          <input ref={input} type="file" accept={FILE_PURPOSES.PROFILE.join(',')} hidden onChange={pick} />
          <button type="button" className="hr-btn is-secondary" disabled={save.isPending} onClick={() => input.current?.click()}>
            {e.profileFileId ? '사진 변경' : '사진 등록'}
          </button>
          {e.profileFileId && (
            <button type="button" className="hr-btn is-secondary" disabled={save.isPending} onClick={() => save.mutate(null)}>
              사진 삭제
            </button>
          )}
          <em>jpg · png, 10MB 이하</em>
        </div>
      )}
    </div>
  );
}
