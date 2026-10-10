// 권한별 노출(프론트 가이드 2.3, 메뉴 구조 M-1).
// 역할 이름("인사 담당" 등)으로 분기하지 않는다 — 회사마다 역할이 다르다.

/** /me 로 can · canAny 를 만든다. 팀 범위는 조직장일 때만 쓸 수 있는 것으로 본다. */
export function makeCan(me) {
  const permissions = me?.permissions ?? [];
  const usable = (p) => p.scope === 'ALL' || me?.isOrgLead === true;
  const can = (code) => permissions.some((p) => p.code === code && usable(p));
  const canAny = (...codes) => codes.some(can);
  return { can, canAny };
}

/** 초기 설정 미완료 + COMPANY_MANAGE 면 "인사 관리"·"회사 설정"을 잠근다(M-6) */
export function isSetupLocked(me) {
  if (!me || me.company?.setupCompleted) return false;
  return makeCan(me).can('COMPANY_MANAGE');
}
