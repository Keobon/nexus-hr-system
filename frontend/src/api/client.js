// 모든 API 호출의 입구(프론트 가이드 2.1).
// 응답은 항상 { success, data, error } — success 만 보고 분기한다.
// 성공이면 data 를 돌려주고, 실패면 ApiError 를 던진다.
import { clearToken, getToken } from '../auth/token';

const BASE_URL = '/api';

export class ApiError extends Error {
  constructor({ status, code, message, fields = null, details = null }) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fields = fields;
    this.details = details;
  }
}

// 401 · 비밀번호 변경 필요는 화면이 아니라 여기서 한 번에 처리한다.
// 라우터 밖이라 navigate 를 쓸 수 없어서 main.jsx 가 이동 함수를 넣어 준다.
let onUnauthorized = () => {};
let onPasswordChangeRequired = () => {};

export function setAuthHandlers({ unauthorized, passwordChangeRequired }) {
  onUnauthorized = unauthorized;
  onPasswordChangeRequired = passwordChangeRequired;
}

const NETWORK_ERROR = { code: 'INTERNAL_ERROR', message: '잠시 후 다시 시도해 주세요' };

function buildUrl(path, params) {
  const url = new URL(BASE_URL + path, window.location.origin);
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') return;
      if (Array.isArray(value)) value.forEach((v) => url.searchParams.append(key, v));
      else url.searchParams.set(key, value);
    });
  }
  return url.pathname + url.search;
}

async function send(method, path, { params, body, raw = false } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let payload;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  let res;
  try {
    res = await fetch(buildUrl(path, params), { method, headers, body: payload });
  } catch {
    throw new ApiError({ status: 0, ...NETWORK_ERROR });
  }

  if (raw && res.ok) return res;

  let json = null;
  try {
    json = await res.json();
  } catch {
    // 본문이 JSON 이 아니다(프록시 오류 페이지 등)
  }

  if (json?.success) return json.data ?? null;

  const error = json?.error;
  if (!error || res.status >= 500) {
    throw new ApiError({ status: res.status, ...NETWORK_ERROR });
  }

  const apiError = new ApiError({ status: res.status, ...error });
  if (res.status === 401) {
    clearToken();
    onUnauthorized(apiError);
  } else if (error.code === 'AUTH_PASSWORD_CHANGE_REQUIRED') {
    onPasswordChangeRequired(apiError);
  }
  throw apiError;
}

export const api = {
  get: (path, params) => send('GET', path, { params }),
  post: (path, body, params) => send('POST', path, { body, params }),
  put: (path, body) => send('PUT', path, { body }),
  patch: (path, body) => send('PATCH', path, { body }),
  delete: (path, params) => send('DELETE', path, { params }),

  /** 파일 올리기(API 14장) — 성공하면 { id, ... } */
  upload: (file, purpose) => {
    const form = new FormData();
    form.append('file', file);
    if (purpose) form.append('purpose', purpose);
    return send('POST', '/files', { body: form });
  },

  /** 토큰이 필요한 파일 내려받기 — <img src> 에 바로 못 쓰므로 blob URL 로 바꾼다 */
  blobUrl: async (path) => {
    const res = await send('GET', path, { raw: true });
    return URL.createObjectURL(await res.blob());
  },
};
