// 사이드바 메뉴 정의(메뉴 구조 v2 2·3장). 메뉴는 우리가 고정하고, 보이는지는 권한이 정한다(M-1).
// visible(ctx) — ctx = { me, can, canAny }. 라우트도 같은 visible 로 막는다(MenuGuard).
// badge — /me 의 todos 키(M-7). 값이 null 이면 배지를 볼 권한이 없다.
import {
  ApartmentOutlined,
  AuditOutlined,
  BankOutlined,
  CalendarOutlined,
  CarOutlined,
  CheckSquareOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  FileSearchOutlined,
  FormOutlined,
  HomeOutlined,
  IdcardOutlined,
  StarOutlined,
  TeamOutlined,
  ToolOutlined,
  UserOutlined,
} from '@ant-design/icons';

const always = () => true;

export const MENU_GROUPS = [
  {
    key: 'me',
    title: '내 업무',
    items: [
      { key: 'home', path: '/', label: '홈', icon: HomeOutlined, visible: always },
      { key: 'my-info', path: '/me', label: '내 정보', icon: UserOutlined, visible: always },
      { key: 'my-attendance', path: '/me/attendance', label: '내 근태', icon: ClockCircleOutlined, visible: always },
      { key: 'my-leaves', path: '/me/leaves', label: '내 휴가', icon: CalendarOutlined, visible: always },
      { key: 'my-work-requests', path: '/me/work-requests', label: '연장근무·출장', icon: CarOutlined, visible: always },
      {
        key: 'my-payroll',
        path: '/me/payroll',
        label: '내 급여',
        icon: DollarOutlined,
        visible: ({ me }) => me.employee?.payrollEligible === true,
      },
      { key: 'my-evaluations', path: '/me/evaluations', label: '내 평가', icon: StarOutlined, visible: always },
      { key: 'org-chart', path: '/org-chart', label: '조직도', icon: ApartmentOutlined, visible: always },
      {
        key: 'approvals',
        path: '/approvals',
        label: '승인함',
        icon: CheckSquareOutlined,
        visible: always,
        badge: 'approvalsPending',
      },
      {
        // 진행 중 평가에서 내가 평가자인 건이 있을 때. /me 에는 미제출 건수만 있어서 그걸로 판단한다
        key: 'eval-todo',
        path: '/evaluations/todo',
        label: '평가하기',
        icon: FormOutlined,
        visible: ({ me }) => (me.todos?.evaluationsToSubmit ?? 0) > 0,
        badge: 'evaluationsToSubmit',
      },
    ],
  },
  {
    key: 'hr',
    title: '인사 관리',
    lockedDuringSetup: true,
    items: [
      {
        key: 'employees',
        path: '/employees',
        label: '직원',
        icon: TeamOutlined,
        visible: ({ canAny }) => canAny('EMPLOYEE_READ', 'EMPLOYEE_MANAGE'),
      },
      {
        key: 'attendance',
        path: '/attendance',
        label: '근태 현황',
        icon: ClockCircleOutlined,
        visible: ({ canAny }) => canAny('ATTENDANCE_READ', 'ATTENDANCE_MANAGE'),
        badge: 'attendanceCorrections',
      },
      {
        key: 'leaves',
        path: '/leaves',
        label: '휴가 현황',
        icon: CalendarOutlined,
        visible: ({ canAny }) => canAny('LEAVE_READ', 'LEAVE_MANAGE'),
      },
      {
        key: 'payroll',
        path: '/payroll',
        label: '급여',
        icon: DollarOutlined,
        visible: ({ canAny }) => canAny('PAYROLL_READ', 'PAYROLL_MANAGE'),
      },
      {
        key: 'assignments',
        path: '/assignments',
        label: '인사발령',
        icon: IdcardOutlined,
        visible: ({ canAny }) => canAny('ASSIGNMENT_READ', 'ASSIGNMENT_MANAGE'),
      },
      {
        key: 'evaluations',
        path: '/evaluations',
        label: '평가 운영',
        icon: StarOutlined,
        visible: ({ canAny }) => canAny('EVAL_READ', 'EVAL_MANAGE'),
      },
    ],
  },
  {
    key: 'settings',
    title: '회사 설정',
    lockedDuringSetup: true,
    items: [
      { key: 'set-company', path: '/settings/company', label: '회사 정보', icon: BankOutlined, perm: 'COMPANY_MANAGE' },
      { key: 'set-work', path: '/settings/work', label: '근무시간·휴일', icon: ClockCircleOutlined, perm: 'COMPANY_MANAGE' },
      { key: 'set-orgs', path: '/settings/organizations', label: '조직', icon: ApartmentOutlined, perm: 'ORG_MANAGE' },
      { key: 'set-titles', path: '/settings/titles', label: '직급·직책', icon: IdcardOutlined, perm: 'ORG_MANAGE' },
      { key: 'set-emp-types', path: '/settings/employment-types', label: '고용형태', icon: TeamOutlined, perm: 'ORG_MANAGE' },
      { key: 'set-emp-fields', path: '/settings/employee-fields', label: '직원 정보 항목', icon: FormOutlined, perm: 'ORG_MANAGE' },
      { key: 'set-roles', path: '/settings/roles', label: '역할·권한', icon: AuditOutlined, perm: 'ROLE_MANAGE' },
      { key: 'set-accounts', path: '/settings/accounts', label: '계정 관리', icon: UserOutlined, perm: 'ROLE_MANAGE' },
      {
        key: 'set-approval-lines',
        path: '/settings/approval-lines',
        label: '승인선',
        icon: CheckSquareOutlined,
        perm: 'APPROVAL_MANAGE',
        badge: 'reassignNeeded',
      },
      { key: 'set-leave-types', path: '/settings/leave-types', label: '휴가 종류', icon: CalendarOutlined, perm: 'LEAVE_MANAGE' },
      { key: 'set-expense-types', path: '/settings/expense-types', label: '출장 경비 종류', icon: CarOutlined, perm: 'ATTENDANCE_MANAGE' },
      { key: 'set-pay', path: '/settings/pay', label: '급여 항목·변수', icon: DollarOutlined, perm: 'PAYROLL_MANAGE' },
      { key: 'set-eval-templates', path: '/settings/eval-templates', label: '평가 템플릿', icon: StarOutlined, perm: 'EVAL_MANAGE' },
      { key: 'set-audit-logs', path: '/settings/audit-logs', label: '감사 로그', icon: FileSearchOutlined, perm: 'AUDIT_READ' },
      {
        key: 'setup',
        path: '/setup',
        label: '초기 설정 이어하기',
        icon: ToolOutlined,
        visible: ({ me, can }) => can('COMPANY_MANAGE') && !me.company?.setupCompleted,
        unlockedDuringSetup: true,
      },
    ].map((item) => (item.perm ? { ...item, visible: ({ can }) => can(item.perm) } : item)),
  },
];

export const ALL_MENU_ITEMS = MENU_GROUPS.flatMap((g) =>
  g.items.map((item) => ({ ...item, group: g.key, lockedDuringSetup: g.lockedDuringSetup && !item.unlockedDuringSetup })),
);

export const findMenu = (key) => ALL_MENU_ITEMS.find((item) => item.key === key);
