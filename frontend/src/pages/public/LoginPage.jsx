import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { App, Select } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { login, meKey } from '../../api/auth';
import { setToken } from '../../auth/token';
import { nextPath } from '../../auth/nextPath';
import BrandLogo from '../../components/brand/BrandLogo';
import { errorMessage } from '../../constants/errors';
import iconClock from '../../assets/figma/icon-clock.svg';
import iconMoney from '../../assets/figma/icon-money.svg';
import iconCalendar from '../../assets/figma/icon-calendar.svg';
import iconPeople from '../../assets/figma/icon-people.svg';
import './auth.css';

const FEATURES = [
  { icon: iconClock, title: '출퇴근 및 근퇴 관리', sub: '실시간 출결 체크 및 정정' },
  { icon: iconMoney, title: '급여명세서 셀프 조회', sub: '월별 지급 내역 및 이력 확인' },
  { icon: iconCalendar, title: '휴가 & 결재 워크플로우', sub: '잔여 연차 확인 및 결재 승인' },
  { icon: iconPeople, title: '조직도 & 인사 발령', sub: '전사 부서 트리 및 발령 추적' },
];

// 개발 서버에서만 보이는 데모 계정(seed_demo.sql). 비밀번호는 모두 Passw0rd!
const DEMO_ACCOUNTS = [
  { value: 'taehyun.lim@nexuslabs.example', label: '임태현 — 신청자(직원)' },
  { value: 'seoyeon.yoon@nexuslabs.example', label: '윤서연 — 1단계 승인(팀장)' },
  { value: 'haneul.kang@nexuslabs.example', label: '강하늘 — 2단계 승인(본부장)' },
  { value: 'sua.jung@nexuslabs.example', label: '정수아 — 인사 담당' },
  { value: 'admin@nexuslabs.example', label: '이지원 — 최고 관리자' },
];

// 로그인 실패 문구는 Figma(Auth_Login_Fail) 그대로 — 잠금 안내를 같이 보여 준다
const LOGIN_MESSAGES = {
  AUTH_INVALID_CREDENTIALS: '이메일 또는 비밀번호가 일치하지 않습니다. (5회 실패 시 계정 잠금)',
};

const SAVED_EMAIL_KEY = 'nexus.savedEmail';

function readSavedEmail() {
  try {
    return localStorage.getItem(SAVED_EMAIL_KEY) ?? '';
  } catch {
    return '';
  }
}

function writeSavedEmail(email) {
  try {
    if (email) localStorage.setItem(SAVED_EMAIL_KEY, email);
    else localStorage.removeItem(SAVED_EMAIL_KEY);
  } catch {
    // 저장소를 못 쓰면 기억하지 않는다
  }
}

/** 로그인(F-AUTH-01) — Figma Auth_Login · Auth_Login_Fail */
export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { modal } = App.useApp();
  const initialEmail = location.state?.email ?? readSavedEmail();
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(!!readSavedEmail());
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState(location.state?.reason ?? null);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim() || !password) {
      setError('이메일과 비밀번호를 입력해 주세요.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const { token, next } = await login(email.trim(), password);
      writeSavedEmail(remember ? email.trim() : null);
      setToken(token);
      queryClient.removeQueries({ queryKey: meKey });
      navigate(nextPath(next), { replace: true });
    } catch (err) {
      setError(LOGIN_MESSAGES[err.code] ?? errorMessage(err));
      setFailed(true);
    } finally {
      setLoading(false);
    }
  };

  // 비밀번호 초기화는 관리자가 한다(F-AUTH-06) — 스스로 바꾸는 기능은 없다
  const onForgot = () =>
    modal.info({
      title: '비밀번호 분실/초기화',
      content: '비밀번호는 회사 관리자가 초기화합니다. 관리자에게 문의하면 임시 비밀번호를 받을 수 있습니다.',
      okText: '확인',
    });

  return (
    <div className="login-page auth-font">
      <section className="login-intro">
        <BrandLogo />
        <div className="login-hero">
          <div className="login-hero-badge">● 사내 통합 인사정보시스템 (HRIS)</div>
          <h1 className="login-hero-title">
            임직원을 위한
            <br />
            <em>통합 업무 포털</em>
          </h1>
          <p className="login-hero-desc">
            본 시스템은 임직원의 근태 체크, 연차 신청, 급여명세서 조회 및 인사 관리를 지원하는 사내 인트라넷 전용 업무
            시스템입니다.
          </p>
          <div className="login-features">
            {FEATURES.map((f) => (
              <div key={f.title} className="login-feature">
                <div className="login-feature-icon">
                  <img src={f.icon} alt="" />
                </div>
                <div className="login-feature-text">
                  <p className="login-feature-title">{f.title}</p>
                  <p className="login-feature-sub">{f.sub}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
        <p className="auth-copyright">© 2026 Team Oishi</p>
      </section>

      <section className="login-panel">
        <form className="login-form" onSubmit={onSubmit} noValidate>
          <span className="login-badge">임직원 전용 인증</span>
          <h2 className="login-title">Internal System Login</h2>
          <p className="login-sub">사내 계정으로 로그인해 주세요.</p>

          <label className="login-label" htmlFor="login-email">
            사내 이메일
          </label>
          <div className="login-input-wrap">
            <input
              id="login-email"
              className="login-input"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <label className="login-label is-password" htmlFor="login-password">
            비밀번호
          </label>
          <div className="login-input-wrap">
            <input
              id="login-password"
              className={`login-input has-toggle${failed ? ' is-failed' : ''}`}
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setFailed(false);
              }}
            />
            {password && (
              <button type="button" className="login-toggle" onClick={() => setShowPassword((v) => !v)}>
                {showPassword ? '숨기기' : '보기'}
              </button>
            )}
          </div>

          <div className="login-row">
            <label className="login-remember">
              <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
              아이디 저장
            </label>
            <button type="button" className="login-link" onClick={onForgot}>
              비밀번호 분실/초기화
            </button>
          </div>

          <button type="submit" className="login-submit" disabled={loading}>
            {loading ? '로그인 중…' : '로그인 (Sign In)'}
          </button>

          <div className="login-error-slot">
            {error && (
              <ul className="login-error" role="alert">
                <li>{error}</li>
              </ul>
            )}
          </div>

          <div className="login-notice">
            <p className="login-notice-title">사내 정보보안 안내</p>
            <ul>
              <li><span>본 시스템은 인가 임직원만 접근 가능합니다.</span></li>
              <li><span>비인가자의 무단 접속 및 계정 도용 시 사규에 따라 제재됩니다.</span></li>
              <li><span>로그인 시 소속 역할(Role)에 맞는 화면으로 자동 연결됩니다.</span></li>
            </ul>
          </div>

          {import.meta.env.DEV && (
            <Select
              className="login-dev"
              size="small"
              placeholder="데모 계정 고르기(개발용)"
              options={DEMO_ACCOUNTS}
              onChange={(value) => {
                setEmail(value);
                setPassword('Passw0rd!');
                setFailed(false);
                setError(null);
              }}
            />
          )}
        </form>
      </section>
    </div>
  );
}
