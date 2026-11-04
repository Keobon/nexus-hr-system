// 승진 대상자 탭 — GET /promotions/candidates(전사 범위만). 지금 직급에 N년 이상인 재직 직원과 최근 3년 정기 평가 평균 · 최근 승진 고과 점수를 보여 준다
// 점수로 거르지 않는다 — 결정은 사람이 하고, 확정은 승진 발령(발령 등록 화면을 미리 채워 연다)
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { InputNumber, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getPromotionCandidates, promotionCandidatesKey } from '../../../api/assignments';
import { fetchAllPages } from '../../../api/employees';
import { jobGradesApi } from '../../../api/orgSettings';
import { useMe } from '../../../auth/useMe';
import { errorMessage } from '../../../constants/errors';
import { PageHead } from '../parts';

const score = (n) => (n == null ? '–' : `${Number(n).toFixed(1)}점`);

export default function PromotionTab({ tabs }) {
  const navigate = useNavigate();
  const { can } = useMe();
  const manage = can('ASSIGNMENT_MANAGE');
  const [minYears, setMinYears] = useState(3);
  const list = useQuery({
    queryKey: promotionCandidatesKey(minYears),
    queryFn: () => fetchAllPages(getPromotionCandidates, { minYears }),
    enabled: minYears >= 1,
  });
  const grades = useQuery({ queryKey: jobGradesApi.key(true), queryFn: () => jobGradesApi.list(true) });
  // 다음 직급 — 직급 순서(sortOrder)가 지금보다 큰 첫 직급
  const nextGrade = (gradeId) => {
    const sorted = [...(grades.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
    const cur = sorted.find((g) => g.id === gradeId);
    return cur ? sorted.find((g) => g.sortOrder > cur.sortOrder) : undefined;
  };
  const rows = list.data ?? [];

  return (
    <div className="hr">
      <PageHead title="전사 인사발령 관리" desc="승진 대상자의 체류 연수와 평가 점수를 확인하고 승진 발령을 등록합니다." />
      {tabs}

      <div className="hr-filter">
        <div className="hr-field">
          <label>지금 직급 체류</label>
          <InputNumber min={1} max={50} value={minYears} onChange={(v) => setMinYears(v ?? 1)} suffix="년 이상" />
        </div>
      </div>

      <div className="hr-table">
        <div className="hr-tr is-head" style={{ gap: '1rem' }}>
          <span style={{ width: '7.5rem' }}>사원번호</span>
          <span style={{ width: '5.625rem' }}>이름</span>
          <span style={{ width: '9rem' }}>소속</span>
          <span style={{ width: '7rem' }}>직급</span>
          <span style={{ width: '6.5rem' }}>직급 시작일</span>
          <span style={{ width: '4.5rem' }}>체류</span>
          <span style={{ width: '9rem' }}>정기 평가 평균(3년)</span>
          <span style={{ width: '6rem' }}>승진 고과</span>
          {manage && <span style={{ width: '6rem' }}>발령</span>}
        </div>
        {list.isLoading ? (
          <div className="hr-tr is-empty"><Spin /></div>
        ) : list.error ? (
          <div className="hr-tr is-empty">{errorMessage(list.error)}</div>
        ) : rows.length === 0 ? (
          <div className="hr-tr is-empty">조건에 맞는 승진 대상자가 없습니다</div>
        ) : (
          rows.map((c) => {
            const next = nextGrade(c.jobGradeId);
            return (
              <div key={c.employeeId} className="hr-tr" style={{ gap: '1rem', fontSize: '0.875rem' }}>
                <span style={{ width: '7.5rem' }}>{c.employeeNo}</span>
                <span className="hr-strong" style={{ width: '5.625rem' }}>{c.name}</span>
                <span style={{ width: '9rem' }}>{c.orgUnitName}</span>
                <span style={{ width: '7rem' }}>{c.jobTitleName ? `${c.jobGradeName} · ${c.jobTitleName}` : c.jobGradeName}</span>
                <span style={{ width: '6.5rem' }}>{c.gradeSince}</span>
                <span style={{ width: '4.5rem' }}>{`${c.yearsInGrade}년`}</span>
                <span style={{ width: '9rem' }}>{c.averageScore == null ? '–' : `${score(c.averageScore)} (${c.evaluationCount}건)`}</span>
                <span style={{ width: '6rem' }} title={c.promotionCycleName ?? undefined}>{score(c.promotionScore)}</span>
                {manage && (
                  <span style={{ width: '6rem' }}>
                    <button type="button" className="hr-link"
                      onClick={() => navigate(`/assignments/new?employeeId=${c.employeeId}&assignmentType=PROMOTION${next ? `&toValue=${next.id}` : ''}`)}>
                      승진 발령
                    </button>
                  </span>
                )}
              </div>
            );
          })
        )}
      </div>
      <p className="hr-notice">점수는 심사 참고 자료입니다. 승진 고과 결과는 본인에게 공개되지 않습니다.</p>
    </div>
  );
}
