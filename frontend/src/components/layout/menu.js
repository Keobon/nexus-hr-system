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
import navAccount from '../../assets/figma/nav-account.svg';
import navCalendar from '../../assets/figma/nav-calendar.svg';
import navClock from '../../assets/figma/nav-clock.svg';
import navDashboard from '../../assets/figma/nav-dashboard.svg';
import navMoney from '../../assets/figma/nav-money.svg';
import navPeople from '../../assets/figma/nav-people.svg';
import navStatistic from '../../assets/figma/nav-statistic.svg';

const always = () => true;

/**
 * 최고 관리자 — 회사를 등록한 사람의 시스템 역할. 이 역할은 이름 · 권한을 바꿀 수 없어서(SYSTEM_ROLE_READONLY)
 * 이름으로 알아본다. 역할 이름으로 분기하는 곳은 이 메뉴 정의 한 곳뿐이다(화면마다 넣지 않는다).
 */
const isSuperAdmin = (me) => me?.role?.name === '최고 관리자';

/**
 * 경영진 — 회사 대시보드(DASHBOARD_COMPANY)는 보고 관리 권한(…_MANAGE)은 하나도 없는 역할(대표 · 임원).
 * Figma 사장 화면(133-6428 외) — 첫 화면은 경영 총괄 대시보드, 사이드바는 승인·평가 · 전사 정보로 나눈다
 */
const MANAGE_PERMS = ['COMPANY_MANAGE', 'ROLE_MANAGE', 'ORG_MANAGE', 'EMPLOYEE_MANAGE', 'ATTENDANCE_MANAGE', 'LEAVE_MANAGE', 'PAYROLL_MANAGE', 'ASSIGNMENT_MANAGE', 'EVAL_MANAGE', 'APPROVAL_MANAGE'];
export const isExecutive = ({ can, canAny }) => can('DASHBOARD_COMPANY') && !canAny(...MANAGE_PERMS);

// 묶음 단위로 숨기는 조건은 hiddenFor(ctx). 아래 MENU_GROUPS 가 각 메뉴의 visible 에 합쳐서
// 사이드바와 주소 접근(MenuGuard)이 같은 규칙을 쓴다.
// 역할에 따라 이름만 바꿀 때는 titleFor(ctx) · labelFor(ctx) — 없으면 title · label
// 사이드바에서만 숨길 때(주소로는 들어갈 수 있게)는 navHiddenFor(ctx)
const GROUPS = [
  // Figma EMP_Sidebar — MAIN(대시보드) · 인사 서비스(내 업무). 이름 · 아이콘이 Figma 에 있는 메뉴는 그대로 쓴다(navIcon)
  {
    key: 'main',
    title: 'MAIN',
    items: [{ key: 'home', path: '/', label: '대시보드', icon: HomeOutlined, navIcon: navDashboard, visible: always }],
  },
  {
    key: 'me',
    title: '인사 서비스',
    // 경영진은 사이드바에 개인 메뉴(내 근태 · 휴가 · 급여 …) 없이 승인 · 평가만 — 주소 · 프로필 메뉴(내 정보)로는 들어간다
    titleFor: (ctx) => (isExecutive(ctx) ? '승인·평가' : null),
    items: [
      { key: 'my-info', path: '/me', label: '내정보', icon: UserOutlined, navIcon: navAccount, visible: always, navHiddenFor: isExecutive },
      {
        key: 'my-attendance',
        path: '/me/attendance',
        label: '출퇴근 및 근태 조회',
        icon: ClockCircleOutlined,
        navIcon: navClock,
        visible: always,
        navHiddenFor: isExecutive,
      },
      {
        key: 'my-leaves',
        path: '/me/leaves',
        label: '휴가 신청 및 조회',
        icon: CalendarOutlined,
        navIcon: navCalendar,
        visible: always,
        navHiddenFor: isExecutive,
      },
      { key: 'my-work-requests', path: '/me/work-requests', label: '연장근무·출장', icon: CarOutlined, visible: always, navHiddenFor: isExecutive },
      {
        key: 'my-payroll',
        path: '/me/payroll',
        label: '급여 명세서',
        icon: DollarOutlined,
        navIcon: navMoney,
        visible: ({ me }) => me.employee?.payrollEligible === true,
        navHiddenFor: isExecutive,
      },
      {
        key: 'my-evaluations',
        path: '/me/evaluations',
        label: '인사발령/평가 조회',
        icon: StarOutlined,
        navIcon: navStatistic,
        visible: always,
        navHiddenFor: isExecutive,
      },
      { key: 'org-chart', path: '/org-chart', label: '조직도', icon: ApartmentOutlined, navIcon: navPeople, visible: always, navHiddenFor: isExecutive },
      {
        // 결재하는 사람만 — 조직장이거나, 승인선에서 직책 · 직원으로 지정돼 지금 처리할 건이 있을 때(일반 직원 화면에는 없다)
        key: 'approvals',
        path: '/approvals',
        label: '승인함',
        icon: CheckSquareOutlined,
        visible: ({ me }) => (me.leadOrgUnitIds?.length ?? 0) > 0 || (me.todos?.approvalsPending ?? 0) > 0,
        badge: 'approvalsPending',
      },
      {
        // 조직장(평가자)이거나 미제출 평가가 있을 때. 다 제출한 조직장도 제출 · 확정 상태를 다시 볼 수 있게 남긴다
        key: 'eval-todo',
        path: '/evaluations/todo',
        label: '평가하기',
        labelFor: (ctx) => (isExecutive(ctx) ? '팀 평가' : null),
        icon: FormOutlined,
        visible: ({ me }) => (me.leadOrgUnitIds?.length ?? 0) > 0 || (me.todos?.evaluationsToSubmit ?? 0) > 0,
        badge: 'evaluationsToSubmit',
      },
    ],
  },
  {
    key: 'hr',
    title: '인사 관리',
    titleFor: (ctx) => (isExecutive(ctx) ? '전사 정보' : null),
    lockedDuringSetup: true,
    // 최고 관리자는 인사 관리를 보지 않는다 — 인사 업무는 인사 담당 계정으로 한다(노션 추가사항, 2026-10-11)
    hiddenFor: ({ me }) => isSuperAdmin(me),
    items: [
      {
        key: 'employees',
        path: '/employees',
        label: '직원',
        labelFor: (ctx) => (isExecutive(ctx) ? '전직원 현황' : null),
        icon: TeamOutlined,
        visible: ({ canAny }) => canAny('EMPLOYEE_READ', 'EMPLOYEE_MANAGE'),
      },
      {
        key: 'attendance',
        path: '/attendance',
        label: '근태 현황',
        labelFor: (ctx) => (isExecutive(ctx) ? '전사 근태 현황' : null),
        icon: ClockCircleOutlined,
        visible: ({ canAny }) => canAny('ATTENDANCE_READ', 'ATTENDANCE_MANAGE'),
        badge: 'attendanceCorrections',
      },
      {
        key: 'leaves',
        path: '/leaves',
        label: '휴가 현황',
        labelFor: (ctx) => (isExecutive(ctx) ? '전사 휴가 현황' : null),
        icon: CalendarOutlined,
        visible: ({ canAny }) => canAny('LEAVE_READ', 'LEAVE_MANAGE'),
      },
      {
        key: 'payroll',
        path: '/payroll',
        label: '급여',
        labelFor: (ctx) => (isExecutive(ctx) ? '전직원 급여 현황' : null),
        icon: DollarOutlined,
        visible: ({ canAny }) => canAny('PAYROLL_READ', 'PAYROLL_MANAGE'),
      },
      {
        key: 'assignments',
        path: '/assignments',
        label: '인사발령',
        labelFor: (ctx) => (isExecutive(ctx) ? '전사 인사발령' : null),
        icon: IdcardOutlined,
        visible: ({ canAny }) => canAny('ASSIGNMENT_READ', 'ASSIGNMENT_MANAGE'),
      },
      {
        key: 'evaluations',
        path: '/evaluations',
        label: '평가 관리',
        labelFor: (ctx) => (isExecutive(ctx) ? '전사 평가 결과' : null),
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

export const MENU_GROUPS = GROUPS.map((group) => ({
  ...group,
  items: group.items.map((item) => ({
    ...item,
    visible: (ctx) => !group.hiddenFor?.(ctx) && item.visible(ctx),
  })),
}));

export const ALL_MENU_ITEMS = MENU_GROUPS.flatMap((g) =>
  g.items.map((item) => ({ ...item, group: g.key, lockedDuringSetup: g.lockedDuringSetup && !item.unlockedDuringSetup })),
);

export const findMenu = (key) => ALL_MENU_ITEMS.find((item) => item.key === key);
