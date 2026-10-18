// 급여명세서 창(Figma 460-12084) — 직원 화면과 같은 지급 · 공제 표 · 값 칸 · 계산 방법(payslipParts). [PDF 다운로드]는 인쇄 창
import { Modal, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getPayslip, payslipKey } from '../../../api/payrollAdmin';
import { errorMessage } from '../../../constants/errors';
import { CalcTable, PayTable, payslipTiles } from '../../me/payroll/payslipParts';
import { won, ym } from './shared';

/** 인쇄할 때는 이 창만 — body 에 표시를 붙였다가 뗀다(pr.css @media print) */
function printModal() {
  document.body.classList.add('pr-printing');
  window.print();
  setTimeout(() => document.body.classList.remove('pr-printing'), 0);
}

export default function PayslipModal({ id, name, onClose }) {
  const q = useQuery({ queryKey: payslipKey(id), queryFn: () => getPayslip(id), enabled: !!id });
  const p = q.data;
  return (
    <Modal
      open={!!id}
      onCancel={onClose}
      footer={null}
      closable={false}
      width={1120}
      className="hr-modal pr-slip-modal"
      destroyOnHidden
      centered
      title={
        <div className="pr-slip-head">
          <span>{`급여명세서 · ${p?.name ?? name ?? ''}`}</span>
          <div className="hr-actions pr-print-hide">
            <button type="button" className="hr-btn is-secondary" onClick={onClose}>
              닫기
            </button>
            <button type="button" className="hr-btn" disabled={!p} onClick={printModal}>
              PDF 다운로드
            </button>
          </div>
        </div>
      }
    >
      {q.isLoading && <Spin />}
      {q.error && <p className="hr-notice is-error">{errorMessage(q.error)}</p>}
      {p && (
        <section className="pr-slip">
          <div className="pr-slip-title">
            <strong>{`${ym(p.payMonth)} 급여 명세서`}</strong>
            <span>{[`${p.employeeNo} · ${p.orgUnitName}`, `지급일 ${p.payDate}`, `근무 ${p.workedDays}/${p.monthDays}일`, `통상시급 ${won(p.ordinaryHourlyWage)}`].join(' · ')}</span>
          </div>
          <div className="ps-scroll">
            <PayTable p={p} />
          </div>
          <div className="ps-tiles">
            {payslipTiles(p).map((t) => (
              <div key={t.key} className="ps-tile">
                <span title={t.label}>{t.label}</span>
                <strong className={t.blue ? 'is-blue' : undefined}>{t.value}</strong>
                {t.note && <em>{t.note}</em>}
              </div>
            ))}
          </div>
          <CalcTable p={p} />
          {p.companyBurdenTotal != null && <p className="pr-note">{`회사 부담(4대보험 회사분) ${won(p.companyBurdenTotal)} — 직원 급여에서 빠지지 않습니다.`}</p>}
        </section>
      )}
    </Modal>
  );
}
