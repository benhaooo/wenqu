/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

const rootDir = path.resolve(import.meta.dirname, '..')

export default defineConfig(({ mode }) => {
  // 端口配置与后端共用根目录 .env
  const env = loadEnv(mode, rootDir, '')
  return {
    plugins: [react(), tailwindcss()],
    resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
    server: {
      port: Number(env.FRONTEND_PORT || 5173),
      strictPort: true,
      proxy: { '/api': `http://localhost:${env.BACKEND_PORT || '8080'}` },
    },
    test: { environment: 'jsdom' },
  }
})
