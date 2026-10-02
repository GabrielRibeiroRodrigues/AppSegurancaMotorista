import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // Tipos antes compartilhados via workspace; agora locais.
      '@panic/shared': fileURLToPath(new URL('./src/shared.ts', import.meta.url)),
    },
  },
});
