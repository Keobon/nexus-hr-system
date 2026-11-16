// AI 비서 대화창(C2) — 문장으로 휴가 결재 초안을 만들고, 확인 카드에서 [올리기]를 눌러야 실제로 신청된다.
// 대화는 이 화면 메모리에만 둔다(저장하지 않음). 열 때마다 새 대화.
// 직원이 쓴 글 · 서버가 준 글은 React 기본 이스케이프로만 그린다(dangerouslySetInnerHTML 쓰지 않음).
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Drawer, Input } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { confirmLeaveDraft, createLeaveDraft, discardLeaveDraft } from '../../api/assistant';
import { meKey } from '../../api/auth';
import { errorMessage } from '../../constants/errors';
import LeaveDraftCard from './LeaveDraftCard';

const LEAVE_PAGE = { to: '/me/leaves', label: '휴가 신청 및 조회로 가기' };

/** API 오류 → 챗봇 말풍선. reset 이면 지금 초안을 버리고 새 대화로 */
function botReply(err) {
  // 503 은 api/client 가 INTERNAL_ERROR 로 바꿔 던지므로 상태 코드로 본다
  if (err?.status === 503 || err?.code === 'AI_UNAVAILABLE') {
    return { text: '지금은 AI를 쓸 수 없어요. 휴가 신청 및 조회 화면에서 직접 신청해 주세요.', link: LEAVE_PAGE, reset: true };
  }
  switch (err?.code) {
    case 'AI_RATE_LIMITED':
      return { text: '요청이 너무 많아요. 1분쯤 뒤에 다시 시도해 주세요.' };
    case 'AI_DRAFT_EXPIRED':
      return { text: '초안이 30분이 지나 만료됐어요. 처음부터 다시 말씀해 주세요.', reset: true };
    case 'AI_DRAFT_NOT_FOUND':
    case 'INVALID_STATE':
      return { text: '이 초안은 더 이상 쓸 수 없어요. 처음부터 다시 말씀해 주세요.', reset: true };
    default:
      return { text: errorMessage(err) };
  }
}

export default function AssistantPanel({ open, onClose, messages, push, onGo }) {
  const queryClient = useQueryClient();
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [draftId, setDraftId] = useState(null);
  const [activeCard, setActiveCard] = useState(null); // 버튼이 살아 있는 카드(마지막 초안)의 말풍선 ID
  const inputRef = useRef(null);
  const listRef = useRef(null);

  // 열 때마다 새 대화
  useEffect(() => {
    if (open) {
      setDraftId(null);
      setActiveCard(null);
      setInput('');
    }
  }, [open]);

  // 새 말풍선이 오면 맨 아래로
  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  const resetDraft = () => {
    setDraftId(null);
    setActiveCard(null);
  };

  const fail = (err) => {
    const reply = botReply(err);
    if (reply.reset) resetDraft();
    push({ role: 'bot', kind: 'text', text: reply.text, link: reply.link });
  };

  const send = async () => {
    const text = input.trim();
    if (!text || busy) return;
    setInput('');
    push({ role: 'user', kind: 'text', text });
    setBusy(true);
    try {
      const res = await createLeaveDraft({ message: text, ...(draftId ? { draftId } : {}) });
      setDraftId(res.draftId);
      if (res.status === 'NEED_INFO') {
        setActiveCard(null);
        push({ role: 'bot', kind: 'text', text: res.question || '조금 더 자세히 알려 주세요.' });
      } else {
        setActiveCard(push({ role: 'bot', kind: 'card', data: res }));
      }
    } catch (err) {
      fail(err);
    } finally {
      setBusy(false);
    }
  };

  const submit = async () => {
    if (!draftId || busy) return;
    setBusy(true);
    try {
      await confirmLeaveDraft(draftId);
      resetDraft();
      queryClient.invalidateQueries({ queryKey: ['leave-requests'] });
      queryClient.invalidateQueries({ queryKey: ['leave-balances'] });
      queryClient.invalidateQueries({ queryKey: meKey });
      push({
        role: 'bot',
        kind: 'text',
        text: '휴가 결재를 올렸습니다. 진행 상황은 휴가 신청 및 조회에서 볼 수 있어요.',
        link: LEAVE_PAGE,
      });
    } catch (err) {
      fail(err);
    } finally {
      setBusy(false);
    }
  };

  const edit = () => {
    push({ role: 'bot', kind: 'text', text: '바꿀 내용을 말씀해 주세요. 예) "날짜를 3/13으로", "오후 반차로"' });
    inputRef.current?.focus();
  };

  const discard = async () => {
    if (!draftId || busy) return;
    setBusy(true);
    try {
      await discardLeaveDraft(draftId);
    } catch {
      // 이미 만료 · 처리된 초안이어도 대화에서는 버린다
    } finally {
      resetDraft();
      setBusy(false);
      push({ role: 'bot', kind: 'text', text: '초안을 지웠어요. 다른 휴가가 필요하면 다시 말씀해 주세요.' });
    }
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      placement="right"
      size={400}
      title="AI 비서"
      className="as-drawer"
      footer={
        <div className="as-composer">
          <Input.TextArea
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onPressEnter={(e) => {
              if (!e.shiftKey && !e.nativeEvent.isComposing) {
                e.preventDefault();
                send();
              }
            }}
            maxLength={500}
            autoSize={{ minRows: 1, maxRows: 4 }}
            placeholder='예) "내일 오후 반차 쓸게요"'
            aria-label="AI 비서에게 보낼 문장"
          />
          <Button type="primary" icon={<SendOutlined />} onClick={send} loading={busy} disabled={!input.trim()} aria-label="보내기" />
        </div>
      }
    >
      <div className="as-list" ref={listRef} aria-live="polite">
        {messages.map((m) => {
          if (m.kind === 'card') {
            return (
              <div key={m.id} className="as-msg is-bot">
                <LeaveDraftCard
                  data={m.data}
                  active={m.id === activeCard}
                  busy={busy}
                  onSubmit={submit}
                  onEdit={edit}
                  onDiscard={discard}
                />
              </div>
            );
          }
          if (m.kind === 'notification' && m.done) {
            // 이미 처리된 결재(승인함에서 바로 승인 · 반려 등) — 회색, 이동 버튼 없음
            return (
              <div key={m.id} className="as-msg is-bot is-alert is-done">
                <p className="as-bubble">
                  <strong>{m.item.title}</strong>
                  {m.item.body && <span className="as-bubble-sub">{m.item.body}</span>}
                  <span>이미 처리된 결재예요.</span>
                </p>
              </div>
            );
          }
          if (m.kind === 'notification') {
            return (
              <div key={m.id} className="as-msg is-bot is-alert">
                <p className="as-bubble">
                  <strong>{m.item.title}</strong>
                  {m.item.body && <span className="as-bubble-sub">{m.item.body}</span>}
                  <span>확인해 보고 승인함으로 이동하세요.</span>
                </p>
                <Button size="small" type="primary" ghost onClick={() => onGo(m.item)}>
                  승인함으로 이동
                </Button>
              </div>
            );
          }
          return (
            <div key={m.id} className={`as-msg is-${m.role}`}>
              <p className="as-bubble">{m.text}</p>
              {m.link && (
                <Link to={m.link.to} className="as-link" onClick={onClose}>
                  {m.link.label}
                </Link>
              )}
            </div>
          );
        })}
        {busy && (
          <div className="as-msg is-bot">
            <p className="as-bubble as-typing">생각하는 중…</p>
          </div>
        )}
      </div>
    </Drawer>
  );
}
