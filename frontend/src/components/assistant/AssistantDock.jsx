// AI 비서(C2) + 결재 알림(B2) — AppLayout 에 한 번만 둔다. 로그인한 직원 누구나.
// 오른쪽 아래 버튼: 안 읽은 알림 개수 뱃지, 누르면 AI 비서 대화창.
// 새 알림: ① 뱃지 ② 토스트(누르면 그 화면으로) ③ 대화창이 열려 있으면 알림 말풍선.
// 알림을 누르면 읽음 처리 → 승인함 배지 · 목록을 새로 받고 → linkPath(/approvals?stepId=…)로 이동.
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { App, FloatButton } from 'antd';
import { CommentOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { meKey } from '../../api/auth';
import { notificationsKey } from '../../api/notifications';
import AssistantPanel from './AssistantPanel';
import { useNotificationStream } from './useNotificationStream';
import './assistant.css';

const GREETING =
  '안녕하세요! 휴가 신청을 도와드릴게요. 예) "3/12 병원 진료 때문에 연차 쓸게요". 내용을 확인한 뒤 [올리기]를 눌러야 실제로 신청돼요.';

export default function AssistantDock() {
  const { notification } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const openRef = useRef(false);
  const seq = useRef(0);
  const markReadRef = useRef(null);

  /** 말풍선 추가 → 그 말풍선 ID */
  const push = useCallback((m) => {
    seq.current += 1;
    const id = seq.current;
    setMessages((list) => [...list, { id, ...m }]);
    return id;
  }, []);

  const refreshApprovals = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: meKey }); // 사이드바 승인함 배지(todos.approvalsPending)
    queryClient.invalidateQueries({ queryKey: ['approvals'] });
    queryClient.invalidateQueries({ queryKey: notificationsKey });
  }, [queryClient]);

  /** 알림을 눌렀을 때 — 읽음 처리 후 이동. 서버가 만든 앱 안 주소(/로 시작)만 따라간다 */
  const go = useCallback(
    async (item) => {
      await markReadRef.current?.(item);
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
      if (openRef.current) push({ role: 'bot', kind: 'notification', item });
    },
    [notification, go, push, refreshApprovals],
  );

  const { unreadCount, markRead } = useNotificationStream(onNotification);
  useEffect(() => {
    markReadRef.current = markRead;
  }, [markRead]);

  const openPanel = () => {
    seq.current = 0;
    setMessages([]);
    push({ role: 'bot', kind: 'text', text: GREETING });
    openRef.current = true;
    setOpen(true);
  };

  const closePanel = () => {
    openRef.current = false;
    setOpen(false);
  };

  return (
    <>
      <FloatButton
        type="primary"
        icon={<CommentOutlined />}
        badge={{ count: unreadCount, overflowCount: 99 }}
        tooltip={unreadCount ? `AI 비서 · 안 읽은 알림 ${unreadCount}건` : 'AI 비서'}
        aria-label={unreadCount ? `AI 비서 열기, 안 읽은 알림 ${unreadCount}건` : 'AI 비서 열기'}
        onClick={openPanel}
        className="as-fab"
      />
      <AssistantPanel
        open={open}
        onClose={closePanel}
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
