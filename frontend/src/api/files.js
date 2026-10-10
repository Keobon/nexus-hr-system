// 파일(API 14장). 올리면 어디에도 연결되지 않은 fileId 가 생기고, 이후 API 가 그 fileId 를 연결한다.
import { api } from './client';

// purpose 별 허용 형식(API 14장 표) — 서버도 검사하지만 올리기 전에 막는다(프론트 가이드 2.5)
const IMAGE = ['image/jpeg', 'image/png'];
const DOCUMENT = [...IMAGE, 'application/pdf'];

export const FILE_PURPOSES = {
  RECEIPT: DOCUMENT,
  COMPANY_DOCUMENT: DOCUMENT,
  EMPLOYEE_DOCUMENT: DOCUMENT,
  PROFILE: IMAGE,
  LOGO: IMAGE,
};

export const MAX_FILE_BYTES = 10 * 1024 * 1024;

/** → { id, originalName, contentType, sizeBytes } */
export function uploadFile(file, purpose) {
  const form = new FormData();
  form.append('purpose', purpose);
  form.append('file', file);
  return api.postForm('/files', form);
}

/** → { blob, fileName } — 다 쓰면 URL.revokeObjectURL */
export const fetchFile = (fileId) => api.fetchBlob(`/files/${fileId}`);

/** 새 탭에서 열기(pdf · 이미지). 막히면 내려받기 */
export async function openFile(fileId) {
  const { blob, fileName } = await fetchFile(fileId);
  const url = URL.createObjectURL(blob);
  // 'noopener' 를 주면 브라우저가 늘 null 을 돌려줘서 막힌 것과 구분이 안 된다(blob URL 은 같은 출처라 괜찮다)
  const win = window.open(url, '_blank');
  if (!win) downloadUrl(url, fileName);
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

export async function downloadFile(fileId) {
  const { blob, fileName } = await fetchFile(fileId);
  const url = URL.createObjectURL(blob);
  downloadUrl(url, fileName);
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

function downloadUrl(url, fileName) {
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName ?? 'file';
  a.click();
}
