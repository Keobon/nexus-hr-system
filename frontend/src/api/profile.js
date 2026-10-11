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
