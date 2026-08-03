/**
 * T-1 (第三轮复验回归) — Safari/WebKit list-row invisibility regression guard.
 *
 * Root cause: `.gallery-list__row` used `animation: item-in … both`; with
 * `fill-mode: both` an element whose entrance animation never triggers
 * (WebKit timing/class-swap quirk) stays stuck at the `from` keyframe
 * (opacity 0), so Safari painted the whole `/favorites` list blank while the
 * AX tree was intact. Grid mode (`.app-card`, fill `backwards`) was fine.
 *
 * Fix: fill-mode `backwards` on staggered entrances (rows, detail sections),
 * no fill on no-delay entrances (scrim/dialog/toast/card/hero), plus an
 * explicit natural-state `opacity: 1` on the element so it is visible
 * whenever the animation does not run.
 *
 * These are STATIC regression assertions: happy-dom does not compute styles
 * from scoped `<style>` blocks (`getComputedStyle` returns "" for animation
 * and opacity — verified by probe), and the rows carry no inline opacity
 * (the entrance lives in CSS). So we pin the CSS source itself instead of
 * rendered computed styles.
 */
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const viewsDir = resolve(__dirname, '../../views')

function viewSource(name: string): string {
  return readFileSync(resolve(viewsDir, name), 'utf8')
}

/** CSS without comment blocks (prose must not trip keyword scans). */
function stripComments(css: string): string {
  return css.replace(/\/\*[\s\S]*?\*\//g, '')
}

/** Rule blocks for `selector` in the scoped style, comments stripped. */
function ruleBlocks(source: string, selector: string): string[] {
  const matches = stripComments(source).matchAll(new RegExp(`\\.${selector}\\s*\\{([^}]*)\\}`, 'g'))
  return [...matches].map((m) => m[1])
}

/** The base rule block for `selector` that declares the entrance animation. */
function ruleBlock(source: string, selector: string): string {
  const blocks = ruleBlocks(source, selector)
  if (blocks.length === 0) throw new Error(`no rule block found for .${selector}`)
  const animated = blocks.find((b) => /animation:/.test(b))
  return animated ?? blocks[0]
}

function collapse(block: string): string {
  return block.replace(/\s+/g, ' ').trim()
}

/** Element must keep an explicit natural-state opacity of 1. */
function expectNaturalState(block: string) {
  expect(block).toMatch(/opacity:\s*1;/)
}

/** `backwards` is the only permitted fill keyword in this rule block. */
function expectFillAtMostBackwards(block: string) {
  const withoutBackwards = block.replace(/backwards/g, '')
  expect(withoutBackwards).not.toMatch(/\b(both|forwards)\b/)
}

/** No fill keyword at all (no-delay entrance: fill would only risk stuck opacity 0). */
function expectNoFill(block: string) {
  expect(block).not.toMatch(/\b(both|backwards|forwards)\b/)
}

const LIST_ROWS: Array<{
  view: string
  selector: string
  shorthand: string
}> = [
  { view: 'FavoriteView.vue', selector: 'gallery-list__row', shorthand: 'animation: item-in 240ms var(--ease-decelerate-quart) backwards' },
  { view: 'HistoryView.vue', selector: 'gallery-list__row', shorthand: 'animation: item-in 240ms var(--ease-decelerate-quart) backwards' },
  { view: 'DownloadView.vue', selector: 'download-list__item', shorthand: 'animation: item-in 240ms var(--ease-decelerate-quart) backwards' },
]

describe('T-1 list entrance rows (Safari blank-list regression)', () => {
  for (const row of LIST_ROWS) {
    const source = viewSource(row.view)
    const block = ruleBlock(source, row.selector)

    it(`${row.view} keeps the 240ms entrance animation with a fade from opacity 0`, () => {
      expect(source).toMatch(/@keyframes item-in/)
      expect(block).toMatch(/animation:\s*item-in\s+240ms/)
      expect(source).toMatch(/from\s*\{\s*opacity:\s*0/)
    })

    it(`${row.view} uses fill backwards (not both) + natural-state opacity 1`, () => {
      expect(collapse(block)).toContain(row.shorthand)
      expect(block).not.toMatch(/opacity:\s*0/)
      expectNaturalState(block)
      expectFillAtMostBackwards(block)
    })

    it(`${row.view} still staggers rows via inline animationDelay`, () => {
      expect(source).toMatch(/animationDelay/)
    })
  }
})

describe('T-1 no-delay entrance overlays (scrim/dialog/toast/card/hero)', () => {
  const overlays: Array<{ view: string; selector: string; shorthand: string }> = [
    { view: 'HistoryView.vue', selector: 'dialog-scrim', shorthand: 'animation: scrim-in 160ms linear' },
    { view: 'HistoryView.vue', selector: 'dialog', shorthand: 'animation: dialog-in 200ms var(--ease-decelerate-quart)' },
    { view: 'DownloadView.vue', selector: 'dialog-scrim', shorthand: 'animation: scrim-in 160ms linear' },
    { view: 'DownloadView.vue', selector: 'dialog', shorthand: 'animation: dialog-in 200ms var(--ease-decelerate-quart)' },
    { view: 'DownloadView.vue', selector: 'toast', shorthand: 'animation: toast-in 220ms var(--ease-decelerate-quint)' },
    { view: 'LoginView.vue', selector: 'login-card', shorthand: 'animation: card-in var(--duration-scene-translate) var(--ease-decelerate-quint)' },
    { view: 'GalleryDetailView.vue', selector: 'detail-header__hero', shorthand: 'animation: rise var(--duration-scene-translate) var(--ease-decelerate-quint)' },
  ]

  for (const overlay of overlays) {
    const block = ruleBlock(viewSource(overlay.view), overlay.selector)

    it(`${overlay.view} .${overlay.selector} keeps its entrance animation`, () => {
      expect(collapse(block)).toContain(overlay.shorthand)
    })

    it(`${overlay.view} .${overlay.selector} has no fill-mode and opacity 1`, () => {
      expectNaturalState(block)
      expectNoFill(block)
    })
  }
})

describe('T-1 staggered detail sections', () => {
  const block = ruleBlock(viewSource('GalleryDetailView.vue'), 'gallery-detail__body > section')

  it('uses fill backwards (not both) with natural-state opacity 1', () => {
    expect(collapse(block)).toContain(
      'animation: rise var(--duration-scene-translate) var(--ease-decelerate-quint) backwards',
    )
    expect(block).not.toMatch(/opacity:\s*0/)
    expectNaturalState(block)
    expectFillAtMostBackwards(block)
  })

  it('keeps the 60/130/200ms stagger delays', () => {
    const source = viewSource('GalleryDetailView.vue')
    for (const delay of ['60ms', '130ms', '200ms']) {
      expect(source).toMatch(`animation-delay: ${delay}`)
    }
  })
})

describe('T-1 sweep: no `both`/forwards fill anywhere in views', () => {
  const views = [
    'FavoriteView.vue',
    'HistoryView.vue',
    'DownloadView.vue',
    'GalleryDetailView.vue',
    'LoginView.vue',
  ]

  for (const view of views) {
    it(`${view} has no fill-mode both in its styles`, () => {
      const css = stripComments(viewSource(view))
      expect(css).not.toMatch(/animation-fill-mode:\s*both/)
      expect(css).not.toMatch(/animation:\s*[^;]*\bboth\b/)
    })
  }
})
