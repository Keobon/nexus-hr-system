// 변경 이력 탭 — GET /company/change-history: 항목 · 전→후 · 효력일 · 사유 · 근거 서류 · 변경자(최신순)
import { Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { companyHistoryKey, getCompanyHistory } from '../../../../api/company';
import { errorMessage } from '../../../../constants/errors';
import { labelOf } from '../../../../constants/labels';
import { formatDateTime } from '../../../../utils/format';

export default function HistoryTab() {
  const list = useQuery({ queryKey: companyHistoryKey, queryFn: getCompanyHistory });
  const rows = list.data ?? [];
  const docName = (d) => (d.docType === 'OTHER' ? d.docName : labelOf('companyDocumentType', d.docType));

  return (
    <section className="ad-section">
      <div className="ad-section-head">
        <h2 className="is-20">변경 이력 <small>회사명 · 대표자명 · 사업자등록번호 · 주소를 바꾼 기록입니다. 이력은 고칠 수 없습니다.</small></h2>
      </div>
      <table className="ad-ctable">
        <thead>
          <tr>
            <th style={{ width: '8rem' }}>효력일</th>
            <th style={{ width: '9rem' }}>항목</th>
            <th>변경 전 → 변경 후</th>
            <th style={{ width: '14rem' }}>사유</th>
            <th style={{ width: '11rem' }}>근거 서류</th>
            <th style={{ width: '10rem' }}>변경자</th>
          </tr>
        </thead>
        <tbody>
          {list.isLoading ? (
            <tr className="is-empty"><td colSpan={6}><Spin /></td></tr>
          ) : list.error ? (
            <tr className="is-empty"><td colSpan={6}>{errorMessage(list.error)}</td></tr>
          ) : rows.length === 0 ? (
            <tr className="is-empty"><td colSpan={6}>변경 이력이 없습니다</td></tr>
          ) : (
            rows.map((r) => (
              <tr key={r.id}>
                <td>{r.effectiveDate}</td>
                <td className="hr-strong">{labelOf('companyField', r.field)}</td>
                <td>{r.oldValue || '-'} <span className="ad-arrow">→</span> {r.newValue || '-'}</td>
                <td>{r.reason}</td>
                <td>{r.document ? docName(r.document) : '-'}</td>
                <td>
                  <span className="hr-strong">{r.changedBy?.name ?? '-'}</span>
                  <div className="muted" style={{ fontSize: '0.6875rem' }}>{formatDateTime(r.createdAt)}</div>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </section>
  );
}
