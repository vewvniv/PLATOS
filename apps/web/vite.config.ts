import { resolve } from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // A fonte servida e a mesma que o KMP embarca, lida do lugar onde ela e versionada. Copiar o TTF
  // para dentro de `apps/web` criaria uma segunda copia que pode divergir em silencio — e a fonte
  // e justamente o que precisa ser identico entre plataformas (D36).
  publicDir: resolve(__dirname, '../../packages/domain/fonts'),
  test: {
    environment: 'node',
  },
});
