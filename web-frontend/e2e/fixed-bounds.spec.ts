import { test, expect } from '@playwright/test'

/* -------------------------------------------------------------------------- */
/* UX-01 regression: no `position: fixed` element may be clipped by the       */
/* viewport on any key route at any supported viewport size.                  */
/*                                                                            */
/* Mirrors the setup of visual.spec.ts (same auth-token init script, same     */
/* animation freeze, same server bootstrap via e2e/playwright.config.ts).     */
/* -------------------------------------------------------------------------- */

interface Viewport {
  id: string
  width: number
  height: number
}

interface RouteEntry {
  /** Router path (see src/router/index.ts) */
  path: string
  /** Filesystem-safe slug used in error messages */
  slug: string
}

const VIEWPORTS: Viewport[] = [
  // Modern phone (CSS px, e.g. iPhone 12/13/14 logical size)
  { id: 'phone-390x844', width: 390, height: 844 },
  // Desktop-class
  { id: 'desktop-1280x800', width: 1280, height: 800 },
  // Extremely narrow phone — tightest horizontal clipping check
  { id: 'narrow-320x568', width: 320, height: 568 },
]

const ROUTES: RouteEntry[] = [
  { path: '/', slug: 'home' },
  { path: '/search', slug: 'search' },
  { path: '/settings', slug: 'settings' },
  { path: '/settings/reader', slug: 'settings-reader' },
  { path: '/admin/proxy', slug: 'admin-proxy' },
  { path: '/admin/devices', slug: 'admin-devices' },
  { path: '/downloads', slug: 'downloads' },
  { path: '/favorites', slug: 'favorites' },
  { path: '/history', slug: 'history' },
  { path: '/login', slug: 'login' },
  { path: '/smb-backup', slug: 'smb-backup' },
]

/* -------------------------------------------------------------------------- */
/* Init script — same rationale as visual.spec.ts:                            */
/*  1. Pin the theme (localStorage key the theme store reads on init).        */
/*  2. Set a dummy auth token so the router guard mounts protected routes     */
/*     instead of redirecting everything to /login.                           */
/*  3. Freeze animations/transitions so FAB expand/collapse states are        */
/*     deterministic (collapsed mini-FABs stay at scale(0)).                  */
/* -------------------------------------------------------------------------- */

const STABILIZE_CSS =
  '*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important;scroll-behavior:auto!important}'

function initScript({ theme, css }: { theme: string; css: string }) {
  try {
    window.localStorage.setItem('anotherviewer-theme', theme)
    window.localStorage.setItem('token', 'e2e-fixed-bounds-token')
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

/* -------------------------------------------------------------------------- */
/* Browser-side check                                                         */
/* -------------------------------------------------------------------------- */

interface Offender {
  selector: string
  rect: string
}

/**
 * Returns every `position: fixed` element whose bounding rect falls outside
 * the viewport (by more than the tolerance), excluding elements that are
 * intentionally not rendered:
 *  - the element or any ancestor has display:none / visibility:hidden /
 *    opacity:0 (covers closed dialogs and hidden surfaces)
 *  - the element itself has pointer-events:none (closed dialog container)
 *  - the rect is 0x0 (collapsed FABs at scale(0) collapse to a point —
 *    nothing visible to clip)
 *  - the rect is fully outside the viewport AND the element or an ancestor
 *    carries a transform — the off-canvas drawer pattern (e.g. the closed
 *    navigation drawer parked at translateX(-100%)). A partial overlap is
 *    never skipped: that is exactly the clipping bug this suite guards.
 */
function findClippedFixedElements(): Offender[] {
  const offenders: Offender[] = []
  const vw = window.innerWidth
  const vh = window.innerHeight
  // 1px tolerance absorbs subpixel rounding of computed positions.
  const tolerance = 1

  for (const el of Array.from(document.querySelectorAll('body *'))) {
    if (getComputedStyle(el).position !== 'fixed') continue

    let node: Element | null = el
    let hidden = false
    while (node) {
      const cs = getComputedStyle(node)
      if (
        cs.display === 'none' ||
        cs.visibility === 'hidden' ||
        Number(cs.opacity) === 0
      ) {
        hidden = true
        break
      }
      node = node.parentElement
    }
    if (hidden) continue
    if (getComputedStyle(el).pointerEvents === 'none') continue

    const rect = el.getBoundingClientRect()
    if (rect.width === 0 && rect.height === 0) continue

    const fullyOutside =
      rect.right <= 0 || rect.left >= vw || rect.bottom <= 0 || rect.top >= vh
    if (fullyOutside) {
      let transformed = false
      node = el
      while (node) {
        if (getComputedStyle(node).transform !== 'none') {
          transformed = true
          break
        }
        node = node.parentElement
      }
      if (transformed) continue
    }

    if (
      rect.left < -tolerance ||
      rect.top < -tolerance ||
      rect.right > vw + tolerance ||
      rect.bottom > vh + tolerance
    ) {
      const cls = Array.from(el.classList).join('.')
      offenders.push({
        selector: `${el.tagName.toLowerCase()}${el.id ? `#${el.id}` : ''}${cls ? `.${cls}` : ''}`,
        rect:
          `left=${rect.left.toFixed(1)} top=${rect.top.toFixed(1)} ` +
          `right=${rect.right.toFixed(1)} bottom=${rect.bottom.toFixed(1)} ` +
          `(viewport ${vw}x${vh})`,
      })
    }
  }

  return offenders
}

/* -------------------------------------------------------------------------- */
/* Tests                                                                      */
/* -------------------------------------------------------------------------- */

test.describe.configure({ mode: 'serial' })

for (const vp of VIEWPORTS) {
  for (const route of ROUTES) {
    test(`no clipped fixed elements on ${route.slug} @ ${vp.id}`, async ({ browser }) => {
      const context = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        deviceScaleFactor: 1,
        reducedMotion: 'reduce',
      })
      await context.addInitScript(initScript, { theme: 'light', css: STABILIZE_CSS })
      const page = await context.newPage()
      try {
        // 'load' (not 'networkidle'): same rationale as visual.spec.ts — a
        // sockjs/stomp reconnect loop keeps the network perpetually active.
        await page.goto(route.path, { waitUntil: 'load' })
        // Let late synchronous renders / store init settle before measuring.
        await page.waitForTimeout(400)

        const offenders = await page.evaluate(findClippedFixedElements)
        const detail = offenders.map((o) => `  - ${o.selector} → ${o.rect}`).join('\n')
        expect(
          offenders,
          `Clipped position:fixed elements on ${route.path} @ ${vp.id}:\n${detail}`,
        ).toEqual([])
      } finally {
        await context.close()
      }
    })
  }
}
