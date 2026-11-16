// 결재 알림 스트림(B2) — GET /api/me/notifications/stream(SSE)에 붙어 안 읽은 개수와 새 알림을 받는다.
// 기본 EventSource 는 Authorization 헤더를 못 보내서 fetch 기반 클라이언트(@microsoft/fetch-event-source)를 쓴다.
//
// - event: unread-count → 안 읽은 개수를 그 값으로 맞춘다(연결 · 재연결마다 한 번)
// - event: notification → onNotification(item)
// - 끊기면 1초 · 2초 · 4초 … 최대 30초 간격으로 다시 붙는다(지수 백오프)
// - 다시 붙으면 GET /me/notifications?unreadOnly=true 로 끊긴 사이 놓친 알림을 채운다 —
//   연결 직전에 커밋된 알림은 스트림이 놓칠 수 있다(서버 리뷰 참고 사항)
// - 이 훅을 쓰는 컴포넌트(AppLayout 안)가 사라지면(로그아웃 · 401) 연결을 끊는다
import { useCallback, useEffect, useRef, useState } from 'react';
import { EventStreamContentType, fetchEventSource } from '@microsoft/fetch-event-source';
import { getNotifications, NOTIFICATION_STREAM_URL, readNotification } from '../../api/notifications';
import { getToken } from '../../auth/token';

const MAX_DELAY = 30_000;

class RetriableError extends Error {}
class FatalError extends Error {}

/** 1초 · 2초 · 4초 … 30초, 몰려서 다시 붙지 않게 0–20% 흔들기 */
function backoff(attempt) {
  const base = Math.min(1000 * 2 ** (attempt - 1), MAX_DELAY);
  return Math.round(base * (1 + Math.random() * 0.2));
}

/**
 * @param onNotification (item, { live }) — live 는 스트림으로 바로 받은 것, false 는 재연결 뒤 목록으로 채운 것
 * @returns { unreadCount, markRead(item) } — markRead 는 읽음 처리 API 를 부르고 개수를 줄인다(실패해도 이동은 막지 않는다)
 */
export function useNotificationStream(onNotification) {
  const [unreadCount, setUnreadCount] = useState(0);
  const handler = useRef(onNotification);
  const seen = useRef(new Set()); // 이미 알린 알림 ID — 스트림 · 보충이 겹쳐도 한 번만
  const read = useRef(new Set()); // 이번 화면에서 읽음 처리한 알림 ID

  useEffect(() => {
    handler.current = onNotification;
  }, [onNotification]);

  useEffect(() => {
    const token = getToken();
    if (!token) return undefined;
    const ctrl = new AbortController();
    let opened = false;
    let attempt = 0;

    const deliver = (item, live) => {
      if (seen.current.has(item.id)) return;
      seen.current.add(item.id);
      if (live) setUnreadCount((c) => c + 1);
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

    fetchEventSource(NOTIFICATION_STREAM_URL, {
      signal: ctrl.signal,
      headers: { Authorization: `Bearer ${token}` },
      openWhenHidden: true, // 다른 탭에 있어도 알림을 받는다
      async onopen(res) {
        if (res.ok && res.headers.get('content-type')?.startsWith(EventStreamContentType)) {
          const first = !opened;
          opened = true;
          attempt = 0;
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
          if (ev.event === 'unread-count') setUnreadCount(Number(data.unreadCount) || 0);
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
    }).catch(() => {
      // 끝(FatalError) 또는 abort
    });

    return () => ctrl.abort();
  }, []);

  const markRead = useCallback(async (item) => {
    if (!item || item.read || read.current.has(item.id)) return;
    read.current.add(item.id);
    setUnreadCount((c) => Math.max(0, c - 1));
    try {
      await readNotification(item.id);
    } catch {
      // 읽음 처리에 실패해도 이동은 한다(다음 연결 때 개수가 다시 맞춰진다)
    }
  }, []);

  return { unreadCount, markRead };
}
