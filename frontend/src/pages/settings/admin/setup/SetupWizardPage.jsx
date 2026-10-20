// 초기 설정 마법사(F-COMP-03, API 3장) — 사이드바 없는 전체 화면, 상단 단계 표시 ①~⑦
// 각 단계 본문은 회사 설정 화면을 그대로 넣는다(⑦은 직원 등록 폼). 체크는 GET /company/setup 의 done.
// 저장한 뒤 다시 불러 체크를 갱신하고, 체크가 없어도 [건너뛰기] · [설정 완료] 를 누를 수 있다
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Spin } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meKey } from '../../../../api/auth';
import { completeSetup, getSetup, setupKey } from '../../../../api/company';
import BrandLogo from '../../../../components/brand/BrandLogo';
import { useNotify } from '../../../../components/common/useNotify';
import { errorMessage } from '../../../../constants/errors';
import { labelOf } from '../../../../constants/labels';
import EmployeeCreatePage from '../../../hr/employees/EmployeeCreatePage';
import OrgSettingsPage from '../../OrgSettingsPage';
import TitleSettingsPage from '../../TitleSettingsPage';
import EmploymentTypeSettingsPage from '../../EmploymentTypeSettingsPage';
import PaySettingsPage from '../../pay/PaySettingsPage';
import WorkSettingsPage from '../work/WorkSettingsPage';
import '../admin.css';

const STEPS = [
  { code: 'ORG_UNITS', intro: '회사 아래에 본부 · 팀을 만드세요. 최상위 조직(회사)은 세지 않습니다.', Body: OrgSettingsPage },
  { code: 'JOB_GRADES_TITLES', intro: '사원 · 대리 같은 직급과 팀장 같은 직책을 등록하세요. 직급이 1개 이상이면 체크됩니다.', Body: TitleSettingsPage },
  { code: 'EMPLOYMENT_TYPES', intro: '기본 고용형태 3개가 만들어져 있습니다. 확인만 하고 넘어가도 됩니다.', Body: EmploymentTypeSettingsPage },
  { code: 'WORK_SCHEDULE_HOLIDAYS', intro: '기본 근무시간(09–18시)이 들어 있습니다. 회사 휴일이 있으면 추가하세요.', Body: () => <WorkSettingsPage embedded /> },
  { code: 'LEAVE_TYPES', intro: '기본 휴가 종류 3개가 만들어져 있습니다.', Body: LeaveTypesNote },
  { code: 'PAY_ITEMS', intro: '기본 급여 항목 10개 · 계산 변수 · 세율 구간이 들어 있습니다. 확인만 하고 넘어가도 됩니다.', Body: PaySettingsPage },
  { code: 'EMPLOYEES', intro: '관리자 본인 말고 직원을 1명 이상 등록하면 체크됩니다. 나머지 직원은 나중에 인사 관리 › 직원에서 등록할 수 있습니다.', Body: EmployeeStep },
];

const countText = (counts = {}) => {
  const L = { orgUnits: '조직', jobGrades: '직급', jobTitles: '직책', employmentTypes: '고용형태', workSchedules: '근무시간', holidays: '휴일',
    leaveTypes: '휴가 종류', payItems: '급여 항목', employees: '직원' };
  return Object.entries(counts).map(([k, v]) => `${L[k] ?? k} ${v}개`).join(' · ');
};

export default function SetupWizardPage() {
  const navigate = useNavigate();
  const notify = useNotify();
  const queryClient = useQueryClient();
  // 단계는 화면 상태로 둔다 — 안에 넣은 설정 화면들이 주소(?tab=)를 바꾸기 때문. 처음만 ?step= 을 읽는다
  const [sp, setSp] = useSearchParams();
  const [index, setIndex] = useState(() => Math.min(Math.max(Number(sp.get('step') ?? 1) || 1, 1), STEPS.length) - 1);
  const setup = useQuery({ queryKey: setupKey, queryFn: getSetup, refetchInterval: 15_000 });

  // 어느 단계에서든 저장이 끝나면 체크를 다시 불러온다
  useEffect(() => queryClient.getMutationCache().subscribe((e) => {
    if (e.type === 'updated' && e.mutation.state.status === 'success') queryClient.invalidateQueries({ queryKey: setupKey });
  }), [queryClient]);
  useEffect(() => { queryClient.invalidateQueries({ queryKey: setupKey }); }, [index, queryClient]);

  const finish = useMutation({
    mutationFn: completeSetup,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: meKey });
      notify.success('초기 설정을 마쳤습니다. 이제 인사 관리 · 회사 설정 메뉴를 쓸 수 있습니다');
      navigate('/', { replace: true });
    },
    onError: (err) => notify.error(err),
  });

  const go = (i) => {
    setIndex(i);
    setSp({}, { replace: true }); // 앞 단계 화면의 ?tab= 을 지운다
    window.scrollTo(0, 0);
  };
  const statusOf = (code) => setup.data?.steps?.find((s) => s.step === code);
  const step = STEPS[index];
  const status = statusOf(step.code);
  const Body = step.Body;
  const last = index === STEPS.length - 1;
  const doneCount = (setup.data?.steps ?? []).filter((s) => s.done).length;

  return (
    <div className="wz">
      <header className="wz-top">
        <BrandLogo />
        <h1>초기 설정</h1>
        <button type="button" className="hr-link wz-later" onClick={() => navigate('/')}>나중에 하기 (내 업무로)</button>
        <button type="button" className="hr-btn" disabled={finish.isPending} onClick={() => finish.mutate()}>설정 완료</button>
      </header>

      <nav className="wz-steps" aria-label="설정 단계">
        {STEPS.map((s, i) => {
          const done = statusOf(s.code)?.done;
          return (
            <button key={s.code} type="button" className={`wz-step${i === index ? ' is-on' : ''}${done ? ' is-done' : ''}`}
              aria-current={i === index ? 'step' : undefined} onClick={() => go(i)}>
              <span className="n">{done ? '✓' : i + 1}</span>
              <span className="t">{labelOf('setupStep', s.code)}</span>
            </button>
          );
        })}
      </nav>

      <main className="wz-body">
        {setup.error ? <p className="hr-notice is-error">{errorMessage(setup.error)}</p> : null}
        <div className="wz-intro">
          <span><strong>{`${index + 1}. ${labelOf('setupStep', step.code)}`}</strong> — {step.intro}</span>
          {setup.isLoading ? <Spin size="small" /> : status ? (
            <span className={`hr-badge ${status.done ? 'is-ok' : 'is-gray'}`}>{status.done ? `완료 · ${countText(status.counts)}` : `미완료 · ${countText(status.counts)}`}</span>
          ) : null}
        </div>
        <Body onNext={() => go(index + 1)} />
      </main>

      <footer className="wz-foot">
        <button type="button" className="hr-btn is-secondary" disabled={index === 0} onClick={() => go(index - 1)}>이전</button>
        <span className="grow">{`${STEPS.length}단계 중 ${doneCount}단계 체크됨 · 모든 단계는 건너뛸 수 있습니다`}</span>
        {!last && <button type="button" className="hr-btn is-secondary" onClick={() => go(index + 1)}>건너뛰기</button>}
        {last ? (
          <button type="button" className="hr-btn" disabled={finish.isPending} onClick={() => finish.mutate()}>{finish.isPending ? '처리 중…' : '설정 완료'}</button>
        ) : (
          <button type="button" className="hr-btn" onClick={() => go(index + 1)}>다음</button>
        )}
      </footer>
    </div>
  );
}

/** ⑤ 휴가 종류 — 휴가 종류 화면은 인사 담당 화면에 있다. 마법사에서는 기본값 확인만 */
function LeaveTypesNote() {
  return (
    <section className="ad-section wz-summary">
      <div className="ad-section-head"><h2 className="is-20">휴가 종류</h2></div>
      <p className="hr-note">회사를 등록할 때 연차 · 경조사 · 병가 같은 기본 휴가 종류가 만들어졌습니다. 이름 · 일수를 바꾸거나 새 종류를 추가하려면 설정을 마친 뒤 회사 설정 › 휴가 종류에서 할 수 있습니다.</p>
    </section>
  );
}

/** ⑦ 직원 등록 — 등록하면 폼을 비우고 이 단계에 남는다(여러 명 연달아) */
function EmployeeStep() {
  return <EmployeeCreatePage onCancel={() => {}} onDone={() => {}} />;
}
