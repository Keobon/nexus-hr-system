import React from 'react';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import koKR from 'antd/locale/ko_KR';
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css';
import { setAuthHandlers } from './api/client';
import { errorMessage } from './constants/errors';
import { router } from './router';
import { theme } from './theme';
import './styles.css';

// 401 → 로그인(만료면 안내 문구), 비밀번호 변경 필요 → /password (프론트 가이드 2.1)
setAuthHandlers({
  unauthorized: (err) => {
    if (window.location.pathname !== '/login') {
      router.navigate('/login', { replace: true, state: { reason: errorMessage(err) } });
    }
  },
  passwordChangeRequired: () => router.navigate('/password', { replace: true }),
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 4xx 는 다시 시도해도 같다. 네트워크 · 500 만 한 번 더
      retry: (count, err) => count < 1 && (!err?.status || err.status >= 500),
      refetchOnWindowFocus: false,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider locale={koKR} theme={theme}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
