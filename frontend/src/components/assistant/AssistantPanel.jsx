// AI 비서 대화창(C2) — 문장으로 휴가 · 연장근무 · 출장 결재 초안을 만들고, 확인 카드에서 [올리기]를 눌러야 실제로 신청된다.
// 대화는 이 화면 메모리에만 둔다(저장하지 않음). 열 때마다 새 대화, 입력바 [+]로도 새 대화(session 이 바뀐다).
// 오른쪽 아래에 떠 있는 패널(캐릭터 '별이') — 닫혀도 내용은 남기고 숨기기만 한다(Drawer 때와 같게). Esc 로 닫는다.
// 업무 종류(workType): 칩으로 고르면 그 업무, 칩 없이 바로 쓰면 POST /me/ai/route 로 고른 뒤 같은 문장으로 그 업무 초안 API 를 부른다.
// 한 업무 진행 중에는 다른 업무 얘기를 해도 지금 업무 API 로 보낸다 — 바꾸려면 [처음으로]. 업무별 차이는 아래 WORK 표 한 곳에만 둔다.
// 신청을 올리거나 그만두면 "또 도와드릴 일이 있을까요?" + 칩으로 돌아오고, 진행 중에는 입력바 위 [처음으로]로 언제든 돌아간다(만들던 초안은 버림).
// 직원이 쓴 글 · 서버가 준 글은 React 기본 이스케이프로만 그린다(dangerouslySetInnerHTML 쓰지 않음).
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Input } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import {
  confirmLeaveDraft,
  confirmOvertimeDraft,
  confirmTripDraft,
  createLeaveDraft,
  createOvertimeDraft,
  createTripDraft,
  discardLeaveDraft,
  discardOvertimeDraft,
  discardTripDraft,
  routeMessage,
} from '../../api/assistant';
import { meKey } from '../../api/auth';
import { overtimeRoot, tripRoot } from '../../api/workRequests';
import byeori from '../../assets/byeori.png';
import { useMe } from '../../auth/useMe';
import { errorMessage } from '../../constants/errors';
import { CalendarIcon, ClockIcon, CloseIcon, HomeIcon, PlaneIcon, PlusIcon, SendIcon } from './icons';
import LeaveDraftCard from './LeaveDraftCard';
import OvertimeDraftCard from './OvertimeDraftCard';
import TripDraftCard from './TripDraftCard';

const LEAVE_PAGE = { to: '/me/leaves', label: '휴가 신청 및 조회로 가기', page: '휴가 신청 및 조회' };
const WORK_PAGE = { to: '/me/work-requests', label: '연장근무 · 출장 신청으로 가기', page: '연장근무 · 출장 신청' };
const MENU_AGAIN = '또 도와드릴 일이 있을까요?';

/**
 * 업무별 설정 — 초안 API(create · confirm · discard) · 확인 카드 · 칩을 눌렀을 때 대화 · 고칠 때 예시 · 올린 뒤 문구 · 이동 링크 · 새로 받을 목록.
 * 화면 곳곳에 if 를 두지 않고 workType 으로 여기서 고른다
 */
const WORK = {
  LEAVE: {
    create: createLeaveDraft,
    confirm: confirmLeaveDraft,
    discard: discardLeaveDraft,
    Card: LeaveDraftCard,
    chip: { say: '휴가 신청할게요', ask: '언제, 어떤 휴가가 필요하세요?' },
    editHint: '바꿀 내용을 말씀해 주세요. 예) "날짜를 3/13으로", "오후 반차로"',
    done: '휴가 결재를 올렸습니다. 진행 상황은 휴가 신청 및 조회에서 볼 수 있어요.',
    link: LEAVE_PAGE,
    invalidate: [['leave-requests'], ['leave-balances']],
  },
  OVERTIME: {
    create: createOvertimeDraft,
    confirm: confirmOvertimeDraft,
    discard: discardOvertimeDraft,
    Card: OvertimeDraftCard,
    chip: { say: '연장근무 신청할게요', ask: '언제, 몇 시부터 몇 시까지, 어떤 일로 연장근무 하세요?', ex: '예) “오늘 7시부터 10시까지 배포 대응”' },
    editHint: '바꿀 내용을 말씀해 주세요. 예) "20시부터 23시까지로", "날짜를 11/26으로"',
    done: '연장근무 결재를 올렸어요.',
    link: WORK_PAGE,
    invalidate: [overtimeRoot],
  },
  BUSINESS_TRIP: {
    create: createTripDraft,
    confirm: confirmTripDraft,
    discard: discardTripDraft,
    Card: TripDraftCard,
    chip: { say: '출장 신청할게요', ask: '어디로, 언제, 무슨 일로 출장 가세요?', ex: '예) “11/27~28 부산 고객사 미팅”' },
    editHint: '바꿀 내용을 말씀해 주세요. 예) "11/28까지로", "예상 비용 50만 원"',
    done: '출장 결재를 올렸어요.',
    link: WORK_PAGE,
    invalidate: [tripRoot],
  },
};

/** API 오류 → 챗봇 말풍선. reset 이면 지금 초안을 버리고 새 대화로. link 는 지금 업무의 신청 화면(업무를 모르면 휴가) */
function botReply(err, link = LEAVE_PAGE) {
  // 503 은 api/client 가 INTERNAL_ERROR 로 바꿔 던지므로 상태 코드로 본다
  if (err?.status === 503 || err?.code === 'AI_UNAVAILABLE') {
    return { text: `지금은 AI를 쓸 수 없어요. ${link.page} 화면에서 직접 신청해 주세요.`, link, reset: true };
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

/** 알림 본문 "조현우 · 연차 · 11/25(수) 1일" → 미니 카드 { initial, who, when }. 모양이 다르면 본문 그대로 */
function noticeParts(item) {
  const parts = (item.body ?? '').split(' · ');
  if (parts.length < 2) return { initial: (item.body ?? '?').slice(0, 1), who: item.body ?? '', when: null };
  return {
    initial: parts[0].slice(0, 1),
    who: `${parts[0]} · ${parts[1]}`,
    when: parts.length > 2 ? parts.slice(2).join(' · ').replace(/\s(\S+일)$/, ' · $1') : null,
  };
}

/** 알림 제목 "휴가 결재가 올라왔습니다" → 말풍선 말투 "휴가 결재가 올라왔어요." */
const noticeTitle = (item) => (item.title ?? '결재가 올라왔습니다').replace(/올라왔습니다\.?$/, '올라왔어요.');

function NoticeCard({ item }) {
  const n = noticeParts(item);
  return (
    <span className="as-notice">
      <span className="as-notice-initial" aria-hidden="true">
        {n.initial}
      </span>
      <span className="as-notice-text">
        <b>{n.who}</b>
        {n.when && <span>{n.when}</span>}
      </span>
    </span>
  );
}

/** 봇 말풍선 행 — 왼쪽 별이 아바타(이름은 헤더에 있어 aria-hidden) */
function BotRow({ className = '', wide = false, children }) {
  return (
    <div className={`as-row is-bot${wide ? ' is-wide' : ''}`}>
      <span className="as-ava" aria-hidden="true">
        <img src={byeori} alt="" />
      </span>
      <div className={`as-msg is-bot ${className}`}>{children}</div>
    </div>
  );
}

/** 빠른 선택 칩 — 첫 인사 · "또 도와드릴 일이 있을까요?" · "어떤 결재인지 골라 주세요." 아래 */
function QuickChips({ onPick }) {
  return (
    <div className="as-chips-quick" role="group" aria-label="빠른 선택">
      <button type="button" className="as-quick" onClick={() => onPick('LEAVE')}>
        <CalendarIcon />
        휴가 신청
      </button>
      <button type="button" className="as-quick" onClick={() => onPick('OVERTIME')}>
        <ClockIcon />
        연장근무 신청
      </button>
      <button type="button" className="as-quick" onClick={() => onPick('BUSINESS_TRIP')}>
        <PlaneIcon />
        출장 신청
      </button>
    </div>
  );
}

/** 사용자 말풍선 행 — 오른쪽 이니셜 원 */
function UserRow({ initial, children }) {
  return (
    <div className="as-row is-user">
      <div className="as-msg is-user">{children}</div>
      <span className="as-ava is-me" aria-hidden="true">
        {initial}
      </span>
    </div>
  );
}

export default function AssistantPanel({ open, session, onClose, onNewChat, messages, push, onGo }) {
  const queryClient = useQueryClient();
  const { me } = useMe();
  const myName = me?.employee?.name ?? '';
  const myInitial = myName.slice(0, 1) || '나';
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [draftId, setDraftId] = useState(null);
  const [workType, setWorkType] = useState(null); // LEAVE · OVERTIME · BUSINESS_TRIP · null(아직 모름 — 다음 문장은 route 로)
  const [activeCard, setActiveCard] = useState(null); // 버튼이 살아 있는 카드(마지막 초안)의 말풍선 ID
  const inputRef = useRef(null);
  const listRef = useRef(null);
  const work = WORK[workType] ?? null;
  // 칩은 마지막 "무엇이 필요하세요?"(첫 인사 · 메뉴) 말풍선 아래에만 — 그 뒤로 직원이 말하면(칩 · 입력) 숨기고 [처음으로]를 보인다
  const menuIdx = messages.reduce((at, m, i) => (m.kind === 'greeting' || m.kind === 'menu' ? i : at), -1);
  const atMenu = menuIdx >= 0 && !messages.slice(menuIdx + 1).some((m) => m.role === 'user');
  const showChips = (m) => atMenu && !busy && m.id === messages[menuIdx]?.id;

  // 열 때마다(그리고 [+] 새 대화마다) 새 대화
  useEffect(() => {
    if (open) {
      setDraftId(null);
      setWorkType(null);
      setActiveCard(null);
      setInput('');
      inputRef.current?.focus();
    }
  }, [open, session]);

  // Esc 로 닫기(Drawer 때와 같게)
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  // 새 말풍선이 오면 맨 아래로
  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  const resetDraft = () => {
    setDraftId(null);
    setWorkType(null);
    setActiveCard(null);
  };

  /** 만들던 초안을 서버에서도 버린다 — 실패해도 무시(이미 만료 · 처리됐거나, 30분 뒤 만료) */
  const dropDraft = () => {
    if (draftId && work) work.discard(draftId).catch(() => {});
  };

  const fail = (err) => {
    const reply = botReply(err, work?.link);
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
      // 칩으로 업무가 정해졌으면 그대로, 아니면 먼저 어떤 결재인지 고른다(사용자에게는 한 번 보낸 것)
      let type = workType;
      if (!type) {
        const routed = (await routeMessage({ message: text }))?.workType;
        if (!WORK[routed]) {
          push({ role: 'bot', kind: 'menu', text: '어떤 결재인지 골라 주세요.' });
          return;
        }
        type = routed;
        setWorkType(routed);
      }
      const res = await WORK[type].create({ message: text, ...(draftId ? { draftId } : {}) });
      setDraftId(res.draftId);
      if (res.status === 'NEED_INFO') {
        setActiveCard(null);
        push({ role: 'bot', kind: 'text', text: res.question || '조금 더 자세히 알려 주세요.' });
      } else {
        setActiveCard(push({ role: 'bot', kind: 'card', workType: type, data: res }));
      }
    } catch (err) {
      fail(err);
    } finally {
      setBusy(false);
    }
  };

  const submit = async () => {
    if (!draftId || !work || busy) return;
    setBusy(true);
    try {
      await work.confirm(draftId);
      resetDraft();
      [...work.invalidate, meKey].forEach((queryKey) => queryClient.invalidateQueries({ queryKey }));
      push({ role: 'bot', kind: 'text', text: work.done, link: work.link });
      push({ role: 'bot', kind: 'menu', text: MENU_AGAIN });
    } catch (err) {
      fail(err);
    } finally {
      setBusy(false);
    }
  };

  const edit = () => {
    push({ role: 'bot', kind: 'text', text: (work ?? WORK.LEAVE).editHint });
    inputRef.current?.focus();
  };

  const discard = async () => {
    if (!draftId || !work || busy) return;
    setBusy(true);
    try {
      await work.discard(draftId);
    } catch {
      // 이미 만료 · 처리된 초안이어도 대화에서는 버린다
    } finally {
      resetDraft();
      setBusy(false);
      push({ role: 'bot', kind: 'text', text: '초안을 지웠어요.' });
      push({ role: 'bot', kind: 'menu', text: MENU_AGAIN });
    }
  };

  /** [처음으로] — 만들던 초안은 서버에서도 버리고 메뉴로 */
  const goHome = () => {
    if (busy) return;
    dropDraft();
    resetDraft();
    push({ role: 'bot', kind: 'menu', text: '처음으로 돌아왔어요. 무엇이 필요하세요?' });
  };

  /** 입력바 [+] — 만들던 초안을 버리고 새 대화(첫 화면) */
  const newChat = () => {
    dropDraft();
    onNewChat();
  };

  /** 칩 — 업무를 정하고 묻기만 한다. 다음 입력부터 그 업무의 초안 API(route 는 부르지 않는다) */
  const pick = (type) => {
    const { chip } = WORK[type];
    resetDraft();
    setWorkType(type);
    push({ role: 'user', kind: 'text', text: chip.say });
    push({ role: 'bot', kind: 'text', text: chip.ask, ex: chip.ex });
    inputRef.current?.focus();
  };

  return (
    <section className="as-panel" role="dialog" aria-label="AI 비서 별이" hidden={!open}>
      <header className="as-head">
        <span className="as-avatar" aria-hidden="true">
          <img src={byeori} alt="" />
        </span>
        <strong className="as-name">별이</strong>
        <button type="button" className="as-close" onClick={onClose} aria-label="AI 비서 닫기">
          <CloseIcon />
        </button>
      </header>

      <div className="as-list" ref={listRef} aria-live="polite">
        {messages.map((m) => {
          if (m.kind === 'greeting') {
            return (
              <div key={m.id} className="as-greeting">
                <BotRow>
                  <p className="as-bubble">
                    {`안녕하세요${myName ? `, ${myName}님` : ''}! 무엇을 도와드릴까요?\n아래에서 고르거나, 그냥 말로 하셔도 돼요.`}
                    <span className="as-bubble-ex">예) “내일 오후 반차 쓸게요” · “11/25 병원 진료 때문에 연차”</span>
                  </p>
                </BotRow>
                {showChips(m) && <QuickChips onPick={pick} />}
              </div>
            );
          }
          if (m.kind === 'menu') {
            return (
              <div key={m.id} className="as-greeting">
                <BotRow>
                  <p className="as-bubble">{m.text}</p>
                </BotRow>
                {showChips(m) && <QuickChips onPick={pick} />}
              </div>
            );
          }
          if (m.kind === 'card') {
            const Card = WORK[m.workType]?.Card ?? LeaveDraftCard;
            return (
              <BotRow key={m.id} className="is-card" wide>
                <Card data={m.data} active={m.id === activeCard} busy={busy} onSubmit={submit} onEdit={edit} onDiscard={discard} />
              </BotRow>
            );
          }
          if (m.kind === 'notification' && m.done) {
            // 이미 처리된 결재(승인함에서 바로 승인 · 반려 등) — 회색, 이동 버튼 없음
            return (
              <BotRow key={m.id} className="is-alert is-done">
                <div className="as-bubble">
                  <span>{`${noticeTitle(m.item)} 이미 처리된 결재예요.`}</span>
                  {m.item.body && <NoticeCard item={m.item} />}
                </div>
              </BotRow>
            );
          }
          if (m.kind === 'notification') {
            return (
              <BotRow key={m.id} className="is-alert">
                <div className="as-bubble">
                  <span>{`${noticeTitle(m.item)} 확인해 보고 승인함으로 이동하세요.`}</span>
                  {m.item.body && <NoticeCard item={m.item} />}
                  <button type="button" className="as-pill" onClick={() => onGo(m.item)}>
                    승인함으로 이동
                  </button>
                </div>
              </BotRow>
            );
          }
          const body = (
            <>
              <p className="as-bubble">
                {m.text}
                {m.ex && <span className="as-bubble-ex">{m.ex}</span>}
              </p>
              {m.link && (
                <Link to={m.link.to} className="as-link" onClick={onClose}>
                  {m.link.label}
                </Link>
              )}
            </>
          );
          return m.role === 'user' ? (
            <UserRow key={m.id} initial={myInitial}>
              {body}
            </UserRow>
          ) : (
            <BotRow key={m.id}>{body}</BotRow>
          );
        })}
        {busy && (
          <BotRow>
            <p className="as-bubble as-typing">생각하는 중…</p>
          </BotRow>
        )}
      </div>

      {!atMenu && (
        <div className="as-home-row">
          <button type="button" className="as-home" onClick={goHome} disabled={busy}>
            <HomeIcon />
            처음으로
          </button>
        </div>
      )}

      <div className="as-composer">
        <button type="button" className="as-round is-new" onClick={newChat} aria-label="새 대화">
          <PlusIcon />
        </button>
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
          placeholder="무엇을 도와드릴까요?"
          aria-label="AI 비서에게 보낼 문장"
          className="as-input"
        />
        <button type="button" className="as-round is-send" onClick={send} disabled={!input.trim() || busy} aria-busy={busy} aria-label="보내기">
          <SendIcon />
        </button>
      </div>
    </section>
  );
}
