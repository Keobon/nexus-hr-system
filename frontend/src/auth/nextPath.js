// 로그인 · 회사 등록 응답의 next → 첫 화면(프론트 가이드 2.2)
const PATHS = {
  CHANGE_PASSWORD: '/password',
  SETUP_WIZARD: '/setup',
  HOME: '/',
};

export const nextPath = (next) => PATHS[next] ?? '/';
