/**
 * UX-09 contrast regression tests.
 *
 * Verifies that every theme defines a small-text primary color that clears
 * WCAG AA (4.5:1) against its surface and background tokens, and that the
 * light-theme secondary text token no longer sits at the 4.5:1 borderline.
 * Reads the real token source (src/styles/tokens.css) so the tests stay in
 * sync with the theme blocks.
 */
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const tokensPath = resolve(__dirname, '../../styles/tokens.css')
const tokensCss = readFileSync(tokensPath, 'utf8')

/** sRGB channel (0-255) → linear light. */
function linearize(channel: number): number {
  const c = channel / 255
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
}

/** Relative luminance per WCAG 2.x (R,G,B ∈ [0,255]). */
export function relativeLuminance(hex: string): number {
  const value = hex.replace('#', '')
  if (!/^[0-9a-fA-F]{6}$/.test(value)) {
    throw new Error(`expected a #rrggbb hex color, got "${hex}"`)
  }
  const r = linearize(parseInt(value.slice(0, 2), 16))
  const g = linearize(parseInt(value.slice(2, 4), 16))
  const b = linearize(parseInt(value.slice(4, 6), 16))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG contrast ratio between two #rrggbb colors. */
export function computeContrastRatio(a: string, b: string): number {
  const l1 = relativeLuminance(a)
  const l2 = relativeLuminance(b)
  const [hi, lo] = l1 >= l2 ? [l1, l2] : [l2, l1]
  return (hi + 0.05) / (lo + 0.05)
}

/** Raw source of one theme block. */
function themeBlock(block: 'light' | 'dark' | 'black'): string {
  const start = tokensCss.indexOf(`[data-theme="${block}"]`)
  expect(start, `theme block [data-theme="${block}"] exists`).toBeGreaterThan(-1)
  const end = tokensCss.indexOf('/* ======', start)
  return tokensCss.slice(start, end > start ? end : undefined)
}

/** Extract the value of a custom property from one theme block. */
function themeVar(block: 'light' | 'dark' | 'black', name: string): string {
  const match = themeBlock(block).match(new RegExp(`${name}:\\s*([^;]+);`))
  expect(match, `${name} defined in ${block} theme`).not.toBeNull()
  return match![1].trim()
}

describe('UX-09 --color-primary-text tokens', () => {
  const wcagAA = 4.5

  const cases: Array<{
    theme: 'light' | 'dark' | 'black'
    primaryText: string
    surface: string
    bg: string
  }> = [
    {
      theme: 'light',
      primaryText: themeVar('light', '--color-primary-text'),
      surface: themeVar('light', '--color-surface'),
      bg: themeVar('light', '--color-bg'),
    },
    {
      theme: 'dark',
      primaryText: themeVar('dark', '--color-primary-text'),
      surface: themeVar('dark', '--color-surface'),
      bg: themeVar('dark', '--color-bg'),
    },
    {
      theme: 'black',
      primaryText: themeVar('black', '--color-primary-text'),
      surface: themeVar('black', '--color-surface'),
      bg: themeVar('black', '--color-bg'),
    },
  ]

  for (const { theme, primaryText, surface, bg } of cases) {
    it(`${theme}: --color-primary-text (${primaryText}) ≥ 4.5:1 on surface (${surface})`, () => {
      expect(computeContrastRatio(primaryText, surface)).toBeGreaterThanOrEqual(wcagAA)
    })

    it(`${theme}: --color-primary-text (${primaryText}) ≥ 4.5:1 on bg (${bg})`, () => {
      expect(computeContrastRatio(primaryText, bg)).toBeGreaterThanOrEqual(wcagAA)
    })
  }

  it('light: --color-primary-text is teal_700 (#00796b) so --color-primary is untouched', () => {
    expect(themeVar('light', '--color-primary-text')).toBe('#00796b')
    // --color-primary stays a base token (teal_500) — not overridden in any theme block.
    expect(tokensCss).toMatch(/--color-primary:\s*#009688/)
    expect(themeBlock('light')).not.toMatch(/--color-primary:/)
  })

  it('dark: --color-primary-text is #4db6ac (teal_300) and passes on both backgrounds', () => {
    expect(themeVar('dark', '--color-primary-text')).toBe('#4db6ac')
    expect(computeContrastRatio('#4db6ac', '#3a3a3a')).toBeGreaterThanOrEqual(wcagAA)
    expect(computeContrastRatio('#4db6ac', '#323232')).toBeGreaterThanOrEqual(wcagAA)
  })

  it('black: --color-primary-text matches dark and passes on both backgrounds', () => {
    expect(themeVar('black', '--color-primary-text')).toBe(themeVar('dark', '--color-primary-text'))
    expect(computeContrastRatio('#4db6ac', '#191919')).toBeGreaterThanOrEqual(wcagAA)
    expect(computeContrastRatio('#4db6ac', '#000000')).toBeGreaterThanOrEqual(wcagAA)
  })
})

describe('UX-09 light theme secondary text', () => {
  it('no longer uses rgba(0,0,0,0.54) (4.5:1 borderline)', () => {
    expect(themeVar('light', '--text-color-secondary')).not.toContain('0.54')
  })

  it('--drawable-color-primary (icon usage, 3:1 target) is untouched', () => {
    expect(themeVar('light', '--drawable-color-primary')).toBe('rgba(0, 0, 0, 0.54)')
  })

  it('the pure computation is correct: #00796b on #f5f5f5 ≈ 4.88:1 and on #ffffff ≈ 5.32:1', () => {
    expect(computeContrastRatio('#00796b', '#f5f5f5')).toBeCloseTo(4.88, 1)
    expect(computeContrastRatio('#00796b', '#ffffff')).toBeCloseTo(5.32, 1)
  })
})
