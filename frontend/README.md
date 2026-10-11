# NEXUS HR — 프론트엔드

React 19 + Vite + Ant Design 6 + TanStack Query 5 + React Router 7 (JavaScript).
기준 문서: 노션 **메뉴 구조 v2 · 프론트엔드 개발 가이드 v2 · API 설계서 v2 · 기능명세서 v2**.

## 실행

```bash
# 1) 저장소 루트에서 백엔드 + 데모 DB
docker compose up --build          # http://localhost:8080

# 2) 프론트
cd frontend
npm install
npm run dev                        # http://localhost:5173
```

`/api` 요청은 개발 서버가 `localhost:8080`으로 넘긴다(`vite.config.js`). 백엔드 주소가 다르면 `.env.example`을 `.env.local`로 복사해 고친다.

개발 서버 로그인 화면에 **데모 계정 고르기**가 있다(빌드에는 안 들어감). 비밀번호는 모두 `Passw0rd!`.

## 구조

```
src/
├─ api/          client.js(응답 래퍼 · 에러 처리), 영역별 API 함수(auth.js · files.js …)
├─ auth/         token.js · useMe() · makeCan()/isSetupLocked() · RequireAuth · nextPath
├─ constants/    labels.js(코드값 → 한글 라벨 · 배지 색, 가이드 부록 A) · errors.js(에러 코드 → 메시지, 가이드 5장)
├─ components/
│  ├─ layout/    AppLayout(사이드바 · 상단 바) · menu.js(메뉴 정의) · MenuGuard
│  └─ common/    StatusTag · useNotify · applyFieldErrors · ApprovalSteps · FileUpload · useFileUrl
├─ pages/        public/(로그인 · 비밀번호 변경 · 첫 화면), 메뉴별 화면(me/, hr/, settings/)
├─ utils/        format.js(날짜 · 시각 · 시간(분) · 금액 · 요율)
├─ router.jsx    라우트 — 메뉴마다 MenuGuard, 만든 화면은 PAGES 에 등록
└─ theme.js      AntD 테마(디자인 v2 토큰이 나오면 여기만)
```

## 새 화면 붙이는 법

1. `pages/` 아래에 화면을 만든다(예: `pages/me/MyLeavesPage.jsx`).
2. `router.jsx`의 `PAGES`에 메뉴 키로 등록한다 — `{ 'my-leaves': MyLeavesPage }`. 메뉴 노출과 주소 접근이 `menu.js`의 같은 `visible`을 쓴다.
3. API 함수는 `api/<영역>.js`에 두고 `api.get/post/patch/put/delete`를 쓴다. 성공하면 `data`가 돌아오고, 실패하면 `ApiError { status, code, message, fields, details }`가 던져진다.

## 규칙 (프론트엔드 개발 가이드 v2 요약)

- 메뉴 · 탭 · 버튼은 `can()` / `canAny()`로 감싼다. 역할 이름으로 분기하지 않는다. 팀 범위는 조직장일 때만 유효.
- 코드값은 `labels.js`(`<StatusTag group code />`, `labelOf`, `optionsOf`), 에러 메시지는 `errors.js`(`useNotify().error(err)`).
- 입력칸 오류(`VALIDATION_ERROR`)는 `applyFieldErrors(form, err)`.
- 성공 응답의 `data.warning`은 `useNotify().success(text, data)`가 경고 토스트로 띄운다.
- 근태 시간 · 휴가 일수 · 급여 금액은 서버 값을 그대로 보여 준다. 화면에서 다시 계산하지 않는다.
- 응답에 없는 필드는 빈칸으로 두지 말고 칸을 숨긴다.
- 파일은 `<FileUpload purpose="RECEIPT" />`를 `Form.Item` 안에 넣는다(값 = fileId). 이미지 표시는 `useFileUrl(fileId)`, 열기 · 내려받기는 `openFile` · `downloadFile`(`api/files.js`) — `GET /files/{id}`는 토큰이 필요해서 `<img src>`에 바로 못 쓴다.
- 승인 진행은 `<ApprovalSteps steps={detail.approvalSteps} />`(연장근무는 `showMinutes`). 휴가 신청 미리보기의 `steps`도 그대로 넣으면 된다.

## 공통 컴포넌트 견본

개발 서버에서 `/dev/components`를 열면 `ApprovalSteps` · `FileUpload`를 예시 데이터로 볼 수 있다(빌드에는 안 들어감).

## 공통 컴포넌트 담당 (FE-A · FE-B 합의)

| 컴포넌트 | 담당 |
|---|---|
| A `SettingList` · B `HistoryCard` · C `SearchTable` | FE-A |
| `ApprovalSteps` · `FileUpload` | FE-B ✅ |

## 진행 상황

- [x] F-01 공통 기반 ① — 셋업 · 프록시 · API 클라이언트 · 토큰 · `/me` · 권한 판정 · 라우팅 가드(비밀번호 변경 · 초기 설정 잠금) · 레이아웃 · 권한별 메뉴와 배지 · 코드값 라벨 · 에러 메시지 · 포맷 · 로그인 · 비밀번호 변경
- [x] F-01 공통 기반 ② — `ApprovalSteps`(승인 진행 표시) · `FileUpload`(파일 올리기) · `useFileUrl` (FE-B)
- [ ] F-01 공통 기반 ③ — 화면 유형 A `SettingList` · B `HistoryCard` · C `SearchTable` (FE-A)
- [x] 로그인 · 회사 등록 Figma 디자인 — Auth_Login · Auth_Login_Fail · first_page · second_page

## 시작 흐름

- 로그인 안 한 사용자 → **로그인**(`/login`). `/welcome`도 로그인으로 넘긴다.
- 회사 등록은 로그인 화면에 링크가 없고 **`/signup` 주소로만** 들어온다(일반 직원은 회사를 등록하지 않는다).
  1단계 회사 정보 → 2단계 관리자 → 등록하면 그 관리자로 바로 로그인되어 초기 설정 마법사(`/setup`)로 간다.
- 마법사를 끝내지 못한 관리자는 로그인하면 다시 마법사로 간다(`next = SETUP_WIZARD`).

## Figma

- **전체 크기는 `src/styles.css`의 `--ui-scale` 하나로 조절한다**(지금 0.875). 우리 CSS 는 Figma px 를 rem 으로 적었고(1rem = Figma 16px), JSX 인라인 크기는 `rem(px)`(`utils/size.js`). AntD 크기는 `theme.js`의 `UI_SCALE`을 같은 값으로. 휴대폰 폭은 1(원래 크기)

- 파일: `Design` (`ton5xp30RtOBzmJoSRQHbF`). 내려받은 로고 · 아이콘은 `src/assets/figma/`.
- 로그인 · 회사 등록 화면 스타일은 `src/pages/public/auth.css`에 프레임 1440 × 1024 값을 그대로 옮겼다.
