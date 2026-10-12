// 사내 전화번호 — 시연(10/15)용 임시 값. 백엔드에 사내 전화 필드가 아직 없다(스키마 · ERD · API 변경 필요, 노션 2.3 R-115).
// 필드가 생기면 이 파일을 지우고 응답 값(member.officePhone 등)으로 바꾼다.

/** 직원 ID로 정해지는 대표번호 + 내선(같은 직원은 늘 같은 번호) */
export function officePhone(employeeId) {
  return `02-6200-${String(1000 + (employeeId % 9000)).padStart(4, '0')}`;
}
