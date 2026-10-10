import { useEffect, useMemo, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Avatar, Badge, Button, Drawer, Dropdown, Grid, Layout, Menu, Space, Typography } from 'antd';
import { DownOutlined, LockOutlined, LogoutOutlined, MenuOutlined, UserOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { logout } from '../../api/auth';
import { clearToken } from '../../auth/token';
import { isSetupLocked } from '../../auth/permissions';
import { useMe } from '../../auth/useMe';
import { ALL_MENU_ITEMS, MENU_GROUPS } from './menu';

const { Header, Sider, Content } = Layout;

/** 지금 주소에 맞는 메뉴 — 가장 길게 일치하는 path(/me/leaves/new → 내 휴가) */
function selectedKey(pathname) {
  const matches = ALL_MENU_ITEMS.filter(
    (item) => pathname === item.path || (item.path !== '/' && pathname.startsWith(`${item.path}/`)),
  );
  matches.sort((a, b) => b.path.length - a.path.length);
  return matches[0]?.key;
}

function useMenuItems() {
  const { me, can, canAny } = useMe();
  return useMemo(() => {
    const ctx = { me, can, canAny };
    const locked = isSetupLocked(me);
    return MENU_GROUPS.map((group) => {
      const children = group.items
        .filter((item) => item.visible(ctx))
        .map((item) => {
          const disabled = locked && group.lockedDuringSetup && !item.unlockedDuringSetup;
          const count = item.badge ? me.todos?.[item.badge] : null;
          const Icon = item.icon;
          return {
            key: item.key,
            icon: <Icon />,
            disabled,
            label: (
              <Link to={disabled ? '#' : item.path} className="menu-link">
                <span>{item.label}</span>
                {count ? <Badge count={count} size="small" /> : null}
              </Link>
            ),
          };
        });
      return children.length ? { type: 'group', key: group.key, label: group.title, children } : null;
    }).filter(Boolean);
  }, [me, can, canAny]);
}

export default function AppLayout() {
  const { me, refetch } = useMe();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const screens = Grid.useBreakpoint();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const items = useMenuItems();
  const isMobile = !screens.lg;

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

  const userMenu = {
    items: [
      { key: 'me', icon: <UserOutlined />, label: '내 정보', onClick: () => navigate('/me') },
      { key: 'password', icon: <LockOutlined />, label: '비밀번호 변경', onClick: () => navigate('/password') },
      { type: 'divider' },
      { key: 'logout', icon: <LogoutOutlined />, label: '로그아웃', onClick: handleLogout },
    ],
  };

  const sideMenu = (
    <Menu mode="inline" items={items} selectedKeys={[selectedKey(location.pathname)]} className="side-menu" />
  );

  return (
    <Layout className="app-layout">
      {isMobile ? (
        <Drawer placement="left" open={drawerOpen} onClose={() => setDrawerOpen(false)} width={260} styles={{ body: { padding: 0 } }}>
          {sideMenu}
        </Drawer>
      ) : (
        <Sider width={232} theme="light" className="app-sider">
          <div className="sider-brand">NEXUS HR</div>
          {sideMenu}
        </Sider>
      )}
      <Layout>
        <Header className="app-header">
          <Space>
            {isMobile && <Button type="text" icon={<MenuOutlined />} onClick={() => setDrawerOpen(true)} aria-label="메뉴" />}
            <Typography.Text strong>{me.company?.name}</Typography.Text>
          </Space>
          <Dropdown menu={userMenu} trigger={['click']}>
            <Button type="text">
              <Space>
                <Avatar size="small" icon={<UserOutlined />} />
                {me.employee?.name}
                <DownOutlined />
              </Space>
            </Button>
          </Dropdown>
        </Header>
        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
