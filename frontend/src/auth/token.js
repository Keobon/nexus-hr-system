// 토큰은 localStorage 에 둔다(유효 8시간, 리프레시 없음 — 프론트 가이드 2.2).
const KEY = 'nexus.token';

export function getToken() {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function setToken(token) {
  try {
    localStorage.setItem(KEY, token);
  } catch {
    // 저장소를 못 쓰는 환경(사생활 보호 모드 등)이면 이번 탭에서만 로그인이 유지되지 않는다
  }
}

export function clearToken() {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // 무시
  }
}
