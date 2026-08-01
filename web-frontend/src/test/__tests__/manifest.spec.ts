/**
 * UX-12 PWA manifest tests — reads the real public/manifest.json and locks
 * in the icon set (incl. 96×96) and standalone display mode.
 */
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const manifestPath = resolve(__dirname, '../../../public/manifest.json')
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8')) as {
  display: string
  icons: Array<{ src: string; sizes: string; type?: string; purpose?: string; form_factor?: string }>
}

describe('UX-12 PWA manifest', () => {
  it('icons includes a 96x96 entry', () => {
    expect(manifest.icons.some((icon) => icon.sizes === '96x96')).toBe(true)
  })

  it('96x96 icon points at /icons/icon-96.png as image/png', () => {
    const icon = manifest.icons.find((i) => i.sizes === '96x96')
    expect(icon?.src).toBe('/icons/icon-96.png')
    expect(icon?.type).toBe('image/png')
  })

  it('a regular (non-maskable) icon is marked form_factor: wide', () => {
    const wide = manifest.icons.find((icon) => icon.form_factor === 'wide')
    expect(wide).toBeDefined()
    expect(wide!.sizes).toBe('512x512')
    expect(wide!.purpose).toBeUndefined()
  })

  it('display is standalone', () => {
    expect(manifest.display).toBe('standalone')
  })

  it('still ships the original 192x192 and 512x512 icons', () => {
    expect(manifest.icons.some((i) => i.sizes === '192x192')).toBe(true)
    expect(manifest.icons.some((i) => i.sizes === '512x512')).toBe(true)
  })
})
