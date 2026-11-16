// 결재 알림 스트림(B2) — GET /api/me/notifications/stream(SSE)에 붙어 새 알림을 받는다.
// 기본 EventSource 는 Authorization 헤더를 못 보내서 fetch 기반 클라이언트(@microsoft/fetch-event-source)를 쓴다.
// 안 읽은 개수(뱃지)는 이 훅이 세지 않는다 — AssistantDock 이 서버의 안 읽은 알림 목록(react-query)으로 보여 준다.
// 승인함에서 바로 처리해 알림을 읽음으로 바꾼 것까지 숫자에 맞게 반영하려고.
//
// - event: unread-count → onUnreadChanged() (연결 · 재연결마다 한 번 — 목록을 다시 받는다)
// - event: notification → onNotification(item)
// - 끊기면 1초 · 2초 · 4초 … 최대 30초 간격으로 다시 붙는다(지수 백오프)
// - 서버는 25초마다 heartbeat(:ping)를 보낸다. 60초 동안 아무 바이트도 안 오면 끊긴 것으로 보고 다시 붙는다 —
//   서버가 재시작돼도 중간 프록시(개발 서버 등)가 브라우저 쪽 연결을 닫지 않아 조용히 멈춘 채 남는 경우가 있다
// - 다시 붙으면 GET /me/notifications?unreadOnly=true 로 끊긴 사이 놓친 알림을 채운다 —
//   연결 직전에 커밋된 알림은 스트림이 놓칠 수 있다(서버 리뷰 참고 사항)
// - 이 훅을 쓰는 컴포넌트(AppLayout 안)가 사라지면(로그아웃 · 401) 연결을 끊는다
import { useEffect, useRef } from 'react';
import { EventStreamContentType, fetchEventSource } from '@microsoft/fetch-event-source';
import { getNotifications, NOTIFICATION_STREAM_URL } from '../../api/notifications';
import { getToken } from '../../auth/token';

const MAX_DELAY = 30_000;
const STALE_MS = 60_000; // heartbeat 25초 × 2 + 여유
const WATCHDOG_MS = 10_000;

class RetriableError extends Error {}
class FatalError extends Error {}

/** 1초 · 2초 · 4초 … 30초, 몰려서 다시 붙지 않게 0–20% 흔들기 */
function backoff(attempt) {
  const base = Math.min(1000 * 2 ** (attempt - 1), MAX_DELAY);
  return Math.round(base * (1 + Math.random() * 0.2));
}

/**
 * @param onNotification (item, { live }) — live 는 스트림으로 바로 받은 것, false 는 재연결 뒤 목록으로 채운 것
 * @param onUnreadChanged () — 연결될 때마다(서버가 안 읽은 개수를 보낼 때) 부른다
 */
export function useNotificationStream(onNotification, onUnreadChanged) {
  const handler = useRef(onNotification);
  const unreadHandler = useRef(onUnreadChanged);
  const seen = useRef(new Set()); // 이미 알린 알림 ID — 스트림 · 보충이 겹쳐도 한 번만

  useEffect(() => {
    handler.current = onNotification;
    unreadHandler.current = onUnreadChanged;
  }, [onNotification, onUnreadChanged]);

  useEffect(() => {
    const token = getToken();
    if (!token) return undefined;
    let stopped = false;
    let session = null; // 지금 연결의 AbortController — 끊긴 연결을 버릴 때 abort
    let retryTimer = null;
    let lastActivity = Date.now();
    let opened = false;
    let attempt = 0;

    // 받은 바이트마다(주석 줄 heartbeat 포함) 마지막 활동 시각을 남긴다 — 라이브러리는 주석 줄을 알려 주지 않는다
    const trackingFetch = async (input, init) => {
      const res = await fetch(input, init);
      if (!res.body) return res;
      const tap = new TransformStream({
        transform(chunk, controller) {
          lastActivity = Date.now();
          controller.enqueue(chunk);
        },
      });
      return new Response(res.body.pipeThrough(tap), { status: res.status, statusText: res.statusText, headers: res.headers });
    };

    const deliver = (item, live) => {
      if (seen.current.has(item.id)) return;
      seen.current.add(item.id);
      handler.current?.(item, { live });
    };

    // 처음 연결이면 지금 안 읽은 알림을 "본 것"으로만 기록하고(토스트 없음), 다시 붙은 거면 놓친 것을 알린다
    const backfill = async (first) => {
      try {
        const page = await getNotifications({ unreadOnly: true, size: 20 });
        const items = page?.content ?? [];
        if (first) {
          items.forEach((it) => seen.current.add(it.id));
        } else {
          [...items].reverse().forEach((it) => deliver(it, false));
        }
      } catch {
        // 목록을 못 받아도 스트림은 계속 쓴다
      }
    };

    const connect = () => {
      if (stopped) return;
      session = new AbortController();
      lastActivity = Date.now();
      fetchEventSource(NOTIFICATION_STREAM_URL, {
        signal: session.signal,
        fetch: trackingFetch,
        headers: { Authorization: `Bearer ${token}` },
        openWhenHidden: true, // 다른 탭에 있어도 알림을 받는다
        async onopen(res) {
          if (res.ok && res.headers.get('content-type')?.startsWith(EventStreamContentType)) {
            const first = !opened;
            opened = true;
            attempt = 0;
            lastActivity = Date.now();
            backfill(first);
            return;
          }
          // 로그인 만료 · 권한 문제는 다시 붙어도 같다 — 다른 API 호출이 로그인 화면으로 보낸다
          if (res.status === 401 || res.status === 403) throw new FatalError();
          throw new RetriableError();
        },
        onmessage(ev) {
          if (!ev.data) return;
          try {
            const data = JSON.parse(ev.data);
            if (ev.event === 'unread-count') unreadHandler.current?.();
            else if (ev.event === 'notification') deliver(data, true);
          } catch {
            // 모양이 다른 이벤트는 무시
          }
        },
        onclose() {
          // 서버가 끊음(30분 타임아웃 · 재시작) → 다시 붙는다
          throw new RetriableError();
        },
        onerror(err) {
          if (err instanceof FatalError) throw err;
          attempt += 1;
          return backoff(attempt);
        },
      }).catch((err) => {
        // FatalError 면 더 붙지 않는다. abort(연결 버림 · 화면 떠남)는 여기로 오지 않는다
        if (err instanceof FatalError) stopped = true;
      });
    };

    // 조용히 멈춘 연결 감시 — 60초 동안 바이트가 없으면 버리고 백오프 뒤 새로 붙는다
    const watchdog = setInterval(() => {
      if (stopped || !session || Date.now() - lastActivity < STALE_MS) return;
      session.abort();
      session = null;
      attempt += 1;
      retryTimer = setTimeout(connect, backoff(attempt));
    }, WATCHDOG_MS);

    connect();

    return () => {
      stopped = true;
      clearInterval(watchdog);
      clearTimeout(retryTimer);
      session?.abort();
    };
  }, []);
}
