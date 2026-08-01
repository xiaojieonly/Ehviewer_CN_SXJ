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
})
