import { createBrowserRouter } from 'react-router-dom';
import RequireAuth from './auth/RequireAuth';
import AppLayout from './components/layout/AppLayout';
import MenuGuard from './components/layout/MenuGuard';
import { ALL_MENU_ITEMS } from './components/layout/menu';
import ForbiddenPage from './pages/ForbiddenPage';
import NotFoundPage from './pages/NotFoundPage';
import PlaceholderPage from './pages/PlaceholderPage';
import LoginPage from './pages/public/LoginPage';
import PasswordPage from './pages/public/PasswordPage';
import WelcomePage from './pages/public/WelcomePage';

// 메뉴별 화면. 만든 화면은 여기에 넣고, 없으면 담당자 표시와 함께 PlaceholderPage(역할 분담 3장)
const PAGES = {};

const OWNER = {
  home: 'FE-B · F-10',
  'my-info': 'FE-B · F-10',
  'org-chart': 'FE-B · F-10',
  'my-attendance': 'FE-B · F-11',
  'my-leaves': 'FE-B · F-12',
  approvals: 'FE-B · F-13',
  'my-work-requests': 'FE-B · F-14',
  'my-payroll': 'FE-B · F-16',
  payroll: 'FE-B · F-16',
  'my-evaluations': 'FE-A · F-17',
  'eval-todo': 'FE-A · F-17',
  evaluations: 'FE-A · F-17',
  employees: 'FE-A · F-04',
  assignments: 'FE-A · F-04',
  attendance: 'FE-A · F-15',
  leaves: 'FE-A · F-15',
  setup: 'FE-A · F-02',
};

const menuRoutes = ALL_MENU_ITEMS.filter((item) => item.key !== 'setup').map((item) => {
  const Page = PAGES[item.key];
  return {
    path: item.path,
    element: (
      <MenuGuard menuKey={item.key}>
        {Page ? <Page /> : <PlaceholderPage title={item.label} owner={OWNER[item.key] ?? 'FE-A · F-03'} />}
      </MenuGuard>
    ),
  };
});

export const router = createBrowserRouter([
  { path: '/welcome', element: <WelcomePage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <PlaceholderPage title="회사 등록" owner="FE-A · F-02" /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/password', element: <PasswordPage /> },
      // 마법사는 사이드바 없는 전체 화면(메뉴 구조 4장)
      { path: '/setup', element: <MenuGuard menuKey="setup"><PlaceholderPage title="초기 설정 마법사" owner={OWNER.setup} /></MenuGuard> },
      {
        element: <AppLayout />,
        children: [
          ...menuRoutes,
          { path: '/forbidden', element: <ForbiddenPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
