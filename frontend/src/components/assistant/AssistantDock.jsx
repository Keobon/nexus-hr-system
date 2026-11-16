// AI 비서(C2) + 결재 알림(B2) — AppLayout 에 한 번만 둔다. 로그인한 직원 누구나.
// 오른쪽 아래 버튼(캐릭터 '별이'): 안 읽은 알림 개수 뱃지(서버의 안 읽은 알림 목록 기준), 누르면 AI 비서 대화창(떠 있는 패널) — 열려 있으면 닫는다.
// 새 알림: ① 뱃지 ② 토스트(누르면 그 화면으로) ③ 대화창이 열려 있으면 알림 말풍선.
// 대화창을 열면 그때까지 안 읽은 알림도 말풍선으로 먼저 보여 준다(뱃지 숫자와 대화창 내용이 맞게).
// 그중 이미 처리된 결재(승인함에서 바로 승인 · 반려, 신청 철회 등으로 내 승인 대기에 없는 건)는
// 읽음으로 정리하고 회색 말풍선("이미 처리된 결재예요", 이동 버튼 없음)으로 보여 준다.
// 알림을 누르면 읽음 처리 → 승인함 배지 · 목록을 새로 받고 → linkPath(/approvals?stepId=…)로 이동.
import { useCallback, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { App, Badge } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getInboxPage } from '../../api/approvals';
import { meKey } from '../../api/auth';
import { fetchAllPages } from '../../api/employees';
import { getUnreadNotifications, notificationsKey, readNotification, unreadNotificationsKey } from '../../api/notifications';
import byeori from '../../assets/byeori.png';
import AssistantPanel from './AssistantPanel';
import { useNotificationStream } from './useNotificationStream';
import './assistant.css';

const GREETING =
  '안녕하세요! 휴가 신청을 도와드릴게요. 예) "내일 오후 반차 쓸게요", "11/25 병원 진료 때문에 연차". 내용을 확인한 뒤 [올리기]를 눌러야 실제로 신청돼요.';

export default function AssistantDock() {
  const { notification } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [session, setSession] = useState(0); // 새 대화마다 +1 — 대화창이 초안 · 입력을 비운다(열 때 · [+] 새 대화)
  const openRef = useRef(false);
  const seq = useRef(0);
  const shown = useRef(new Set()); // 이번 대화에 이미 말풍선으로 띄운 알림 ID — 목록 · 스트림이 겹쳐도 한 번만

  const unread = useQuery({ queryKey: unreadNotificationsKey, queryFn: getUnreadNotifications });
  const unreadCount = unread.data?.totalElements ?? 0;

  /** 말풍선 추가 → 그 말풍선 ID */
  const push = useCallback((m) => {
    seq.current += 1;
    const id = seq.current;
    setMessages((list) => [...list, { id, ...m }]);
    return id;
  }, []);

  const refreshUnread = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: notificationsKey });
  }, [queryClient]);

  const refreshApprovals = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: meKey }); // 사이드바 승인함 배지(todos.approvalsPending)
    queryClient.invalidateQueries({ queryKey: ['approvals'] });
    refreshUnread();
  }, [queryClient, refreshUnread]);

  /** 알림을 눌렀을 때 — 읽음 처리 후 이동. 서버가 만든 앱 안 주소(/로 시작)만 따라간다 */
  const go = useCallback(
    async (item) => {
      if (!item.read) {
        try {
          await readNotification(item.id);
        } catch {
          // 읽음 처리에 실패해도 이동은 한다
        }
      }
      refreshApprovals();
      if (typeof item.linkPath === 'string' && item.linkPath.startsWith('/') && !item.linkPath.startsWith('//')) {
        navigate(item.linkPath);
      }
    },
    [navigate, refreshApprovals],
  );

  const onNotification = useCallback(
    (item) => {
      refreshApprovals();
      const key = `notification-${item.id}`;
      notification.info({
        key,
        title: item.title,
        description: item.body,
        duration: 8,
        className: 'as-toast',
        onClick: () => {
          notification.destroy(key);
          go(item);
        },
      });
      if (openRef.current && !shown.current.has(item.id)) {
        shown.current.add(item.id);
        push({ role: 'bot', kind: 'notification', item });
      }
    },
    [notification, go, push, refreshApprovals],
  );

  useNotificationStream(onNotification, refreshUnread);

  const openPanel = async () => {
    setSession((n) => n + 1);
    seq.current = 0;
    shown.current = new Set();
    setMessages([]);
    push({ role: 'bot', kind: 'text', text: GREETING });
    openRef.current = true;
    setOpen(true);

    // 열기 전에 와 있던 안 읽은 알림 — 지금도 내 승인 대기에 있는지 승인함과 맞춰 본다
    try {
      const [page, inbox] = await Promise.all([getUnreadNotifications(), fetchAllPages(getInboxPage, {})]);
      const pending = new Set((inbox ?? []).map((it) => it.stepId));
      const items = (page?.content ?? []).filter((it) => !shown.current.has(it.id));
      if (!openRef.current || items.length === 0) return;
      const isDone = (it) => it.approvalStepId != null && !pending.has(it.approvalStepId);
      const done = items.filter(isDone);
      const waiting = items.length - done.length;

      push({
        role: 'bot',
        kind: 'text',
        text:
          waiting > 0
            ? `확인할 결재 알림이 ${waiting}건 있어요.${done.length ? ` (이미 처리된 ${done.length}건은 회색으로 표시했어요)` : ''}`
            : `새 결재 알림 ${done.length}건은 모두 이미 처리된 결재예요.`,
      });
      [...items].reverse().forEach((item) => {
        shown.current.add(item.id);
        push({ role: 'bot', kind: 'notification', item, done: isDone(item) });
      });

      // 이미 처리된 결재의 알림은 읽음으로 정리 → 뱃지에서 빠진다. 열려 있는 승인함 목록 · 배지도 새로 받는다
      if (done.length) {
        await Promise.all(done.map((it) => readNotification(it.id).catch(() => null)));
        refreshApprovals();
      }
    } catch {
      // 목록을 못 받아도 대화는 쓸 수 있다
    }
  };

  const closePanel = () => {
    openRef.current = false;
    setOpen(false);
  };

  return (
    <>
      <button
        type="button"
        className={`as-fab${open ? ' is-open' : ''}`}
        title={unreadCount ? `AI 비서 · 안 읽은 알림 ${unreadCount}건` : 'AI 비서'}
        aria-label={open ? 'AI 비서 닫기' : unreadCount ? `AI 비서 열기, 안 읽은 알림 ${unreadCount}건` : 'AI 비서 열기'}
        aria-expanded={open}
        onClick={open ? closePanel : openPanel}
      >
        <Badge count={unreadCount} overflowCount={99} className="as-fab-badge">
          <img src={byeori} alt="" className="as-fab-img" />
        </Badge>
      </button>
      <AssistantPanel
        open={open}
        session={session}
        onClose={closePanel}
        onNewChat={openPanel}
        messages={messages}
        push={push}
        onGo={(item) => {
          closePanel();
          go(item);
        }}
      />
    </>
  );
}
