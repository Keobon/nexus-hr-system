// VALIDATION_ERROR 의 error.fields 를 입력칸 아래에 표시한다(프론트 가이드 2.1).
// 서버 키 "values.12" 같은 점 표기는 AntD 이름 배열 ['values', '12'] 로 바꾼다.

/** 필드 오류를 폼에 넣었으면 true — false 면 호출한 쪽이 토스트를 띄운다 */
export function applyFieldErrors(form, err) {
  if (err?.code !== 'VALIDATION_ERROR' || !err.fields) return false;
  const known = new Set(
    Object.keys(form.getFieldsValue(true)).map(String),
  );
  // "values.12" 의 12 는 배열 위치가 아니라 항목 ID 라서 문자열 그대로 둔다
  const entries = Object.entries(err.fields).map(([key, msg]) => ({
    name: key.split('.'),
    errors: [msg],
  }));
  form.setFields(entries);
  return entries.some((e) => known.has(String(e.name[0])));
}
