// 회사 등록 · 인증 · 내 정보(API 2장)
import { api } from './client';

export const meKey = ['me'];

export const getMe = () => api.get('/me');

/** → { token, next: 'CHANGE_PASSWORD' | 'SETUP_WIZARD' | 'HOME' } */
export const login = (email, password) => api.post('/auth/login', { email, password });

export const logout = () => api.post('/auth/logout');

export const changePassword = (currentPassword, newPassword) =>
  api.patch('/auth/password', { currentPassword, newPassword });

/** → { token, companyId, employeeId, next } */
export const registerCompany = (body) => api.post('/companies', body);
