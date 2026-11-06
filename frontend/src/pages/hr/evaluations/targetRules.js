// 정기 평가 대상 규칙(위에서부터 처음 맞는 규칙) — 평가 만들기 · 수정 창은 조직장 규칙과 나머지 직원 규칙만 다룬다
const hasOtherCond = (r) => !!(r.condJobGradeId || r.condJobTitleId || r.condEmploymentTypeId);
const isLeadRule = (r) => r.condIsOrgLead === true && !hasOtherCond(r);
const isRestRule = (r) => r.condIsOrgLead == null && !hasOtherCond(r);

export const leadRuleOf = (rules) => rules.find(isLeadRule);
export const restRuleOf = (rules) => rules.find(isRestRule);
/** 이 창이 다루지 않는 규칙(직급 · 직책 · 고용형태 조건, "조직장 아님" 등) — 그대로 둔다 */
export const customRulesOf = (rules) => rules.filter((r) => !isLeadRule(r) && !isRestRule(r));

/**
 * 조직장 · 나머지 규칙만 새 값으로 바꾸고 다른 규칙은 순서 그대로 둔다. 조직장 규칙이 새로 생기면 맨 앞,
 * 나머지 규칙이 없으면 맨 뒤. 조직장 평가지가 "직원과 같게"면 조직장 규칙을 뺀다
 */
export function mergeRegularRules(rules, { leadTemplateId, staffTemplateId }) {
  const lead =
    leadTemplateId !== 'SAME' && leadTemplateId !== staffTemplateId
      ? { condIsOrgLead: true, evalTemplateId: leadTemplateId === 'NONE' ? null : leadTemplateId }
      : null;
  const rest = { evalTemplateId: staffTemplateId };
  const next = [];
  let leadSeen = false;
  let restSeen = false;
  rules.forEach((r) => {
    if (isLeadRule(r)) {
      if (!leadSeen && lead) next.push(lead);
      leadSeen = true;
    } else if (isRestRule(r)) {
      if (!restSeen) next.push(rest);
      restSeen = true;
    } else {
      next.push(r);
    }
  });
  if (lead && !leadSeen) next.unshift(lead);
  if (!restSeen) next.push(rest);
  return next.map((r, i) => ({ ...r, priority: i + 1 }));
}
