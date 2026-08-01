import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'
import { createPinia, setActivePinia } from 'pinia'
import { mount, type VueWrapper } from '@vue/test-utils'
import App from '../App.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ name: 'Homepage', path: '/' }),
  useRouter: () => ({ push: vi.fn() }),
}))

function appCss(): string {
  return readFileSync(resolve(process.cwd(), 'src/App.vue'), 'utf8')
}

describe('App (UX-13 hamburger reserved slot)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it('renders the hamburger with its accessible label', () => {
    wrapper = mount(App)
    const hamburger = wrapper.find('button.app-hamburger')
    expect(hamburger.exists()).toBe(true)
    expect(hamburger.attributes('aria-label')).toBe('打开导航菜单')
  })

  it('reserves a padding-left slot under the hamburger below 720px (UX-13)', () => {
    const css = appCss()
    // Narrow viewport media query must declare the hamburger slot.
    const narrowBlock = css.match(/@media \(max-width: 719px\) \{[^}]*\}/)?.[0]
    expect(narrowBlock).toBeDefined()
    expect(narrowBlock).toContain('padding-left: calc(48px + var(--safe-area-left))')
  })

  it('zeroes the slot padding at >=720px where the hamburger is hidden (UX-13)', () => {
    const css = appCss()
    const wideBlock = css.match(/@media \(min-width: 720px\) \{[\s\S]*?\n\}/)?.[0]
    expect(wideBlock).toBeDefined()
    expect(wideBlock).toContain('padding-left: 0')
    expect(css).toMatch(/@media \(min-width: 720px\) \{[\s\S]*\.app-hamburger \{\s*display: none;\s*\}\n\}/)
  })

  it('keeps safe-area insets on the fixed hamburger and the reserved slot (UX-13)', () => {
    const css = appCss()
    const hamburgerBlock = css.match(/\.app-hamburger \{[\s\S]*?\n\}/)?.[0]
    expect(hamburgerBlock).toContain('top: calc(8px + var(--safe-area-top))')
    expect(hamburgerBlock).toContain('left: calc(8px + var(--safe-area-left))')
  })

  it('does not reserve the slot on full-width chrome-less routes (login / reader)', () => {
    const css = appCss()
    const fullBlock = css.match(/\.app-content--full \{[\s\S]*?\n\}/)?.[0]
    expect(fullBlock).toContain('padding-left: 0')
  })
})
