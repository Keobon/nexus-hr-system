import { App } from 'antd';
import { errorMessage, warningMessage } from '../../constants/errors';

/**
 * 버튼 동작 결과 토스트(프론트 가이드 2.1).
 * - error(err): 에러 코드 → 5장 메시지
 * - success(text, data): 성공 토스트, data.warning 이 있으면 경고 토스트를 더 띄운다
 */
export function useNotify() {
  const { message } = App.useApp();
  return {
    error: (err) => message.error(errorMessage(err)),
    success: (text, data) => {
      if (text) message.success(text);
      const warning = warningMessage(data?.warning);
      if (warning) message.warning(warning);
    },
  };
}
