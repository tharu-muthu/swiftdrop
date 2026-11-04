import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

export default defineConfig(({ mode }) => {
  const frontendEnv = fileURLToPath(new URL('.', import.meta.url));
  // Only read frontend/.env files for the proxy; never parse root/backend credentials.
  const env = loadEnv(mode, frontendEnv, 'BACKEND_');
  const proxy = {
    '/api': {
      target: env.BACKEND_URL || 'http://localhost:8081',
      changeOrigin: true,
    },
    '/oauth2': {
      target: env.BACKEND_URL || 'http://localhost:8081',
      changeOrigin: false,
    },
    '/login/oauth2': {
      target: env.BACKEND_URL || 'http://localhost:8081',
      changeOrigin: false,
    },
  };

  return {
    plugins: [react()],
    server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.js',
      restoreMocks: true,
      unstubGlobals: true,
    },
  };
});
