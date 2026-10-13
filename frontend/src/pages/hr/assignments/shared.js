// 인사발령 화면 공통 — 유형별로 바뀌는 값과 화면 문구
import { labelOf } from '../../../constants/labels';

/** 유형 → 바뀌는 칸(요청 필드 · 응답 이름 필드) */
export const FIELD_OF = {
  TRANSFER: { req: 'toOrgUnitId', from: 'fromOrgUnitName', to: 'toOrgUnitName', toId: 'toOrgUnitId', cur: 'orgUnitId', curName: 'orgUnitName', label: '부서' },
  PROMOTION: { req: 'toJobGradeId', from: 'fromJobGradeName', to: 'toJobGradeName', toId: 'toJobGradeId', cur: 'jobGradeId', curName: 'jobGradeName', label: '직급' },
  TITLE_CHANGE: { req: 'toJobTitleId', from: 'fromJobTitleName', to: 'toJobTitleName', toId: 'toJobTitleId', cur: 'jobTitleId', curName: 'jobTitleName', label: '직책' },
};

const none = (v) => v ?? '없음';

/** "인프라보안팀 → 백엔드개발팀" */
export const changeText = (a) => {
  const f = FIELD_OF[a.assignmentType];
  return f ? `${none(a[f.from])} → ${none(a[f.to])}` : '-';
};

/** 유형 알약 — 정정 건은 "정정 발령"(노랑), 나머지는 labels.js 의 assignmentType */
export const typeBadge = (a) =>
  a.correctionOfId ? { label: '정정 발령', tone: 'is-warn' } : { label: labelOf('assignmentType', a.assignmentType), tone: 'is-info' };

export const correctionText = (a) => {
  if (a.correctionOf) return `${a.correctionOf.effectiveDate} ${labelOf('assignmentType', a.correctionOf.assignmentType)} 발령 정정`;
  if (a.correctedById) return '정정됨 (정정 발령으로 대체)';
  return '해당 없음';
};

/** 정정 발령 화면으로 넘길 때 쓰는 "변경 후 값" 선택지 */
export function valueOptions(type, { orgOptions, grades, titles }, exceptId) {
  const filterOut = (list) => list.filter((o) => o.value !== exceptId);
  if (type === 'TRANSFER') return filterOut(orgOptions);
  if (type === 'PROMOTION') return filterOut((grades ?? []).map((g) => ({ value: g.id, label: g.name })));
  const titleOpts = (titles ?? []).map((t) => ({ value: t.id, label: t.name }));
  // 직책은 비울 수 있다 — "없음"은 0 으로 두고 보낼 때 null
  return filterOut([{ value: 0, label: '없음' }, ...titleOpts].filter((o) => !(o.value === 0 && exceptId == null)));
}
