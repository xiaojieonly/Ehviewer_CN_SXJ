import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import HomeView from '../HomeView.vue'
import { galleryApi } from '@/api/gallery'
import type { GalleryInfo } from '@/types'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('@/api/gallery', () => ({
  galleryApi: { search: vi.fn(), getQuickSearches: vi.fn() },
}))

function gallery(overrides: Partial<GalleryInfo> = {}): GalleryInfo {
  return {
    gid: 1,
    token: 'abc123',
    title: 'Sample',
    titleJpn: '',
    thumb: '',
    category: 2,
    posted: '2026-01-01',
    uploader: '',
    rating: 4,
    rated: false,
    simpleLanguage: '',
    simpleTags: [],
    thumbWidth: 0,
    thumbHeight: 0,
    pages: 10,
    favoriteSlot: -1,
    favoriteName: '',
    ...overrides,
  }
}

describe('HomeView (首页)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    pushMock.mockClear()
    vi.mocked(galleryApi.getQuickSearches).mockResolvedValue({ success: true, data: [] })
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  async function mountHome(list: GalleryInfo[]) {
    vi.mocked(galleryApi.search).mockResolvedValue({ success: true, data: list, total: list.length })
    wrapper = mount(HomeView)
    await flushPromises()
    return wrapper
  }

  function ctaButton(label: string) {
    return wrapper.findAll('button').find((b) => b.text() === label)
  }

  it('renders the guided CTA buttons when there is no gallery data (UX-07)', async () => {
    await mountHome([])

    expect(wrapper.find('[data-testid="content-state-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="content-state-empty"]').text()).toContain('还没有画廊数据')
    expect(ctaButton('去搜索')).toBeDefined()
    // Not authenticated → login CTA present.
    expect(ctaButton('登录')).toBeDefined()
  })

  it('navigates to /search when 去搜索 is tapped', async () => {
    await mountHome([])

    await ctaButton('去搜索')!.trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/search')
  })

  it('hides the login CTA when already authenticated', async () => {
    localStorage.setItem('token', 'test-token')
    await mountHome([])

    expect(ctaButton('去搜索')).toBeDefined()
    expect(ctaButton('登录')).toBeUndefined()
  })

  it('hides the CTA buttons when galleries are present', async () => {
    await mountHome([gallery()])

    expect(wrapper.find('[data-testid="content-state-empty"]').exists()).toBe(false)
    expect(ctaButton('去搜索')).toBeUndefined()
  })

  it('renders the full list when no viewport geometry is available (fallback)', async () => {
    const items = Array.from({ length: 120 }, (_, i) =>
      gallery({ gid: i + 1, title: `G${i + 1}` }),
    )
    await mountHome(items)

    // Headless environments expose zero clientHeight — the virtual window
    // stays off and every gallery is rendered, like before the feature.
    expect(wrapper.findAll('.app-card')).toHaveLength(120)
  })

  it('virtualizes the grid: renders only the visible rows plus overscan', async () => {
    // Grid math under test (list mode uses a fixed card-height estimate).
    localStorage.setItem('ehviewer-webui:gallery-list-mode', 'grid')
    const items = Array.from({ length: 200 }, (_, i) =>
      gallery({ gid: i + 1, title: `G${i + 1}` }),
    )
    await mountHome(items)

    const scroller = wrapper.find('.fast-scroller__container')
    expect(scroller.exists()).toBe(true)
    const el = scroller.element
    // 500px wide / 120px min column → 4 columns; 600px viewport → 3.2 rows.
    // scrollTop 1875px → row 10; window = rows 7..17 with 3 overscan rows.
    Object.defineProperty(el, 'clientHeight', { value: 600, configurable: true })
    Object.defineProperty(el, 'clientWidth', { value: 500, configurable: true })
    Object.defineProperty(el, 'scrollTop', { value: 1875, configurable: true })
    // Keep the load-more sentinel quiet (ContentLayout's scroller handler
    // compares scrollTop + clientHeight against scrollHeight).
    Object.defineProperty(el, 'scrollHeight', { value: 20000, configurable: true })

    window.dispatchEvent(new Event('resize'))
    await flushPromises()
    el.dispatchEvent(new Event('scroll'))
    await flushPromises()

    const cards = wrapper.findAll('.app-card')
    // Rows 7..17 × 4 columns = 40 cards, far fewer than the 200 galleries.
    expect(cards).toHaveLength(40)
    // Window starts at item index 28 (gid 29); items far below stay unmounted.
    expect(cards[0].text()).toContain('G29')
    expect(wrapper.text()).not.toContain('G150')

    // The spacers preserve the full 50-row scroll geometry around the window
    // (7 rows above, 10 window rows, 33 rows below × 187.5px row height).
    const spacers = wrapper.findAll('.home__virtual-spacer')
    expect(spacers).toHaveLength(2)
    expect(spacers[0].attributes('style')).toContain('1312.5px')
    expect(spacers[1].attributes('style')).toContain('6187.5px')
  })
})
