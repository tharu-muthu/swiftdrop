import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

export default defineConfig(({ mode }) => {
  const rootEnv = fileURLToPath(new URL('..', import.meta.url));
  // Read only the proxy target. Credentials never enter browser code.
  const env = loadEnv(mode, rootEnv, 'BACKEND_');
  const proxy = {
    '/api': {
      target: env.BACKEND_URL || 'http://localhost:8081',
      changeOrigin: true,
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
