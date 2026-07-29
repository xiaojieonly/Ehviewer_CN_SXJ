import { defineConfig } from '@playwright/test'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

// This config lives in web-frontend/e2e/. The Vite dev server (`npm run dev`)
// must be spawned from the web-frontend package root (the parent directory).
const __dirname = dirname(fileURLToPath(import.meta.url))
const packageRoot = resolve(__dirname, '..')

const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:3000'

/**
 * Visual-regression-only Playwright config.
 *
 * - Chromium only (WebKit/Firefox are intentionally out of scope — the goal is
 *   to match the Android WebView/Chrome rendering, and Chromium is the
 *   deterministic reference target on this machine).
 * - Single worker, serial execution: screenshots must be captured in a stable,
 *   reproducible order with no concurrent load on the dev server.
 * - `webServer` auto-starts `npm run dev` so the suite never assumes an
 *   externally-running server. `reuseExistingServer` lets a developer who
 *   already has the dev server up avoid a second boot.
 */
export default defineConfig({
  testDir: __dirname,
  testMatch: /visual\.spec\.ts/,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  timeout: 90_000,
  outputDir: resolve(__dirname, 'test-results'),
  use: {
    baseURL: BASE_URL,
    trace: 'off',
    video: 'off',
    screenshot: 'off',
  },
  webServer: {
    command: 'npm run dev',
    cwd: packageRoot,
    url: BASE_URL,
    reuseExistingServer: true,
    timeout: 180_000,
  },
})
