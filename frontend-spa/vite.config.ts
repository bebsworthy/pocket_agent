import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  define: {
    __DEV__: JSON.stringify(process.env.NODE_ENV === 'development'), // Enable development mode flags
  },
  build: {
    target: 'es2015',
    minify: 'esbuild',
    sourcemap: true, // Enable sourcemaps for production too
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom'],
          'router-vendor': ['react-router-dom'],
          'state-vendor': ['jotai'],
          'ui-vendor': ['lucide-react'],
        },
      },
    },
    chunkSizeWarningLimit: 1000,
  },
  css: {
    devSourcemap: true, // Enable CSS sourcemaps in dev
  },
  esbuild: {
    sourcemap: true, // Enable esbuild sourcemaps
  },
  server: {
    port: 3000,
    host: true,
  },
  preview: {
    port: 3000,
    host: true,
  },
});
