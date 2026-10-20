import { createBrowserRouter, Navigate } from 'react-router-dom';
import RequireAuth from './auth/RequireAuth';
import AppLayout from './components/layout/AppLayout';
import MenuGuard from './components/layout/MenuGuard';
import { ALL_MENU_ITEMS } from './components/layout/menu';
import ComponentsPage from './pages/dev/ComponentsPage';
import HomePage from './pages/me/HomePage';
import MyInfoPage from './pages/me/myinfo/MyInfoPage';
import MyAttendancePage from './pages/me/attendance/MyAttendancePage';
import MyLeavePage from './pages/me/leave/MyLeavePage';
import MyWorkRequestsPage from './pages/me/work/MyWorkRequestsPage';
import MyPayrollPage from './pages/me/payroll/MyPayrollPage';
import MyEvaluationPage from './pages/me/evaluation/MyEvaluationPage';
import OrgChartPage from './pages/me/org/OrgChartPage';
import EmployeeListPage from './pages/hr/employees/EmployeeListPage';
import EmployeeCreatePage from './pages/hr/employees/EmployeeCreatePage';
import EmployeeDetailPage from './pages/hr/employees/EmployeeDetailPage';
import AssignmentListPage from './pages/hr/assignments/AssignmentListPage';
import PayrollPage from './pages/hr/payroll/PayrollPage';
import EvaluationAdminPage from './pages/hr/evaluations/EvaluationAdminPage';
import AssignmentCreatePage from './pages/hr/assignments/AssignmentCreatePage';
import AssignmentCorrectionPage from './pages/hr/assignments/AssignmentCorrectionPage';
import OrgSettingsPage from './pages/settings/OrgSettingsPage';
import TitleSettingsPage from './pages/settings/TitleSettingsPage';
import PaySettingsPage from './pages/settings/pay/PaySettingsPage';
import EvalTemplatePage from './pages/settings/evalTemplates/EvalTemplatePage';
import EvalTodoPage from './pages/me/evalTodo/EvalTodoPage';
import EmploymentTypeSettingsPage from './pages/settings/EmploymentTypeSettingsPage';
import CompanyPage from './pages/settings/admin/company/CompanyPage';
import WorkSettingsPage from './pages/settings/admin/work/WorkSettingsPage';
import RolesPage from './pages/settings/admin/roles/RolesPage';
import AccountsPage from './pages/settings/admin/accounts/AccountsPage';
import ApprovalLinesPage from './pages/settings/admin/approval/ApprovalLinesPage';
import AuditLogPage from './pages/settings/admin/audit/AuditLogPage';
import SetupWizardPage from './pages/settings/admin/setup/SetupWizardPage';
import ForbiddenPage from './pages/ForbiddenPage';
import NotFoundPage from './pages/NotFoundPage';
import PlaceholderPage from './pages/PlaceholderPage';
import LoginPage from './pages/public/LoginPage';
import PasswordPage from './pages/public/PasswordPage';
import SignupPage from './pages/public/SignupPage';

// 메뉴별 화면. 만든 화면은 여기에 넣고, 없으면 담당자 표시와 함께 PlaceholderPage(역할 분담 3장)
const PAGES = {
  home: HomePage,
  'my-info': MyInfoPage,
  'my-attendance': MyAttendancePage,
  'my-leaves': MyLeavePage,
  'my-work-requests': MyWorkRequestsPage,
  'my-payroll': MyPayrollPage,
  'my-evaluations': MyEvaluationPage,
  'eval-todo': EvalTodoPage,
  'org-chart': OrgChartPage,
  employees: EmployeeListPage,
  assignments: AssignmentListPage,
  payroll: PayrollPage,
  evaluations: EvaluationAdminPage,
  'set-orgs': OrgSettingsPage,
  'set-titles': TitleSettingsPage,
  'set-pay': PaySettingsPage,
  'set-eval-templates': EvalTemplatePage,
  'set-company': CompanyPage,
  'set-work': WorkSettingsPage,
  'set-emp-types': EmploymentTypeSettingsPage,
  'set-roles': RolesPage,
  'set-accounts': AccountsPage,
  'set-approval-lines': ApprovalLinesPage,
  'set-audit-logs': AuditLogPage,
};

// 메뉴 아래 화면(등록 · 상세 · 정정) — 부모 메뉴와 같은 규칙으로 막는다. 버튼은 화면에서 can() 으로 감싼다
const SUB_PAGES = [
  { path: '/employees/new', menuKey: 'employees', Page: EmployeeCreatePage },
  { path: '/employees/:id', menuKey: 'employees', Page: EmployeeDetailPage },
  { path: '/assignments/new', menuKey: 'assignments', Page: AssignmentCreatePage },
  { path: '/assignments/:id/correct', menuKey: 'assignments', Page: AssignmentCorrectionPage },
];

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
  'eval-todo': 'FE-B · F-17',
  evaluations: 'FE-A · F-17',
  employees: 'FE-A · F-04',
  assignments: 'FE-A · F-04',
  attendance: 'FE-A · F-15',
  leaves: 'FE-A · F-15',
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

const subRoutes = SUB_PAGES.map(({ path, menuKey, Page }) => ({
  path,
  element: (
    <MenuGuard menuKey={menuKey}>
      <Page />
    </MenuGuard>
  ),
}));

export const router = createBrowserRouter([
  // 로그인 안 한 사용자는 로그인 화면부터. 회사 등록은 링크 없이 /signup 주소로만(일반 직원은 등록하지 않는다)
  { path: '/welcome', element: <Navigate to="/login" replace /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/password', element: <PasswordPage /> },
      // 마법사는 사이드바 없는 전체 화면(메뉴 구조 4장)
      { path: '/setup', element: <MenuGuard menuKey="setup"><SetupWizardPage /></MenuGuard> },
      {
        element: <AppLayout />,
        children: [
          ...menuRoutes,
          ...subRoutes,
          // 공통 컴포넌트 견본 — 개발 서버에서만(빌드에서는 import.meta.env.DEV 가 false 라 빠진다)
          ...(import.meta.env.DEV ? [{ path: '/dev/components', element: <ComponentsPage /> }] : []),
          { path: '/forbidden', element: <ForbiddenPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
