/**
 * 출퇴근 위치 — 브라우저 위치를 "시도만" 한다(프론트 가이드 3.2).
 * 거부 · 실패 · 3초 초과면 빈 객체를 돌려줘서 위치 없이 그대로 출퇴근한다.
 */
export function tryLocation(timeoutMs = 3000) {
  if (!navigator.geolocation) return Promise.resolve({});
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve({}), timeoutMs);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        clearTimeout(timer);
        resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude });
      },
      () => {
        clearTimeout(timer);
        resolve({});
      },
      { timeout: timeoutMs, maximumAge: 60_000 },
    );
  });
}
