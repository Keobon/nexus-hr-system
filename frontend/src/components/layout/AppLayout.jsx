import { useEffect, useMemo, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Button, Drawer, Dropdown } from 'antd';
import { LockOutlined, LogoutOutlined, MenuOutlined, UserOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { logout } from '../../api/auth';
import { clearToken } from '../../auth/token';
import { isSetupLocked } from '../../auth/permissions';
import { useMe } from '../../auth/useMe';
import BrandLogo from '../brand/BrandLogo';
import { useFileUrl } from '../common/useFileUrl';
import navLine from '../../assets/figma/nav-line.svg';
import { ALL_MENU_ITEMS, MENU_GROUPS } from './menu';
import './layout.css';

/** 지금 주소에 맞는 메뉴 — 가장 길게 일치하는 path(/me/leaves/new → 휴가 신청 및 조회) */
function selectedKey(pathname) {
  const matches = ALL_MENU_ITEMS.filter(
    (item) => pathname === item.path || (item.path !== '/' && pathname.startsWith(`${item.path}/`)),
  );
  matches.sort((a, b) => b.path.length - a.path.length);
  return matches[0]?.key;
}

const GROUP_CLASS = { main: 'is-main', me: 'is-service' };

function NavIcon({ item }) {
  if (item.navIcon) {
    // 내정보 아이콘은 Figma 에서 24px, 나머지 20px
    const large = item.key === 'my-info';
    return (
      <span className={`nav-icon${large ? ' is-large' : ''}`}>
        <span className="nav-icon-mask" style={{ '--icon': `url("${item.navIcon}")`, width: '100%', height: '100%' }} />
      </span>
    );
  }
  const Icon = item.icon;
  return (
    <span className="nav-icon">
      <Icon />
    </span>
  );
}

function Sidebar({ activeKey, onLogout }) {
  const { me, can, canAny } = useMe();
  const navigate = useNavigate();
  const avatarUrl = useFileUrl(me.employee?.profileFileId);

  const groups = useMemo(() => {
    const ctx = { me, can, canAny };
    const locked = isSetupLocked(me);
    return MENU_GROUPS.map((group) => ({
      ...group,
      items: group.items
        .filter((item) => item.visible(ctx))
        .map((item) => ({
          ...item,
          disabled: locked && group.lockedDuringSetup && !item.unlockedDuringSetup,
          count: item.badge ? me.todos?.[item.badge] : null,
        })),
    })).filter((group) => group.items.length > 0);
  }, [me, can, canAny]);

  const profileMenu = {
    items: [
      { key: 'me', icon: <UserOutlined />, label: '내 정보', onClick: () => navigate('/me') },
      { key: 'password', icon: <LockOutlined />, label: '비밀번호 변경', onClick: () => navigate('/password') },
      { type: 'divider' },
      { key: 'logout', icon: <LogoutOutlined />, label: '로그아웃', onClick: onLogout },
    ],
  };

  // Figma: "김민수 사원" / "EMPLOYEE/개발팀" — 역할 코드 대신 회사가 만든 역할 이름(role.name)
  const title = [me.employee?.name, me.employee?.jobGradeName].filter(Boolean).join(' ');
  const sub = [me.role?.name, me.employee?.orgUnitName].filter(Boolean).join('/');

  return (
    <nav className="nav" aria-label="메인 메뉴">
      <div className="nav-top">
        <BrandLogo />
        <img className="nav-line" src={navLine} alt="" />
      </div>

      <div className="nav-groups">
        {groups.map((group) => (
          <div key={group.key} className={`nav-group ${GROUP_CLASS[group.key] ?? 'is-admin'}`}>
            <p className="nav-group-title">{group.title}</p>
            <div className="nav-group-items">
              {group.items.map((item) => {
                const className = `nav-item${item.key === activeKey ? ' is-active' : ''}${item.disabled ? ' is-disabled' : ''}`;
                const content = (
                  <>
                    <NavIcon item={item} />
                    <span className="nav-item-label">{item.label}</span>
                    {item.count ? <span className="nav-badge">{item.count}</span> : null}
                  </>
                );
                return item.disabled ? (
                  <span key={item.key} className={className} aria-disabled="true" title="초기 설정을 마친 뒤 쓸 수 있습니다">
                    {content}
                  </span>
                ) : (
                  <Link key={item.key} to={item.path} className={className} aria-current={item.key === activeKey ? 'page' : undefined}>
                    {content}
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </div>

      <div className="nav-profile">
        <Dropdown menu={profileMenu} trigger={['click']} placement="topLeft">
          <button type="button" className="nav-profile-card">
            <span className="nav-avatar">{avatarUrl ? <img src={avatarUrl} alt="" /> : 'EMP'}</span>
            <span className="nav-profile-text">
              <span className="nav-profile-name">{title}</span>
              <span className="nav-profile-sub">{sub}</span>
            </span>
          </button>
        </Dropdown>
      </div>
    </nav>
  );
}

export default function AppLayout() {
  const { refetch } = useMe();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const activeKey = selectedKey(location.pathname);

  // 화면 이동 때 /me 를 다시 받는다 — 역할 · 배지가 바뀌면 다음 이동에 반영(M-3)
  useEffect(() => {
    refetch();
    setDrawerOpen(false);
  }, [location.pathname, refetch]);

  const handleLogout = async () => {
    try {
      await logout();
    } catch {
      // 서버 상태가 없어서 실패해도 토큰만 버리면 된다
    }
    clearToken();
    queryClient.clear();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app-shell">
      <Sidebar activeKey={activeKey} onLogout={handleLogout} />
      <header className="app-topbar">
        <Button type="text" icon={<MenuOutlined />} onClick={() => setDrawerOpen(true)} aria-label="메뉴" />
        <BrandLogo />
      </header>
      <Drawer
        className="nav-drawer"
        placement="left"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        size={260}
        closable={false}
      >
        <Sidebar activeKey={activeKey} onLogout={handleLogout} />
      </Drawer>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
