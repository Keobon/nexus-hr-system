import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Modal, Result, Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { getMyAssignments, getMyEvalTodo, getMyEvaluations, myAssignmentsKey, myEvalTodoKey, myEvaluationsKey } from '../../../api/evaluations';
import { getMyProfile, myProfileKey } from '../../../api/profile';
import { usePendingFeature } from '../../../components/common/usePendingFeature';
import { errorMessage } from '../../../constants/errors';
import { LABELS } from '../../../constants/labels';
import iconBriefcase from '../../../assets/figma/icon-briefcase.svg';
import iconChevron from '../../../assets/figma/icon-chevron.svg';
import iconPrinter from '../../../assets/figma/icon-printer.svg';
import './evaluation.css';

const dot = (d) => (d ? d.slice(0, 10).replaceAll('-', '.') : '-');
const num = (n) => (n == null ? '-' : Number(n).toLocaleString('ko-KR', { maximumFractionDigits: 1 }));
const TABS = [
  ['assign', '인사발령 이력'],
  ['eval', '인사평가'],
  ['reward', '포상·징계'],
];
const PERIODS = [
  ['all', '전체 기간'],
  ['1', '최근 1년'],
  ['3', '최근 3년'],
  ['5', '최근 5년'],
];

/** 소속 · 직급 · 직책을 "백엔드팀 · 대리 · 팀장"처럼 */
const place = (org, grade, title) => [org, grade, title].filter(Boolean).join(' · ') || '—';
const typeLabel = (a) => (a.correctionOfId ? '정정' : (LABELS.assignmentType[a.assignmentType]?.label ?? a.assignmentType));

function Select({ value, onChange, options, label, width }) {
  return (
    <label className="ev-filter" style={{ width }}>
      <span>{label}</span>
      <span className="ev-select">
        <select value={value} onChange={(e) => onChange(e.target.value)}>
          {options.map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </select>
        <img src={iconChevron} alt="" />
      </span>
    </label>
  );
}

/** 인쇄 창 — 통지서처럼 모달만 찍을 때는 body 에 표시를 붙였다가 뗀다 */
function printOnly(cls) {
  if (cls) document.body.classList.add(cls);
  window.print();
  if (cls) setTimeout(() => document.body.classList.remove(cls), 0);
}

/* ───── ① 인사발령 이력 ───── */
function AssignTab({ data, status }) {
  const pending = usePendingFeature();
  const [period, setPeriod] = useState('all');
  const [type, setType] = useState('all');
  const [applied, setApplied] = useState({ period: 'all', type: 'all' });
  const [notice, setNotice] = useState(null);
  const cur = data.current;
  const history = data.history ?? [];
  const latest = history[0];
  const from = applied.period === 'all' ? null : dayjs().subtract(Number(applied.period), 'year').format('YYYY-MM-DD');
  const rows = history.filter(
    (a) =>
      (!from || a.effectiveDate >= from) &&
      (applied.type === 'all' || (applied.type === 'CORRECTION' ? a.correctionOfId : !a.correctionOfId && a.assignmentType === applied.type)),
  );

  return (
    <>
      <div className="ev-current">
        <img src={iconBriefcase} alt="" />
        <div>
          <strong>{`현재 소속 · ${[cur.orgUnitName, cur.jobGradeName, cur.jobTitleName].filter(Boolean).join(' / ')}`}</strong>
          <span>{latest ? `최근 발령 ${dot(latest.effectiveDate)} · ${typeLabel(latest)}${latest.reason ? ` · ${latest.reason}` : ''}` : '발령 이력이 없습니다'}</span>
        </div>
        {status && <span className={`ev-badge${status === 'ACTIVE' ? ' is-green' : ''}`}>{LABELS.employmentStatus[status]?.label}</span>}
      </div>

      <section className="ev-card is-filter">
        <div className="ev-filters">
          <Select label="조회 기간" value={period} onChange={setPeriod} options={PERIODS} width="16.875rem" />
          <Select
            label="발령 유형"
            value={type}
            onChange={setType}
            width="11.25rem"
            options={[['all', '전체 유형'], ...Object.entries(LABELS.assignmentType).map(([k, v]) => [k, v.label]), ['CORRECTION', '정정']]}
          />
          <button type="button" className="ev-btn" onClick={() => setApplied({ period, type })}>
            조회
          </button>
          <span className="ev-filter-note">발령일 기준 · 최신순</span>
        </div>
      </section>

      <section className="ev-card">
        <div className="ev-sec-head">
          <div>
            <h2>나의 발령 이력</h2>
            <p>부서 이동, 직급·직책 변경 기록을 날짜별로 확인합니다.</p>
          </div>
          <span className="ev-badge">{`총 ${rows.length}건`}</span>
        </div>
        <div className="ev-table">
          <div className="ev-tr is-head">
            <span>발령일</span>
            <span>유형</span>
            <span>변경 전</span>
            <span>변경 후</span>
            <span>발령 사유</span>
            <span>발령 통지서</span>
          </div>
          {rows.length === 0 && <p className="ev-empty">조건에 맞는 발령 이력이 없습니다.</p>}
          {rows.map((a, i) => (
            <div key={a.id} className={`ev-tr${i === 0 && a.id === latest?.id ? ' is-latest' : ''}`}>
              <span className="is-date">{dot(a.effectiveDate)}</span>
              <span>
                <span className={`ev-badge${i === 0 && a.id === latest?.id ? ' is-blue' : ''}`}>{typeLabel(a)}</span>
              </span>
              <span className="is-from">{place(a.fromOrgUnitName, a.fromJobGradeName, a.fromJobTitleName)}</span>
              <span>{place(a.toOrgUnitName, a.toJobGradeName, a.toJobTitleName)}</span>
              <span>{a.reason || '-'}</span>
              <span className="ev-links">
                <button type="button" className="ev-link" onClick={() => setNotice(a)}>
                  통지서 보기
                </button>
                <img src={iconPrinter} alt="" />
                <button
                  type="button"
                  className="ev-link"
                  onClick={() => {
                    setNotice(a);
                    setTimeout(() => printOnly('ev-printing-notice'), 300);
                  }}
                >
                  출력
                </button>
              </span>
            </div>
          ))}
        </div>
        <div className="ev-foot">
          <p className="ev-muted">발령 통지서는 각 기록에서 보거나 출력할 수 있습니다. 출력 창에서 &quot;PDF로 저장&quot;을 고르면 PDF 로 받습니다.</p>
        </div>
      </section>

      <section className="ev-card ev-print-hide">
        <div className="ev-sec-head">
          <div>
            <h2>사내 인사발령 공지</h2>
            <p>전사 및 소속 부서에 공개된 공지사항입니다.</p>
          </div>
        </div>
        <div className="ev-pending">
          <strong>준비 중</strong>
          <span>사내 공지 기능은 아직 제공하지 않습니다. 발령 공지는 인사팀 안내를 확인해 주세요.</span>
          <button type="button" className="ev-link" style={{ alignSelf: 'flex-start' }} onClick={() => pending('사내 인사발령 공지', '사내 공지')}>
            {'자세히 →'}
          </button>
        </div>
      </section>

      <Modal
        open={!!notice}
        title="인사발령 통지서"
        onCancel={() => setNotice(null)}
        footer={
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <button type="button" className="ev-btn is-line" onClick={() => setNotice(null)}>
              닫기
            </button>
            <button type="button" className="ev-btn" onClick={() => printOnly('ev-printing-notice')}>
              출력 · PDF
            </button>
          </div>
        }
      >
        {notice && (
          <div className="ev-notice">
            <p className="ev-muted">아래와 같이 인사발령을 통지합니다.</p>
            <dl>
              <dt>성명</dt>
              <dd>{`${cur.name} (사번 ${cur.employeeNo})`}</dd>
              <dt>발령일</dt>
              <dd>{dot(notice.effectiveDate)}</dd>
              <dt>발령 유형</dt>
              <dd>{typeLabel(notice)}</dd>
              <dt>변경 전</dt>
              <dd>{place(notice.fromOrgUnitName, notice.fromJobGradeName, notice.fromJobTitleName)}</dd>
              <dt>변경 후</dt>
              <dd>{place(notice.toOrgUnitName, notice.toJobGradeName, notice.toJobTitleName)}</dd>
              <dt>발령 사유</dt>
              <dd>{notice.reason || '-'}</dd>
              {notice.correctionOf && (
                <>
                  <dt>정정 대상</dt>
                  <dd>{`${dot(notice.correctionOf.effectiveDate)} 발령의 정정`}</dd>
                </>
              )}
              <dt>등록</dt>
              <dd>{`${notice.createdByName ?? '-'} · ${dot(notice.createdAt)}`}</dd>
            </dl>
          </div>
        )}
      </Modal>
    </>
  );
}

/* ───── ② 인사평가 ───── */
/**
 * 응답 척도 — 질문마다 1–5점(BR-EVAL-004). 수준 이름 · 설명은 Figma 인사평가 기준표 문구.
 * 평균이 소수라 경계는 4.5 · 3.5 · 2.5 · 1.5 "이상"으로 나눈다.
 */
const SCALE = [
  [5, 4.5, '매우 우수', '기대 수준을 꾸준히 넘어서며 주변에 모범이 됨'],
  [4, 3.5, '우수', '기대 수준을 안정적으로 충족하고 일부는 넘어섬'],
  [3, 2.5, '보통', '기대 수준을 대체로 충족함'],
  [2, 1.5, '미흡', '기대 수준에 자주 못 미쳐 보완이 필요함'],
  [1, 1, '매우 미흡', '기대 수준에 크게 못 미쳐 즉각적인 개선이 필요함'],
];
const levelOf = (avg) => SCALE.find(([, min]) => avg >= min)?.[2] ?? '-';
/** 종합 수준 — 총점 ÷ 20 = 배점으로 가중한 항목 평균(1–5). 등급(A · B)은 서버에 없어 척도 수준으로 보여 준다 */
const overallAvg = (e) => (e?.totalScore == null ? null : Number(e.totalScore) / 20);
const yearOf = (e) => e.cycleName.match(/(\d{4})/)?.[1] ?? (e.confirmedAt ?? '').slice(0, 4);

function EvalTab({ evaluations, onCriteria, todoCount }) {
  const navigate = useNavigate();
  const years = [...new Set(evaluations.map(yearOf))];
  const [draft, setDraft] = useState(() => ({ year: years[0] ?? '', type: 'ALL', id: evaluations[0] ? String(evaluations[0].evaluationId) : '' }));
  const [applied, setApplied] = useState(draft.id);
  const inYear = evaluations.filter((x) => yearOf(x) === draft.year);
  const types = [...new Set(inYear.map((x) => x.templateName))];
  const periods = inYear.filter((x) => draft.type === 'ALL' || x.templateName === draft.type);
  // 연도 · 유형을 바꾸면 기간은 그 안의 첫 평가로
  const change = (key, value) =>
    setDraft((d) => {
      const next = { ...d, [key]: value };
      if (key !== 'id') {
        const list = evaluations.filter((x) => yearOf(x) === next.year && (next.type === 'ALL' || x.templateName === next.type));
        next.id = list[0] ? String(list[0].evaluationId) : '';
      }
      return next;
    });
  const e = evaluations.find((x) => String(x.evaluationId) === applied);
  const avg = overallAvg(e);
  const empty = [['', '확정된 평가 없음']];

  return (
    <>
      <section className="ev-card is-filter is-r16 ev-print-hide">
        <div className="ev-filters is-eval">
          <Select label="평가 연도" value={draft.year} onChange={(v) => change('year', v)} width="9.375rem" options={years.length ? years.map((y) => [y, `${y}년`]) : empty} />
          <Select
            label="평가 기간"
            value={draft.id}
            onChange={(v) => change('id', v)}
            width="13.75rem"
            options={periods.length ? periods.map((x) => [String(x.evaluationId), x.cycleName]) : empty}
          />
          <Select
            label="평가 유형"
            value={draft.type}
            onChange={(v) => change('type', v)}
            width="11.875rem"
            options={[['ALL', '전체 유형'], ...types.map((t) => [t, t])]}
          />
          <button type="button" className="ev-btn" disabled={!draft.id} onClick={() => setApplied(draft.id)}>
            조회
          </button>
          <button type="button" className="ev-btn is-line" disabled={!e} onClick={() => printOnly()}>
            PDF 다운로드
          </button>
          <button type="button" className="ev-btn is-line" disabled={!e} onClick={() => printOnly()}>
            출력
          </button>
          <button type="button" className="ev-btn is-line" onClick={() => onCriteria(e?.evaluationId)}>
            인사평가 기준표 보기
          </button>
        </div>
      </section>

      <section className="ev-card is-r16 ev-eval">
        <div className="ev-sec-head">
          <div>
            <h2>{e ? `${e.cycleName} 평가 결과` : '평가 결과'}</h2>
            <p>{e ? `${e.templateName} · 확정일 ${dot(e.confirmedAt)}` : '확정된 평가가 생기면 여기에 표시됩니다.'}</p>
          </div>
          <span className={`ev-badge${e ? ' is-green' : ''}`}>{e ? '확정' : '확정 대기 중'}</span>
        </div>
        {!e && <p className="ev-empty">아직 확정된 평가 결과가 없습니다. 평가가 확정되면 본인에게 공개됩니다.</p>}
        {e && (
          <>
            <div className="ev-result">
              <div className="ev-total-box">
                <span>종합 수준</span>
                <strong>{avg == null ? '-' : num(Math.round(avg * 10) / 10)}</strong>
                <span className="is-blue">{avg == null ? '-' : levelOf(avg)}</span>
              </div>
              <div className="ev-scores">
                {e.criteria.map((c) => (
                  <div key={c.criteriaId} className="ev-score-row">
                    <div className="ev-score">
                      <span>{c.name}</span>
                      <strong>{`${num(c.convertedScore)} / ${c.weight}점`}</strong>
                    </div>
                    <div className="ev-bar">
                      <span style={{ width: `${Math.min(100, ((Number(c.convertedScore) || 0) / c.weight) * 100)}%` }} />
                    </div>
                  </div>
                ))}
                <div className="ev-score is-total">
                  <span>총점</span>
                  <strong>{`${num(e.totalScore)} / 100점`}</strong>
                </div>
              </div>
            </div>
            <div className="ev-feedback is-box">
              <h3>종합 피드백</h3>
              <p>{e.overallComment || '작성된 종합 의견이 없습니다.'}</p>
            </div>
          </>
        )}
        <p className="ev-muted is-11">미확정 평가 기간은 ‘확정 대기 중’으로 안내되며 결과가 공개되지 않습니다.</p>
      </section>

      <section className="ev-card is-r16 ev-eval ev-print-hide">
        <div className="ev-sec-head">
          <div>
            <h2>자기평가 작성</h2>
            <p>평가는 평가자(조직장)가 작성합니다</p>
          </div>
          <span className="ev-badge">준비 중</span>
        </div>
        <div className="ev-pending">
          <strong>자기평가는 아직 제공하지 않습니다</strong>
          <span>이 시스템의 평가는 평가자가 대상자를 평가하는 방식입니다. 내가 평가자인 평가가 있으면 아래에서 바로 갈 수 있습니다.</span>
        </div>
        {todoCount > 0 && (
          <button type="button" className="ev-btn" style={{ alignSelf: 'flex-end' }} onClick={() => navigate('/evaluations/todo')}>
            {`평가하기 · ${todoCount}건`}
          </button>
        )}
      </section>

      <section className="ev-outside ev-print-hide">
        <div className="ev-outside-head">
          <h2>평가자 피드백 및 면담록</h2>
          <span>{e ? `${e.cycleName} · 나에게 공개된 평가 의견` : '나에게 공개된 평가 의견'}</span>
        </div>
        <div className="ev-card is-r16">
          <div className="ev-pending">
            <strong>평가자별 의견 · 면담록은 준비 중입니다</strong>
            <span>지금은 평가자 한 명의 종합 의견만 공개되며, 위 평가 결과의 종합 피드백에서 확인할 수 있습니다.</span>
          </div>
        </div>
      </section>
    </>
  );
}

/* ───── ③ 포상 · 징계 — 기능명세서 v2 범위 밖 ───── */
function RewardTab() {
  return (
    <>
      <div className="ev-two">
        {[
          ['나의 포상 이력', '우수사원 표창 · 사내 상장'],
          ['나의 징계 · 경고 이력', '본인에게 공개된 기록'],
        ].map(([t, s]) => (
          <section key={t} className="ev-card">
            <div className="ev-summary">
              <div>
                <span className="ev-muted">{t}</span>
                <strong>준비 중</strong>
                <span className="ev-muted">{s}</span>
              </div>
            </div>
          </section>
        ))}
      </div>
      <section className="ev-card">
        <div className="ev-pending">
          <strong>포상 · 징계 기능은 아직 제공하지 않습니다</strong>
          <span>포상 · 징계 기록은 기능명세서 v2 범위에 없어 이 시스템에서 관리하지 않습니다. 필요한 내용은 인사팀에 문의해 주세요.</span>
        </div>
      </section>
    </>
  );
}

/* ───── ④ 인사평가 기준표 — Figma 348:7763 ───── */
/** 항목 환산 점수 범위 — 환산 = 항목 평균 ÷ 5 × 배점(BR-EVAL-004) */
const band = (weight, min) => Math.round(((weight * min) / 5) * 10) / 10;

function Criteria({ e, onBack }) {
  const total = e ? e.criteria.reduce((a, c) => a + c.weight, 0) : 100;
  return (
    <div className="ev">
      <div className="ev-crumb ev-print-hide">
        <button type="button" onClick={onBack}>
          인사발령/평가조회
        </button>
        <span>/</span>
        <strong>인사평가 기준표</strong>
      </div>
      <header className="ev-head">
        <div>
          <h1>인사평가 기준표</h1>
          <p>평가 항목, 배점, 세부 기준을 확인할 수 있는 기준표입니다.</p>
        </div>
        <div className="ev-links ev-print-hide">
          <button type="button" className="ev-btn" onClick={() => window.print()}>
            PDF 다운로드
          </button>
          <button type="button" className="ev-btn is-line" onClick={() => window.print()}>
            출력
          </button>
        </div>
      </header>
      <section className="ev-card ev-crit">
        <div className="ev-sheet-title">
          <div>
            <span className="ev-muted">{e ? `기준표 · ${e.cycleName}` : '기준표'}</span>
            <h2>{e ? `${e.templateName} 기준표` : '인사평가 기준표'}</h2>
          </div>
          <span className="ev-badge is-green">{e ? '적용 기준' : '공통 기준'}</span>
        </div>

        <div className="ev-crit-sec">
          <h3>기준표 안내</h3>
          <p>
            평가자는 항목마다 질문에 1–5점으로 답하고, 항목 점수는 그 질문 점수의 평균입니다. 환산 점수는 항목 점수 ÷ 5 × 배점이며, 항목 환산 점수를 모두 더한 값이 총점(100점 만점)입니다.
          </p>
        </div>
        {e && (
          <div className="ev-crit-sum">
            <div>
              <span>총 배점</span>
              <strong className="is-big">{`${total}점`}</strong>
            </div>
            <div>
              <span>평가 항목</span>
              <strong>{e.criteria.map((c) => c.name).join(' · ')}</strong>
            </div>
            <div>
              <span>적용 범위</span>
              <strong>{e.templateName}</strong>
            </div>
          </div>
        )}

        <div className="ev-crit-sec">
          <h3>세부 기준표</h3>
          <p>각 항목은 배점과 기준을 함께 확인할 수 있습니다. 항목 점수(질문 평균)에 따라 아래 범위의 환산 점수를 받습니다.</p>
        </div>
        <div className="ev-crit-box">
          <h4>응답 척도</h4>
          <div className="ev-crit-row is-head">
            <span>점수</span>
            <span>수준</span>
            <span>설명</span>
          </div>
          {SCALE.map(([score, , name, desc]) => (
            <div key={score} className="ev-crit-row">
              <strong>{score}</strong>
              <span>{name}</span>
              <span>{desc}</span>
            </div>
          ))}
        </div>
        {!e && <p className="ev-empty">확정된 평가가 생기면 그 평가에 쓰인 항목 · 배점별 기준이 여기에 보입니다.</p>}
        {e?.criteria.map((c) => (
          <div key={c.criteriaId} className="ev-crit-box is-item">
            <div className="ev-sec-head">
              <div>
                <h4>{c.name}</h4>
                <span className="ev-crit-weight">{`배점 ${c.weight}점`}</span>
              </div>
              {c.category && <span className="ev-badge is-green">{c.category}</span>}
            </div>
            {SCALE.map(([score, min, name], i) => (
              <div key={score} className="ev-crit-row">
                <strong>{`${num(band(c.weight, min))}–${num(i === 0 ? c.weight : band(c.weight, SCALE[i - 1][1]))}점`}</strong>
                <span>{`평균 ${min} 이상`}</span>
                <span>{`${name} — ${SCALE[i][3]}`}</span>
              </div>
            ))}
          </div>
        ))}
      </section>
      <div className="ev-note ev-print-hide">
        <span>본인에게 공개된 인사정보만 조회할 수 있습니다. 범위의 경계 점수는 위 구간에 속합니다.</span>
        <span>인사 문의 · 인사팀</span>
      </div>
    </div>
  );
}

/** 인사발령/평가 조회(F-ASSIGN-03 본인 · F-EVAL-05) — Figma 인사발령 이력 · 인사평가 · 포상·징계 · 인사평가 기준표 */
export default function MyEvaluationPage() {
  const [params, setParams] = useSearchParams();
  const tab = TABS.some(([k]) => k === params.get('tab')) ? params.get('tab') : 'assign';
  // 인사평가 기준표 — ?view=criteria&eval=<평가 id>(없으면 공통 척도만)
  const criteriaView = params.get('view') === 'criteria';
  const criteriaId = params.get('eval');
  const assignments = useQuery({ queryKey: myAssignmentsKey, queryFn: getMyAssignments });
  const evaluations = useQuery({ queryKey: myEvaluationsKey, queryFn: getMyEvaluations });
  const profile = useQuery({ queryKey: myProfileKey, queryFn: getMyProfile });
  const todo = useQuery({ queryKey: myEvalTodoKey, queryFn: getMyEvalTodo });

  const list = useMemo(() => evaluations.data ?? [], [evaluations.data]);
  const todoCount = (todo.data ?? []).filter((t) => t.status !== 'SUBMITTED' && t.status !== 'CONFIRMED').length;

  if (assignments.isLoading || evaluations.isLoading) {
    return (
      <div className="page-center" style={{ minHeight: '25rem' }}>
        <Spin size="large" />
      </div>
    );
  }
  const error = assignments.error ?? evaluations.error;
  if (error) return <Result status="warning" title="인사 정보를 불러오지 못했습니다" subTitle={errorMessage(error)} />;

  const cur = assignments.data.current;
  const go = (next) => setParams({ tab: next });
  if (criteriaView) return <Criteria e={list.find((x) => String(x.evaluationId) === criteriaId)} onBack={() => setParams({ tab: 'eval' })} />;

  return (
    <div className="ev">
      <header className="ev-head ev-print-hide">
        <div>
          <h1>인사발령/평가조회</h1>
          <p>나의 인사 이력과 평가 내역을 확인하세요.</p>
        </div>
        <div className="ev-who">
          <strong>{place(cur.name, cur.orgUnitName, cur.jobGradeName)}</strong>
          <span>{`${cur.jobTitleName ? `${cur.jobTitleName}  |  ` : ''}사번 ${cur.employeeNo}`}</span>
        </div>
      </header>

      <nav className="ev-tabs ev-print-hide" aria-label="인사발령/평가 메뉴">
        {TABS.map(([k, l]) => (
          <button key={k} type="button" className={`ev-tab${tab === k ? ' is-active' : ''}`} onClick={() => go(k)}>
            {l}
          </button>
        ))}
      </nav>

      {tab === 'assign' && <AssignTab data={assignments.data} status={profile.data?.status} />}
      {tab === 'eval' && (
        <EvalTab
          evaluations={list}
          todoCount={todoCount}
          onCriteria={(id) => setParams(id ? { tab: 'eval', view: 'criteria', eval: String(id) } : { tab: 'eval', view: 'criteria' })}
        />
      )}
      {tab === 'reward' && <RewardTab />}

      <div className="ev-note ev-print-hide">
        <span>본인에게 공개된 인사정보만 조회할 수 있습니다.</span>
        <span>인사 문의 · 인사팀</span>
      </div>
    </div>
  );
}
