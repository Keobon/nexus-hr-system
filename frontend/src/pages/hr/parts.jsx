// 인사 관리 화면들이 같이 쓰는 작은 조각(Figma HR_* 의 PageHeader · StatusBadge · TabBar · MODAL_confirm)
import { useState } from 'react';
import { Modal } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { flattenOrgTree, getOrgTree, orgTreeKey } from '../../api/org';
import { labelOf } from '../../constants/labels';
import { errorMessage } from '../../constants/errors';
import './hr.css';

export function PageHead({ title, desc, children }) {
  return (
    <div className="hr-head">
      <div>
        <h1>{title}</h1>
        {desc ? <p>{desc}</p> : null}
      </div>
      {children ? <div className="hr-actions">{children}</div> : null}
    </div>
  );
}

const STATUS_TONE = { ACTIVE: 'is-ok', ON_LEAVE: 'is-warn', RESIGNED: 'is-gray' };

/** 재직상태 알약 — 라벨은 labels.js(employmentStatus), 색은 Figma StatusBadge */
export function EmpStatusBadge({ status }) {
  if (!status) return null;
  return <span className={`hr-badge ${STATUS_TONE[status] ?? 'is-gray'}`}>{labelOf('employmentStatus', status)}</span>;
}

export function ActiveBadge({ active }) {
  return <span className={`hr-badge ${active ? 'is-ok' : 'is-gray'}`}>{active ? '활성' : '비활성'}</span>;
}

export function TabBar({ tabs, value, onChange }) {
  return (
    <div className="hr-tabbar" role="tablist">
      {tabs.map((t) => (
        <button key={t.value} type="button" role="tab" aria-selected={value === t.value}
          className={value === t.value ? 'is-on' : ''} onClick={() => onChange(t.value)}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

/** MODAL_confirm — "확정하시겠습니까?" onConfirm 이 끝날 때까지 버튼을 잠그고, 실패하면 창 안에 메시지 */
export function ConfirmModal({ open, title = '확정하시겠습니까?', desc = '이 작업은 되돌릴 수 없습니다. 계속하시겠습니까?', okText = '확인', danger, onConfirm, onClose }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const close = () => { if (!busy) { setError(null); onClose(); } };
  const run = async () => {
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
      setBusy(false);
      onClose();
    } catch (err) {
      setBusy(false);
      setError(errorMessage(err));
    }
  };
  return (
    <Modal open={open} title={title} footer={null} onCancel={close} width={440} className="hr-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">{desc}</p>
      {error ? <p className="hr-notice is-error">{error}</p> : <div style={{ height: '1.5rem' }} />}
      <div className="hr-modal-foot">
        <button type="button" className="hr-btn is-secondary" onClick={close} disabled={busy}>취소</button>
        <button type="button" className={`hr-btn${danger ? ' is-danger' : ''}`} onClick={run} disabled={busy}>{busy ? '처리 중…' : okText}</button>
      </div>
    </Modal>
  );
}

/** 모달 아래 취소 · 저장 줄 */
export function ModalFoot({ onCancel, okText = '저장', busy, danger, okType = 'submit', onOk }) {
  return (
    <div className="hr-modal-foot">
      <button type="button" className="hr-btn is-secondary" onClick={onCancel} disabled={busy}>취소</button>
      <button type={okType} className={`hr-btn${danger ? ' is-danger' : ''}`} onClick={onOk} disabled={busy}>{busy ? '저장 중…' : okText}</button>
    </div>
  );
}

/**
 * 조직 선택지 — 활성 조직만, "개발본부 › 백엔드팀"처럼 경로를 붙인다(최상위 회사는 빼고).
 * 조직도 API 는 로그인만 하면 볼 수 있다.
 */
export function useOrgOptions() {
  const { data: tree } = useQuery({ queryKey: orgTreeKey, queryFn: getOrgTree });
  const flat = flattenOrgTree(tree);
  return {
    flat,
    options: flat.filter((o) => o.depth > 0 && o.isActive !== false).map((o) => ({ value: o.id, label: o.path.join(' › ') })),
    byId: (id) => flat.find((o) => o.id === id),
  };
}
