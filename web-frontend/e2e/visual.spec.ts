import { test, expect } from '@playwright/test'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import fs from 'node:fs'

const __dirname = dirname(fileURLToPath(import.meta.url))

/* -------------------------------------------------------------------------- */
/* Capture matrix: routes × themes × viewports                                */
/* -------------------------------------------------------------------------- */

type Theme = 'light' | 'dark' | 'black'

interface Viewport {
  id: string
  width: number
  height: number
}

interface RouteEntry {
  /** Router path (see src/router/index.ts) */
  path: string
  /** Filesystem-safe slug used in the screenshot filename */
  slug: string
}

const THEMES: Theme[] = ['light', 'dark', 'black']

const VIEWPORTS: Viewport[] = [
  // iPad-class landscape/portrait tablet (CSS px)
  { id: 'ipad-1024x1366', width: 1024, height: 1366 },
  // Modern phone (CSS px, e.g. iPhone 12/13/14 logical size)
  { id: 'phone-390x844', width: 390, height: 844 },
  // 4:3 landscape desktop-class tablet
  { id: 'tablet-1024x768', width: 1024, height: 768 },
  // Extremely narrow phone — hamburger collision + tab overflow checks
  { id: 'narrow-320x568', width: 320, height: 568 },
]

const ROUTES: RouteEntry[] = [
  { path: '/', slug: 'home' },
  { path: '/favorites', slug: 'favorites' },
  { path: '/history', slug: 'history' },
  { path: '/downloads', slug: 'downloads' },
  { path: '/settings', slug: 'settings' },
  { path: '/login', slug: 'login' },
  { path: '/settings/general', slug: 'settings-general' },
  { path: '/settings/reader', slug: 'settings-reader' },
  { path: '/settings/privacy', slug: 'settings-privacy' },
  { path: '/settings/transfer', slug: 'settings-transfer' },
  { path: '/admin/download', slug: 'admin-download' },
  { path: '/admin/server', slug: 'admin-server' },
  { path: '/admin/devices', slug: 'admin-devices' },
  { path: '/admin/proxy', slug: 'admin-proxy' },
  { path: '/admin/access', slug: 'admin-access' },
  { path: '/admin/processing', slug: 'admin-processing' },
  { path: '/admin/advanced', slug: 'admin-advanced' },
  { path: '/admin/about', slug: 'admin-about' },
  { path: '/reader/12345', slug: 'reader' },
]

/* -------------------------------------------------------------------------- */
/* Output mode                                                                */
/* -------------------------------------------------------------------------- */
//
// VISUAL_MODE=update  → write screenshots into e2e/baseline/  (test:visual:update)
// (anything else)     → write screenshots into e2e/actual/    (test:visual)
//
// The compare script (e2e/compare.mjs) then diffs actual/ against baseline/.

const IS_UPDATE = process.env.VISUAL_MODE === 'update'
const OUT_DIR = resolve(__dirname, IS_UPDATE ? 'baseline' : 'actual')

/**
 * Injected before any app script runs. Two jobs:
 *  1. Pin the theme by writing the same localStorage key the theme store reads
 *     on init (`anotherviewer-theme`, see src/stores/theme.ts). This is more robust
 *     than poking `data-theme` directly because the store applies it and keeps
 *     the <meta theme-color> in sync.
 *  2. Set a dummy auth token so the router guard (src/router/index.ts) lets the
 *     protected routes mount their own component (in its empty/loading state)
 *     instead of every route redirecting to /login. This is NOT mocking data —
 *     the views still render their real empty/error state against the (absent)
 *     backend; we only bypass the auth redirect so each route is distinguishable.
 *  3. Freeze animations/transitions for pixel-stable captures.
 */
const STABILIZE_CSS =
  '*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important;scroll-behavior:auto!important}'

/**
 * Init script (runs in the browser before any app script). Passed as a real
 * function so Playwright can bind the `{ theme, css }` argument — string
 * scripts cannot take arguments.
 */
function initScript({ theme, css }: { theme: string; css: string }) {
  try {
    window.localStorage.setItem('anotherviewer-theme', theme)
    window.localStorage.setItem('token', 'e2e-visual-regression-token')
  } catch {
    /* storage may be unavailable; non-fatal */
  }
  const apply = () => {
    const s = document.createElement('style')
    s.setAttribute('data-e2e-stabilize', '')
    s.textContent = css
    document.head.appendChild(s)
  }
  if (document.head) apply()
  else document.addEventListener('DOMContentLoaded', apply)
}

// Serial, single-worker: deterministic capture order, no concurrent server load.
test.describe.configure({ mode: 'serial' })

test('capture visual screenshots for every route × theme × viewport', async ({ browser }) => {
  fs.mkdirSync(OUT_DIR, { recursive: true })

  let captured = 0
  for (const vp of VIEWPORTS) {
    for (const theme of THEMES) {
      const context = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        // Fixed DPR keeps the raster dimensions identical across runs so the
        // pixel-by-pixel comparison is well-defined. (When swapping in real
        // Android baselines, capture them at a matching viewport + DPR — see
        // e2e/README.md.)
        deviceScaleFactor: 1,
        reducedMotion: 'reduce',
      })
      await context.addInitScript(initScript, { theme, css: STABILIZE_CSS })
      const page = await context.newPage()

      for (const route of ROUTES) {
        // 'load' (not 'networkidle'): a sockjs/stomp reconnect loop or a failing
        // API proxy could otherwise keep the network perpetually "active" and
        // stall networkidle forever. 'load' + a short settle is deterministic.
        await page.goto(route.path, { waitUntil: 'load' })
        // Let late synchronous renders / store init settle before the snapshot.
        await page.waitForTimeout(400)

        const name = `${route.slug}__${theme}__${vp.id}.png`
        await page.screenshot({
          path: resolve(OUT_DIR, name),
          fullPage: false, // capture the visible "screen", analogous to a device screenshot
          animations: 'disabled',
        })
        captured++
      }
      await context.close()
    }
  }

  console.log(
    `Captured ${captured} screenshots → ${OUT_DIR} ` +
      `(${ROUTES.length} routes × ${THEMES.length} themes × ${VIEWPORTS.length} viewports, mode=${IS_UPDATE ? 'update/baseline' : 'compare/actual'})`,
  )
})

// Review re-check: drawer open state on a narrow viewport — hamburger opens
// the modal drawer and the scrim is rendered (DOM assertion, no pixel diff).
test('drawer opens with scrim on narrow viewport', async ({ browser }) => {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 1,
    reducedMotion: 'reduce',
  })
  await context.addInitScript(initScript, { theme: 'light', css: STABILIZE_CSS })
  const page = await context.newPage()
  await page.goto('/', { waitUntil: 'load' })
  await page.waitForTimeout(400)

  const hamburger = page.locator('.app-hamburger')
  await expect(hamburger).toBeVisible()
  await hamburger.click()
  await page.waitForTimeout(300)

  await expect(page.locator('.navigation-drawer.is-open')).toBeVisible()
  await expect(page.locator('.navigation-drawer__scrim')).toBeVisible()

  fs.mkdirSync(OUT_DIR, { recursive: true })
  await page.screenshot({
    path: resolve(OUT_DIR, 'drawer-open__light__phone-390x844.png'),
    animations: 'disabled',
  })
  await context.close()
})
