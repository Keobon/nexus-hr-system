import { useEffect, useState } from 'react';
import { fetchFile } from '../../api/files';

/**
 * fileId → <img src> 에 쓸 blob URL. GET /files/{id} 는 토큰이 필요해서 주소를 바로 못 쓴다(프론트 가이드 2.5).
 * 프로필 사진 · 로고 · 영수증 미리보기에 쓴다. fileId 가 없거나 실패하면 null.
 */
export function useFileUrl(fileId) {
  const [url, setUrl] = useState(null);

  useEffect(() => {
    if (!fileId) {
      setUrl(null);
      return undefined;
    }
    let objectUrl = null;
    let cancelled = false;
    fetchFile(fileId)
      .then(({ blob }) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => !cancelled && setUrl(null));
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [fileId]);

  return url;
}
