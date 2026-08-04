import { defineConfig } from '@playwright/test'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

// This config lives in web-frontend/e2e/ alongside the visual-regression
// config (playwright.config.ts). The two suites are disjoint by testMatch:
// visual|fixed-bounds there, drawer here.
const __dirname = dirname(fileURLToPath(import.meta.url))

// W4 抽屉回归连接「已在运行」的 WebUI 后端 —— 任务约定 :8085（该实例自带
// ANOTHERVIEWER_GALLERY_MOCK_BASE_URL 环境）。因此这里 **不** 配 webServer
// 自起 vite（那是 visual 套件的事）；后端不可达时 spec 的 beforeAll 探针
// 会给出带指引的失败。baseURL 参数化：E2E_BASE_URL 可指向任意实例。
const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:8085'

export default defineConfig({
  testDir: __dirname,
  testMatch: /drawer\.spec\.ts/,
  // Chromium only — matches the visual suite's deterministic reference target
  // (Android WebView/Chrome rendering parity).
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  timeout: 30_000,
  outputDir: resolve(__dirname, 'test-results-drawer'),
  use: {
    baseURL: BASE_URL,
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
  },
})
