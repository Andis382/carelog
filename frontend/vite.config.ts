import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

// Where the API runs in development. The browser only ever talks to Vite, so the
// session and XSRF cookies stay first-party.
const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080'
const PORT = Number(process.env.PORT ?? 5173)

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: {
        name: 'CareLog',
        short_name: 'CareLog',
        description: 'A shared care log for the family and the carer of an elderly parent.',
        theme_color: '#10302a',
        background_color: '#f5f8f6',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        navigateFallbackDenylist: [/^\/api\//],
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: PORT,
    strictPort: true,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: false },
      '/sanctum': { target: BACKEND, changeOrigin: false },
    },
  },
  preview: {
    port: PORT,
    proxy: {
      '/api': { target: BACKEND, changeOrigin: false },
    },
  },
})
