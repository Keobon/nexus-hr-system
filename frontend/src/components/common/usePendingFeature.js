import { App } from 'antd';

/**
 * 증빙 승인 요청(추가사항 ②) — 백엔드에 변경 요청 · 승인 API 가 아직 없다.
 * 화면은 Figma 대로 다 만들고, 보내기만 막아 안내한다. API 가 생기면 이 한 곳을 실제 호출로 바꾼다.
 */
export function usePendingFeature() {
  const { modal } = App.useApp();
  // feature: 안내에 쓸 기능 이름(증빙 승인 외에 증명서 발급 · 2차 인증처럼 백엔드에 없는 기능도 같은 안내)
  return (title = '승인 요청', feature = '증빙 제출 · 인사팀 승인') =>
    modal.info({
      title: `${title} 준비 중`,
      content: `${feature} 기능은 백엔드 연결 후 사용할 수 있습니다. 지금 입력한 내용은 저장되지 않습니다.`,
      okText: '확인',
    });
}
