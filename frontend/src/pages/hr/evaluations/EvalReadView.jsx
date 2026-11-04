// 평가 결과 조회 — 평가를 조회만 하는 사람(경영진 · 조직장, EVAL_MANAGE 없음). Figma 72-7789(전사 평가 결과 조회)
// GET /evaluations/results — 범위 안 확정 결과(종합 의견 포함). 평가 사이클 · 부서 · 직원으로 거르고, 고른 사람의 항목 점수 · 총점 · 종합 의견을 오른쪽에
import { useMemo, useState } from 'react';
import { Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useMe } from '../../../auth/useMe';
import { evalResultsKey, getEvalResults } from '../../../api/evalAdmin';
import { fetchAllPages, getEmployees } from '../../../api/employees';
import { errorMessage } from '../../../constants/errors';
import { PageHead } from '../parts';
import { Field, OrgField, Pill, Table } from '../attendance/shared';
import { num } from './shared';
import '../attendance/at.css';

export default function EvalReadView() {
  const { me } = useMe();
  const whole = (me?.permissions ?? []).some((p) => p.code === 'EVAL_READ' && p.scope === 'ALL');
  const [cycle, setCycle] = useState(null);
  const [orgUnitId, setOrgUnitId] = useState(null);
  const [employeeNo, setEmployeeNo] = useState(null);
  const [pick, setPick] = useState(null);

  const results = useQuery({ queryKey: evalResultsKey({ all: true, orgUnitId }), queryFn: () => fetchAllPages(getEvalResults, { orgUnitId: orgUnitId ?? undefined }) });
  // 대상자 직급 — 결과 행에 없어 직원 목록에서(못 읽으면 부서만)
  const emps = useQuery({ queryKey: ['employees', 'all-nos'], queryFn: () => fetchAllPages(getEmployees, {}), staleTime: 5 * 60 * 1000 });
  const gradeOf = useMemo(() => Object.fromEntries((emps.data ?? []).map((e) => [e.employeeNo, e.jobGradeName])), [emps.data]);

  const all = results.data ?? [];
  const cycles = [...new Set(all.map((r) => r.cycleName))];
  const people = [...new Map(all.map((r) => [r.employeeNo, `${r.targetName} · ${r.orgUnitName}`])).entries()].map(([value, label]) => ({ value, label }));
  const rows = all.filter((r) => (!cycle || r.cycleName === cycle) && (!employeeNo || r.employeeNo === employeeNo));
  const selected = rows.find((r) => r.evaluationId === pick) ?? rows[0];

  return (
    <div className="hr at erv">
      <PageHead title={whole ? '전사 평가 결과 조회' : '팀 평가 결과 조회'} desc="사이클·부서·직원으로 확정된 평가 결과를 조회합니다. (조회 전용)" />

      <div className="at-filters erv-box">
        <Field label="평가 사이클" width="13rem">
          <Select value={cycle ?? 'ALL'} onChange={(v) => setCycle(v === 'ALL' ? null : v)} options={[{ value: 'ALL', label: '전체 사이클' }, ...cycles.map((c) => ({ value: c, label: c }))]} />
        </Field>
        <OrgField value={orgUnitId} onChange={(v) => { setOrgUnitId(v); setEmployeeNo(null); }} />
        <Field label="직원" width="11rem">
          <Select value={employeeNo ?? 'ALL'} onChange={(v) => setEmployeeNo(v === 'ALL' ? null : v)} options={[{ value: 'ALL', label: '전체 직원' }, ...people]} showSearch optionFilterProp="label" />
        </Field>
      </div>

      <div className="erv-split">
        <section className="at-card">
          <Table
            cols={[
              { key: 'cycle', label: '사이클' },
              { key: 'who', label: '대상자', width: '8rem' },
              { key: 'total', label: '총점', width: '6rem' },
              { key: 'st', label: '상태', width: '6rem' },
            ]}
            loading={results.isLoading}
            error={results.error && errorMessage(results.error)}
            empty={rows.length === 0 && '확정된 평가 결과가 없습니다'}
          >
            {rows.map((r) => (
              <tr key={r.evaluationId} className={`is-click${selected?.evaluationId === r.evaluationId ? ' is-on' : ''}`} onClick={() => setPick(r.evaluationId)}>
                <td>{r.cycleName}</td>
                <td className="at-strong">{r.targetName}</td>
                <td className="at-strong">{`${num(r.totalScore)}점`}</td>
                <td>
                  <Pill tone="ok">확정</Pill>
                </td>
              </tr>
            ))}
          </Table>
        </section>

        <section className="at-card erv-detail">
          <h3>평가 상세</h3>
          {!selected ? (
            <p className="at-muted">왼쪽 목록에서 평가를 고르세요.</p>
          ) : (
            <>
              <dl className="erv-kv">
                <dt>대상자</dt>
                <dd>{`${selected.targetName} (${[selected.orgUnitName, gradeOf[selected.employeeNo]].filter(Boolean).join(' · ')})`}</dd>
                <dt>평가 사이클</dt>
                <dd>{selected.cycleName}</dd>
                <dt>평가 유형</dt>
                <dd>{`${selected.templateName} · 평가자 ${selected.evaluatorName}`}</dd>
                {(selected.criteria ?? []).map((c) => (
                  <FragmentRow key={c.criteriaId ?? c.name} k={c.name} v={`${num(c.convertedScore)} / ${c.weight}점`} />
                ))}
                <dt className="is-strong">총점</dt>
                <dd className="erv-total">{`${num(selected.totalScore)} / 100점`}</dd>
              </dl>
              <div className="erv-comment">
                <span>종합의견</span>
                <p>{selected.overallComment || '작성된 종합 의견이 없습니다.'}</p>
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  );
}

function FragmentRow({ k, v }) {
  return (
    <>
      <dt>{k}</dt>
      <dd>{v}</dd>
    </>
  );
}
