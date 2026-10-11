import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { changePassword, meKey } from '../../../api/auth';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { InfoRow, Note, usePendingFeature } from './parts';
import { rem } from '../../../utils/size';

// 8자 이상, 영문 · 숫자 포함(F-AUTH-03) — 서버 PasswordRule 과 같은 조건
const validPassword = (v) => v.length >= 8 && /[A-Za-z]/.test(v) && /\d/.test(v);

/** 계정 및 보안 탭 — Figma EMP_계정및보안. 비밀번호는 본인이 바꾸면 즉시 반영(추가사항 ①) */
export default function SecurityTab() {
  const pending = usePendingFeature();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState(null);

  const mutation = useMutation({
    mutationFn: () => changePassword(current, next),
    onSuccess: () => {
      setCurrent('');
      setNext('');
      setConfirm('');
      queryClient.invalidateQueries({ queryKey: meKey });
      notify.success('비밀번호를 바꿨습니다');
    },
    onError: (err) => setError(err.fields?.newPassword ?? errorMessage(err)),
  });

  const submit = () => {
    if (!current) return setError('현재 비밀번호를 입력해 주세요');
    if (!validPassword(next)) return setError('새 비밀번호는 8자 이상, 영문과 숫자를 포함해야 합니다');
    if (next !== confirm) return setError('새 비밀번호와 재확인이 다릅니다');
    if (next === current) return setError('현재 비밀번호와 다른 비밀번호를 입력해 주세요');
    setError(null);
    return mutation.mutate();
  };

  return (
    <div className="mi-stack">
      <div className="mi-row">
        <section className="mi-card">
          <h2 className="mi-card-title">비밀번호</h2>
          <p className="mi-text is-14" style={{ lineHeight: 'normal' }}>
            본인 관리 · 별도 승인 없이 즉시 반영
          </p>
          <InfoRow label="보안 상태">설정 확인 필요</InfoRow>
          <div className="mi-field">
            <label htmlFor="pw-current">현재 비밀번호</label>
            <input
              id="pw-current"
              className="mi-input is-48"
              type="password"
              autoComplete="current-password"
              placeholder="••••••••••••"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
            />
          </div>
          <div className="mi-field">
            <label htmlFor="pw-next">새 비밀번호</label>
            <input
              id="pw-next"
              className="mi-input is-48"
              type="password"
              autoComplete="new-password"
              placeholder="새 비밀번호를 입력해 주세요"
              value={next}
              onChange={(e) => setNext(e.target.value)}
            />
          </div>
          <div className="mi-field">
            <label htmlFor="pw-confirm">비밀번호 재확인</label>
            <input
              id="pw-confirm"
              className="mi-input is-48"
              type="password"
              autoComplete="new-password"
              placeholder="새 비밀번호를 다시 입력해 주세요"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </div>
          {error && <p className="mi-error">{error}</p>}
          <p className="mi-text">비밀번호는 보안을 위해 표시되지 않으며, 변경 후 즉시 반영됩니다.</p>
          <div className="mi-actions is-divided">
            <button type="button" className="mi-btn is-green" style={{ width: rem(158) }} disabled={mutation.isPending} onClick={submit}>
              비밀번호 변경
            </button>
          </div>
        </section>

        <section className="mi-card">
          <h2 className="mi-card-title">2차 인증</h2>
          <div className="mi-rows">
            <InfoRow label="설정 상태">설정 확인 필요</InfoRow>
            <InfoRow label="인증 수단">확인 필요</InfoRow>
          </div>
          <p className="mi-text">2차 인증 등록·변경은 기존 담당자 확인 절차에 따라 처리됩니다.</p>
          <div className="mi-actions is-divided">
            <button type="button" className="mi-btn is-secondary" style={{ width: rem(171) }} onClick={() => pending('2차 인증 등록 신청', '2차 인증')}>
              2차 인증 등록 신청
            </button>
            <button type="button" className="mi-btn is-secondary" style={{ width: rem(171) }} onClick={() => pending('2차 인증 변경 신청', '2차 인증')}>
              2차 인증 변경 신청
            </button>
          </div>
        </section>
      </div>

      <section className="mi-card">
        <h2 className="mi-card-title">로그인 보안 설정</h2>
        <InfoRow label="설정 상태">설정 확인 필요</InfoRow>
        <p className="mi-text is-14">로그인 보안 설정은 기존 담당자 확인 절차에 따라 변경을 신청해 주세요.</p>
        <div className="mi-actions is-divided">
          <button
            type="button"
            className="mi-btn is-secondary"
            style={{ width: rem(223) }}
            onClick={() => pending('로그인 보안 설정 변경 신청', '로그인 보안 설정')}
          >
            로그인 보안 설정 변경 신청
          </button>
        </div>
      </section>

      <Note variant="page">
        비밀번호는 별도 승인 없이 변경 즉시 반영됩니다. 2차 인증과 로그인 보안 설정은 기존 담당자 확인 절차를 유지합니다.
      </Note>
    </div>
  );
}
