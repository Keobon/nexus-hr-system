# 🏢 NEXUS LABS 인사관리 시스템 (HR Management System)

NEXUS LABS의 실무 인사 관리(근태, 휴가, 급여, 평가, 조직 발령)를 효율적으로 처리하기 위한 사내 HR 플랫폼입니다.

---

## 🚨 팀원 필독: 핵심 비즈니스 로직 및 개발 규칙
우리 시스템은 기업의 인사/급여 데이터를 다루므로 **데이터의 무결성과 과거 이력 추적**이 최우선입니다. 코드를 작성하기 전 아래 규칙을 반드시 숙지해 주세요.

### 1. 절대 수정/삭제 금지 구역 (Append-only 원칙)
- **적용 도메인:** 인사발령(`AssignmentHistory`), 급여설정(`EmployeeSalary`), 급여명세서(`PayStub`), 재직상태이력(`EmploymentStatusHistory`)
- 위 테이블들은 백엔드에서 **UPDATE, DELETE API를 절대 만들지 마세요.** 
- 데이터 정정은 반드시 원본을 가리키는 신규 데이터(Correction)를 `INSERT`하는 방식으로 처리합니다.

### 2. 클라이언트(프론트엔드)의 요청 값을 의심하세요
- **모든 권한과 범위는 서버(백엔드)가 JWT 토큰을 기반으로 재계산합니다.**
- API 요청(Path, Body)에 포함된 대상 ID(`employeeId`, `approverId`)를 맹신하지 마세요. 
- 예: 휴가 신청 시 프론트에서 결재자를 골라서 보내지 않습니다. 백엔드가 토큰을 확인해 신청자의 부서장(`lead_employee_id`)을 찾아 자동으로 할당해야 합니다.

### 3. "계산된 총합"은 DB에 저장하지 않습니다 (On-the-fly)
- **휴가 잔여일수:** DB에 잔여일 컬럼은 없습니다. API 요청 시 `(15일 - 승인완료 일수 - 승인대기 일수)`로 실시간 계산해서 응답합니다.
- **평가 총점:** 총점 컬럼 없이, 질문별 응답 평균과 가중치를 곱해 실시간으로 환산하여 내려줍니다.

### 4. 도메인 자동 연동 파이프라인
- **휴가 → 근태:** 팀장이 휴가를 '승인(Approve)' 처리하는 순간, 트랜잭션 내에서 `Attendance(근태)` 테이블의 해당 기간을 '휴가' 상태로 자동 갱신해야 합니다.
- **부서장 지정 필수:** 부서(`Department`) 생성 시 부서장(`lead_employee_id`)을 반드시 지정해야 휴가 결재 라우팅이 정상 동작합니다.

---

## 🎨 프론트엔드(FE) 개발 가이드

### 1. 공통 응답 래퍼(Wrapper) 처리
모든 API 응답은 아래와 같은 공통 포맷으로 감싸져 옵니다. `success` 여부로 로직을 분기하세요.
```json
// 성공 시
{ "success": true, "data": { "id": 1, "name": "홍길동" } }

// 실패 시
{ "success": false, "error": { "code": "LEAVE_INSUFFICIENT_BALANCE", "message": "잔여 휴가가 부족합니다" } }

 2. Mock 데이터 선행 개발
백엔드 API 완성을 기다리지 마세요! API 명세서에 있는 JSON 응답 예시를 활용해 프론트엔드 내부에 가짜 데이터(Mock)를 두고 화면 UI와 액션을 먼저 개발합니다.

🛠️ DB 접속 및 기초 데이터(Seed) 세팅 안내 (백엔드 팀 필수)
1. 로컬 환경 DB 연결 정보 세팅
백엔드 프로젝트 구동 시 PostgreSQL 데이터베이스에 접근하기 위해 다음 정보를 환경변수(또는 application.yml, .env)에 설정해야 합니다.

DB URL (주소): jdbc:postgresql://localhost:5432/nexus_labs_hr

기본 포트는 5432이며, 생성한 데이터베이스 이름은 nexus_labs_hr입니다.

Username (사용자 계정): postgres (기본 최고 관리자 계정)

Password (비밀번호): 040416 (팀원 간 공유 필요)

2. 기초 데이터(Seed Data) 세팅 순서
시스템이 정상적으로 굴러가기 위해 DB 연결 후 가장 먼저 아래 순서대로 기초 데이터를 INSERT 해주세요. (순서가 틀리면 외래키(FK) 제약조건 에러가 발생합니다.)

마스터 데이터 생성: 4대 보험 요율 4건(INSURANCERATE), 소득세 구간 5건(TAXBRACKET), 직급/직책(JOBGRADE, JOBTITLE), 부서(DEPARTMENT)

최고 관리자 계정 생성:

부서장 및 관리자 역할을 할 직원(EMPLOYEE)을 먼저 1명 생성합니다.

해당 직원의 id를 부서(DEPARTMENT)의 lead_employee_id로 업데이트(UPDATE) 합니다.

해당 직원과 연결된 계정(ACCOUNT)을 생성하고 role을 HR_ADMIN 또는 CEO로 부여합니다.

🌿 깃허브(GitHub) 협업 및 브랜치 규칙
코드가 꼬이거나 날아가는 것을 방지하기 위해 아래의 Git 워크플로우를 엄격히 준수합니다.

1. 브랜치(Branch) 전략
main: 절대 직접 푸시(Push) 금지! 에러가 없는 완벽하게 배포 가능한 상태의 코드만 모이는 곳입니다.

develop: 프론트와 백엔드가 각자 작업한 코드를 합치고 테스트하는 공용 개발 브랜치입니다.

기능 브랜치 (feat/...): 실제 개인 작업은 반드시 새로운 브랜치를 따서 진행합니다.

프론트엔드 예시: feat/fe-login, feat/fe-leave-request

백엔드 예시: feat/be-auth-api, feat/be-salary-calc

2. 작업 및 업로드(Push) 순서
develop 브랜치 최신화: git pull origin develop

내 작업 브랜치 생성 및 이동: git checkout -b feat/작업명

열심히 코딩 후 저장(Commit):

git add .

git commit -m "feat: 휴가 신청 API 구현" (아래 커밋 메시지 규칙 준수)

내 작업 브랜치에 올리기: git push origin feat/작업명

깃허브 웹사이트에 접속해 Pull Request (PR) 생성 (목적지: develop 브랜치)

팀원 1명 이상의 리뷰(또는 승인) 후 develop으로 Merge(병합) 합니다.

3. 커밋 메시지 규칙
feat: 새로운 기능 추가

fix: 버그 수정

docs: 문서 수정 (README 등)

style: 코드 포맷팅, 세미콜론 누락 등 (코드 변경 없음)

refactor: 코드 리팩토링 (기능 변경 없이 코드 구조만 개선)
