// 소득세 탭(Figma 71:5717) — 서버는 간이세액표가 아니라 누진세율 구간(이상 · 미만 · 세율 · 누진공제)으로 계산한다.
// 그래서 구간 표를 보여 주고 고치게 한다(전체 교체 PUT). 간이세액표 엑셀 업로드는 준비 중
import { useState } from 'react';
import { InputNumber, Modal, Spin } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getPayVariables, getTaxBrackets, payVariablesKey, replaceTaxBrackets, taxBracketsKey } from '../../../api/paySettings';
import { useNotify } from '../../../components/common/useNotify';
import { errorMessage } from '../../../constants/errors';
import { ModalFoot } from '../../hr/parts';
import { pct, varText, won } from './shared';

export default function TaxTab() {
  const brackets = useQuery({ queryKey: taxBracketsKey, queryFn: getTaxBrackets });
  const vars = useQuery({ queryKey: payVariablesKey, queryFn: getPayVariables });
  const [editing, setEditing] = useState(false);

  if (brackets.isLoading) return <Spin />;
  if (brackets.error) return <p className="hr-notice is-error">{errorMessage(brackets.error)}</p>;
  const rows = brackets.data?.brackets ?? [];
  const v = (code) => vars.data?.find((x) => x.varCode === code)?.current?.value;

  return (
    <>
      <div className="pay-bar is-plain">
        <div className="pay-basis">
          <span>계산 방식</span>
          <strong>누진세율 구간</strong>
          <em className="pay-chip-on">적용 중</em>
        </div>
        <button type="button" className="hr-btn" onClick={() => setEditing(true)}>
          구간 수정
        </button>
      </div>

      <div className="pay-stats">
        <div>
          <span>계산 방식</span>
          <strong>누진세율</strong>
          <em>과세표준 구간별 세율</em>
        </div>
        <div>
          <span>등록 구간 수</span>
          <strong>{`${rows.length}개 구간`}</strong>
          <em>0원부터 빈틈 없이</em>
        </div>
        <div>
          <span>지방소득세</span>
          <strong>{`소득세의 ${v('LOCAL_TAX_RATE') ?? '–'}%`}</strong>
          <em>계산 변수에서 변경</em>
        </div>
        <div>
          <span>부양가족 공제</span>
          <strong>{varText('DEPENDENT_DEDUCTION', v('DEPENDENT_DEDUCTION'))}</strong>
          <em>1명당 · 계산 변수에서 변경</em>
        </div>
      </div>

      <div className="pay-split">
        <section className="hr-table pay-card">
          <div className="pay-card-head">
            <strong>소득세 구간</strong>
            <span>과세표준이 [이상, 미만) 구간에 들면 그 세율과 누진공제를 씁니다</span>
          </div>
          <div className="hr-tr is-head">
            <span className="pay-c-num">과세표준 이상</span>
            <span className="pay-c-num">미만</span>
            <span className="pay-c-num">세율</span>
            <span className="pay-c-num">누진공제</span>
          </div>
          {rows.map((b) => (
            <div key={b.lowerBound} className="hr-tr pay-row">
              <span className="pay-c-num hr-strong">{won(b.lowerBound)}</span>
              <span className="pay-c-num">{b.upperBound == null ? '이상 전부' : won(b.upperBound)}</span>
              <span className="pay-c-num">{pct(b.rate)}</span>
              <span className="pay-c-num">{won(b.progressiveDeduction)}</span>
            </div>
          ))}
          <div className="pay-card-foot">
            <span>금액 단위: 원 · 월 과세표준 기준</span>
          </div>
        </section>

        <section className="hr-card pay-rule">
          <p className="hr-card-title">계산 기준</p>
          <dl>
            <dt>과세표준</dt>
            <dd>과세 급여 − 부양가족 수 × 1인당 공제 − 다자녀 추가 공제(자녀 2명부터)</dd>
            <dt>부양가족 수</dt>
            <dd>직원 가족 정보에서 자동 계산</dd>
            <dt>소득세</dt>
            <dd>과세표준 × 구간 세율 − 누진공제(원 미만 버림)</dd>
            <dt>지방소득세</dt>
            <dd>{`소득세 × ${v('LOCAL_TAX_RATE') ?? '지방소득세율'}%`}</dd>
          </dl>
          <p className="pay-hint">국세청 간이세액표(엑셀) 업로드는 준비 중입니다.</p>
        </section>
      </div>

      {editing && <BracketModal rows={rows} onClose={() => setEditing(false)} />}
    </>
  );
}

function BracketModal({ rows, onClose }) {
  const notify = useNotify();
  const queryClient = useQueryClient();
  // 미만 값은 다음 구간 이상 값으로 자동 — 이상 값 · 세율 · 누진공제만 입력
  const [draft, setDraft] = useState(() => rows.map((b) => ({ lowerBound: b.lowerBound, rate: b.rate, progressiveDeduction: b.progressiveDeduction })));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const set = (i, key, v) => setDraft((d) => d.map((r, j) => (j === i ? { ...r, [key]: v } : r)));
  const add = () => setDraft((d) => [...d, { lowerBound: null, rate: null, progressiveDeduction: 0 }]);
  const del = (i) => setDraft((d) => d.filter((_, j) => j !== i));

  const save = async () => {
    const sorted = [...draft].sort((a, b) => (a.lowerBound ?? 0) - (b.lowerBound ?? 0));
    if (sorted.some((r) => r.lowerBound == null || r.rate == null || r.progressiveDeduction == null)) return setError('모든 칸을 입력하세요');
    if (sorted[0].lowerBound !== 0) return setError('첫 구간은 0원부터 시작해야 합니다');
    if (new Set(sorted.map((r) => r.lowerBound)).size !== sorted.length) return setError('같은 시작 금액의 구간이 두 개 있습니다');
    const brackets = sorted.map((r, i) => ({ ...r, upperBound: sorted[i + 1]?.lowerBound ?? null }));
    setBusy(true);
    setError(null);
    try {
      await replaceTaxBrackets({ brackets });
      queryClient.invalidateQueries({ queryKey: taxBracketsKey });
      notify.success('소득세 구간을 저장했습니다');
      onClose();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };
  const money = {
    formatter: (v) => (v || v === 0 ? `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''),
    parser: (v) => v.replace(/,/g, ''),
  };

  return (
    <Modal open title="소득세 구간 수정" footer={null} onCancel={onClose} width={720} className="hr-modal pay-modal" destroyOnHidden centered>
      <p className="hr-modal-sub">구간마다 시작 금액(이상) · 세율 · 누진공제를 입력하세요. 끝 금액(미만)은 다음 구간의 시작 금액으로 정해집니다.</p>
      <div className="hr-table pay-edit-table">
        <div className="hr-tr is-head">
          <span className="pay-c-in">과세표준 이상(원)</span>
          <span className="pay-c-in">세율(%)</span>
          <span className="pay-c-in">누진공제(원)</span>
          <span className="pay-c-del" />
        </div>
        {draft.map((r, i) => (
          <div key={i} className="hr-tr pay-row">
            <span className="pay-c-in">
              <InputNumber className="pay-input" min={0} step={100000} value={r.lowerBound} onChange={(v) => set(i, 'lowerBound', v)} {...money} />
            </span>
            <span className="pay-c-in">
              <InputNumber className="pay-input" min={0} max={100} step={0.5} value={r.rate} onChange={(v) => set(i, 'rate', v)} suffix="%" />
            </span>
            <span className="pay-c-in">
              <InputNumber className="pay-input" min={0} step={1000} value={r.progressiveDeduction} onChange={(v) => set(i, 'progressiveDeduction', v)} {...money} />
            </span>
            <span className="pay-c-del">
              <button type="button" className="hr-link is-red" disabled={draft.length <= 1} onClick={() => del(i)}>
                삭제
              </button>
            </span>
          </div>
        ))}
      </div>
      <button type="button" className="hr-link pay-add" onClick={add}>
        + 구간 추가
      </button>
      {error ? <p className="hr-notice is-error">{error}</p> : <p className="hr-notice">저장하면 다음 급여 정산부터 새 구간으로 계산합니다. 확정된 급여는 바뀌지 않습니다.</p>}
      <ModalFoot onCancel={onClose} busy={busy} okType="button" onOk={save} />
    </Modal>
  );
}
