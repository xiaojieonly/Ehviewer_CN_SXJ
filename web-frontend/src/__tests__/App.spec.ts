import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'
import { createPinia, setActivePinia } from 'pinia'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import App from '../App.vue'

const { pushMock, routeState } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  routeState: { name: 'Homepage', path: '/', fullPath: '/' },
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: pushMock }),
}))

/**
 * App 的 router-view 用了 v-slot（KeepAlive 列表缓存）；mock 掉 vue-router
 * 后 `<router-view>` 无法解析，注册一个最小 stub：把 slot props 原样回传
 * （Component 恒 undefined → 不渲染视图）。
 */
const RouterViewStub = defineComponent({
  name: 'RouterView',
  setup(_: unknown, { slots }: { slots: { default?: (props: unknown) => unknown } }) {
    return () => slots.default?.({ Component: undefined, route: routeState })
  },
})

const mountApp = () => mount(App, { global: { components: { RouterView: RouterViewStub } } })

function appCss(): string {
  return readFileSync(resolve(process.cwd(), 'src/App.vue'), 'utf8')
}

describe('App (UX-13 hamburger reserved slot)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    pushMock.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it('maps the feed nav items to the home route with feed queries', async () => {
    wrapper = mountApp()
    const clickItem = async (label: string) => {
      const button = wrapper
        .findAll('[data-testid="drawer-item"]')
        .find((b) => b.text().includes(label))
      expect(button).toBeDefined()
      await button!.trigger('click')
      routeState.fullPath = pushMock.mock.calls.at(-1)?.[0] as string
    }

    await clickItem('订阅')
    expect(pushMock).toHaveBeenCalledWith('/?feed=subscription')

    await clickItem('热门')
    expect(pushMock).toHaveBeenCalledWith('/?feed=popular')

    await clickItem('排行榜')
    expect(pushMock).toHaveBeenCalledWith('/?feed=toplist')
  })

  it('does not re-push the currently active feed target', async () => {
    routeState.fullPath = '/?feed=popular'
    wrapper = mountApp()
    const hotButton = wrapper
      .findAll('[data-testid="drawer-item"]')
      .find((b) => b.text().includes('热门'))
    await hotButton!.trigger('click')
    expect(pushMock).not.toHaveBeenCalled()
  })

  it('renders the hamburger with its accessible label', () => {
    wrapper = mountApp()
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
