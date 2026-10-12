// 내 정보(API 6장) — 개인 페이지 · 본인 수정
import { api } from './client';

export const myProfileKey = ['me', 'profile'];

/** GET /me/profile — 기본 정보 · 소속 · 재직상태 · 추가 항목 값(인사 메모 · 계좌 없음) */
export const getMyProfile = () => api.get('/me/profile');

/** PATCH /me/profile — 본인이 바로 고치는 항목만(전화 · 주소 · 비상연락처 · 프로필 사진 등) */
export const updateMyProfile = (body) => api.patch('/me/profile', body);

export const myBankAccountKey = ['me', 'bank-account'];

/** GET /me/bank-account → { bankName, accountNoMasked, accountNo, holder }, 등록 전이면 null */
export const getMyBankAccount = () => api.get('/me/bank-account');

export const myFamilyKey = ['me', 'family'];

/** GET /me/family → { dependentsCount, childrenCount, members[] } (본인은 조회만) */
export const getMyFamily = () => api.get('/me/family');

/** PUT /me/bank-account { bankName, accountNo, holder } — 본인이 바로 수정(F-PAY-03, 노션 수정사항 10/12) */
export const updateMyBankAccount = (body) => api.put('/me/bank-account', body);

/** GET /employee-fields?activeOnly=true → [{ id, name, fieldType, options, isRequired, isMultiple, isSelfEditable, sortOrder }] */
export const employeeFieldsKey = ['employee-fields', 'active'];
export const getEmployeeFields = () => api.get('/employee-fields', { activeOnly: true });

/**
 * PUT /me/field-values { values: [{ fieldDefId, seq, value }] } — 본인 수정 가능 항목 **전체** 교체.
 * 보내지 않은 본인 수정 항목의 값은 지워지므로, 한 항목만 고칠 때도 나머지 값을 같이 보낸다.
 */
export const updateMyFieldValues = (values) => api.put('/me/field-values', { values });
