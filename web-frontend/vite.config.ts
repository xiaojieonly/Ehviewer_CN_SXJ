import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  define: {
    __APP_VERSION__: JSON.stringify(process.env.WEB_VERSION || 'dev'),
  },
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        // VITE_PROXY_TARGET lets a dev server ride on a remote backend
        // (e.g. the LAN instance) without rebuilding anything.
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: (process.env.VITE_PROXY_TARGET || 'ws://localhost:8080').replace(/^http/, 'ws'),
        ws: true,
      },
    },
  },
  build: {
    outDir: '../anotherviewer-web/src/main/resources/static',
    // outDir is outside the project root, so Vite would otherwise leave
    // stale content-hashed bundles from previous builds piling up here.
    emptyOutDir: true,
    sourcemap: false,
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
      },
    },
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          axios: ['axios'],
        },
      },
    },
  },
})
