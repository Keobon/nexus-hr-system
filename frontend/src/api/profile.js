// 내 정보(API 6장) — 개인 페이지 · 본인 수정
import { api } from './client';

export const myProfileKey = ['me', 'profile'];

/** GET /me/profile — 기본 정보 · 소속 · 재직상태 · 추가 항목 값(인사 메모 · 계좌 없음) */
export const getMyProfile = () => api.get('/me/profile');

/** PATCH /me/profile — 본인이 바로 고치는 항목만(전화 · 주소 · 비상연락처 · 프로필 사진 등) */
export const updateMyProfile = (body) => api.patch('/me/profile', body);
