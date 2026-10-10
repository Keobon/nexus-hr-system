import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// /api 요청은 백엔드로 넘긴다(프론트 가이드 2.1). 주소가 다르면 .env.local 의 VITE_API_TARGET.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target: env.VITE_API_TARGET || 'http://localhost:8080', changeOrigin: true },
      },
    },
  };
});
