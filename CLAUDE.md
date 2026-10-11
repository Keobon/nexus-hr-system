# CLAUDE.md

## 커밋 · PR

- **Claude 공동 작업자 표시를 넣지 않는다.** 커밋 메시지에 `Co-Authored-By: Claude …` 줄을, PR 본문에 `🤖 Generated with Claude Code` 줄을 붙이지 않는다.
- 브랜치는 `feat/be-*` · `feat/fe-<번호>-<내용>`, PR 대상은 `develop`.

## 프론트 (`frontend/`)

- 화면은 Figma(`Design` 파일)를 그대로 따른다. 규칙은 노션 프론트엔드 개발 가이드 v2 · 메뉴 구조 v2.
- 메뉴를 숨기는 조건은 `src/components/layout/menu.js` 한 곳에만 둔다(화면마다 넣지 않는다).
  예: 최고 관리자는 "인사 관리" 메뉴 묶음을 보지 않는다.
- 인사 업무(직원 등록 · 휴가 부여 · 급여 정산 · 평가 운영) 시연은 인사 담당 계정으로 한다 — 데모 데이터 정수아 `sua.jung@nexuslabs.example`.
  새 회사 등록을 시연할 때는 마법사 마지막 단계(⑦ 직원 등록)에서 인사 담당 역할 직원을 한 명 같이 등록한다.
