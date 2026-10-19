// 기준표 미리보기(Figma 473-12319) — 편집 중인 템플릿을 직원 화면 "인사평가 기준표"와 같은 문서 모양으로
import { Modal } from 'antd';

/**
 * 응답 척도 — 질문마다 1–5점(BR-EVAL-004). 수준 이름 · 설명은 Figma 인사평가 기준표 문구(직원 화면과 같은 값).
 * 평균이 소수라 경계는 4.5 · 3.5 · 2.5 · 1.5 "이상"으로 나눈다.
 */
export const SCALE = [
  [5, 4.5, '매우 우수', '기대 수준을 꾸준히 넘어서며 주변에 모범이 됨'],
  [4, 3.5, '우수', '기대 수준을 안정적으로 충족하고 일부는 넘어섬'],
  [3, 2.5, '보통', '기대 수준을 대체로 충족함'],
  [2, 1.5, '미흡', '기대 수준에 자주 못 미쳐 보완이 필요함'],
  [1, 1, '매우 미흡', '기대 수준에 크게 못 미쳐 즉각적인 개선이 필요함'],
];
/** 항목 환산 점수 범위 — 환산 = 항목 평균 ÷ 5 × 배점(BR-EVAL-004) */
export const band = (weight, min) => Math.round(((weight * min) / 5) * 10) / 10;
export const num = (n) => Number(n).toLocaleString('ko-KR', { maximumFractionDigits: 1 });

export default function CriteriaPreview({ draft, onClose }) {
  const total = draft.criteria.reduce((a, c) => a + (c.weight || 0), 0);
  const name = draft.name.trim() || '새 템플릿';
  return (
    <Modal open title={null} footer={null} onCancel={onClose} width={880} className="et-preview" destroyOnHidden centered>
      <div className="et-pv-head et-print-hide">
        <strong>기준표 미리보기</strong>
        <span>현재 편집 중인 기준표를 기준표 문서 형태로 확인합니다.</span>
      </div>
      <div className="et-sheet">
        <div className="et-pv-title">
          <div>
            <h1>인사평가 기준표</h1>
            <p>평가 항목, 배점, 세부 기준을 확인할 수 있는 기준표입니다.</p>
          </div>
          <div className="et-actions et-print-hide">
            <button type="button" className="et-btn" onClick={() => window.print()}>
              PDF 다운로드
            </button>
            <button type="button" className="et-btn is-line" onClick={() => window.print()}>
              출력
            </button>
          </div>
        </div>

        <section className="et-doc">
          <div className="et-doc-title">
            <div>
              <span className="et-muted">기준표</span>
              <h2>{`${name} 기준표`}</h2>
            </div>
            <span className="hr-badge is-ok">편집 중</span>
          </div>

          <div className="et-doc-sum">
            <div>
              <span>총 배점</span>
              <strong className="is-big">{`${total}점`}</strong>
            </div>
            <div>
              <span>평가 항목</span>
              <strong>{draft.criteria.map((c) => c.name || '새 항목').join(' · ')}</strong>
            </div>
            <div>
              <span>질문 수</span>
              <strong>{`${draft.criteria.reduce((a, c) => a + c.questions.length, 0)}개`}</strong>
            </div>
          </div>

          <div className="et-doc-sec">
            <h3>기준표 안내</h3>
            <p>
              평가자는 항목마다 질문에 1–5점으로 답하고, 항목 점수는 그 질문 점수의 평균입니다. 환산 점수는 항목 점수 ÷ 5 × 배점이며, 항목 환산 점수를 모두 더한 값이 총점(100점 만점)입니다.
            </p>
          </div>

          <div className="et-doc-box">
            <h4>응답 척도</h4>
            <div className="et-doc-row is-head">
              <span>점수</span>
              <span>수준</span>
              <span>설명</span>
            </div>
            {SCALE.map(([score, , level, desc]) => (
              <div key={score} className="et-doc-row">
                <strong>{score}</strong>
                <span>{level}</span>
                <span>{desc}</span>
              </div>
            ))}
          </div>

          <div className="et-doc-sec">
            <h3>세부 기준표</h3>
            <p>각 항목은 배점과 기준을 함께 확인할 수 있습니다. 항목 점수(질문 평균)에 따라 아래 범위의 환산 점수를 받습니다.</p>
          </div>
          {draft.criteria.map((c) => (
            <div key={c.key} className="et-doc-item">
              <div className="et-doc-item-head">
                <div>
                  <h4>{c.name || '새 항목'}</h4>
                  <span className="et-muted">{`배점 ${c.weight || 0}점`}</span>
                </div>
                <span className="hr-badge is-ok">{c.category}</span>
              </div>
              {c.questions.map((q, i) => (
                <div key={q.key} className="et-doc-q">
                  <span>{i + 1}</span>
                  <span>{q.content || '(질문 없음)'}</span>
                </div>
              ))}
              <div className="et-doc-box">
                <h4>세부 기준</h4>
                <div className="et-band is-head">
                  <span>점수 구간</span>
                  <span>질문 평균</span>
                  <span>설명</span>
                </div>
                {SCALE.map(([score, min, level, desc], i) => (
                  <div key={score} className="et-band is-plain">
                    <strong>{`${num(band(c.weight || 0, min))}–${num(i === 0 ? c.weight || 0 : band(c.weight || 0, SCALE[i - 1][1]))}점`}</strong>
                    <span>{i === 0 ? `${min} 이상` : `${min} ~ ${num(SCALE[i - 1][1] - 0.1)}`}</span>
                    <span>{`${level} — ${desc}`}</span>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </section>
      </div>
    </Modal>
  );
}
