import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { reactive } from 'vue'
import HomeView from '../HomeView.vue'
import SearchBar from '@/components/search/SearchBar.vue'
import FilterPanel from '@/components/search/FilterPanel.vue'
import { galleryApi } from '@/api/gallery'
import { siteApi } from '@/api/site'
import { preferencesApi } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import { availability, markUnknown } from '@/stores/availability'
import type { Preferences } from '@/api/preferences'
import type { GalleryInfo, GalleryListResponse, TopListItem } from '@/types'

const { pushMock, replaceMock, routeMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  replaceMock: vi.fn(),
  routeMock: { query: {} },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
  // Return a reactive wrapper so query mutations (via the cached proxy in
  // tests) re-trigger HomeView's feed watcher.
  useRoute: () => reactive(routeMock),
}))

vi.mock('@/api/gallery', () => ({
  galleryApi: {
    search: vi.fn(),
    feed: vi.fn(),
    getQuickSearches: vi.fn(),
    createQuickSearch: vi.fn(),
  },
}))

vi.mock('@/api/preferences', () => ({
  preferencesApi: { get: vi.fn(), update: vi.fn() },
}))

vi.mock('@/api/site', () => ({
  siteApi: { getAvailability: vi.fn(), probeAvailability: vi.fn() },
}))

/**
 * `vi.mocked(galleryApi.feed)` resolves to the last (toplist) overload, so the
 * subscription/popular mocks are typed through this list-overload alias.
 */
const feedListMock = vi.mocked(
  galleryApi.feed as (
    mode: 'subscription' | 'popular',
    page?: number,
    pageSize?: number,
  ) => Promise<GalleryListResponse>,
)

/** Legacy localStorage key of the pre-preferences list mode (B-1 migration). */
const LIST_MODE_KEY = 'anotherviewer-webui:gallery-list-mode'

/** Minimal Preferences fixture — schema keys optional (parallel-work keys). */
function makePrefs(general: Record<string, unknown>): Preferences {
  return { general } as unknown as Preferences
}

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

function toplistItem(overrides: Partial<TopListItem> = {}): TopListItem {
  return {
    gid: 1,
    token: 'abc123',
    tag: 'parody:one piece',
    value: '1000',
    href: 'https://e-hentai.org/g/1/abc123/',
    ...overrides,
  }
}

describe('HomeView (首页)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    pushMock.mockClear()
    replaceMock.mockClear()
    routeMock.query = {}
    // 熔断单例（模块级）在 spec 间持久——重置为初始未知态。
    availability.state = null
    availability.downAt = null
    availability.lastReason = null
    availability.lastLoadedAt = null
    markUnknown()
    vi.mocked(galleryApi.getQuickSearches).mockResolvedValue({ success: true, data: [] })
    // Default: preferences resolve with the grid layout (the pre-migration
    // tests below can override per case).
    vi.mocked(preferencesApi.get).mockResolvedValue(makePrefs({ listMode: 'grid' }))
    vi.mocked(preferencesApi.update).mockResolvedValue(makePrefs({}))
    // 默认站点状态 UP：横幅不出现（每个用例可按需覆盖）。
    vi.mocked(siteApi.getAvailability).mockResolvedValue({ state: 'UP' })
    vi.mocked(siteApi.probeAvailability).mockResolvedValue({ state: 'UP' })
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
    // Grid mode now comes from preferences, not localStorage (B-1).
    vi.mocked(preferencesApi.get).mockResolvedValue(makePrefs({ listMode: 'grid' }))
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

  it('loads the popular feed through galleryApi.feed when ?feed=popular', async () => {
    routeMock.query = { feed: 'popular' }
    feedListMock.mockResolvedValue({ success: true, data: [gallery()], total: 1 })
    wrapper = mount(HomeView)
    await flushPromises()

    expect(galleryApi.feed).toHaveBeenCalledWith('popular', 0, 25)
    expect(galleryApi.search).not.toHaveBeenCalled()
    expect(wrapper.find('.app-card').exists()).toBe(true)
  })

  it('loads the subscription feed when ?feed=subscription', async () => {
    routeMock.query = { feed: 'subscription' }
    feedListMock.mockResolvedValue({ success: true, data: [gallery()], total: 1 })
    wrapper = mount(HomeView)
    await flushPromises()

    expect(galleryApi.feed).toHaveBeenCalledWith('subscription', 0, 25)
    expect(galleryApi.search).not.toHaveBeenCalled()
  })

  it('renders ranked toplist rows when ?feed=toplist', async () => {
    routeMock.query = { feed: 'toplist' }
    vi.mocked(galleryApi.feed).mockResolvedValue({
      success: true,
      data: [
        toplistItem({ tag: 'parody:one piece', value: '999' }),
        toplistItem({ gid: 2, token: 'def456', tag: 'language:chinese', value: '42' }),
      ],
      total: 2,
    })
    wrapper = mount(HomeView)
    await flushPromises()

    expect(galleryApi.feed).toHaveBeenCalledWith('toplist', 0, 25)
    expect(galleryApi.search).not.toHaveBeenCalled()
    const rows = wrapper.findAll('[data-testid^="toplist-row-"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('parody:one piece')
    expect(rows[0].text()).toContain('999')
    expect(rows[1].text()).toContain('language:chinese')
    // The gallery grid is not rendered in toplist mode.
    expect(wrapper.find('.app-card').exists()).toBe(false)
  })

  it('keeps the search path when no feed query is present', async () => {
    vi.mocked(galleryApi.search).mockResolvedValue({ success: true, data: [gallery()], total: 1 })
    wrapper = mount(HomeView)
    await flushPromises()

    expect(galleryApi.search).toHaveBeenCalled()
    expect(galleryApi.feed).not.toHaveBeenCalled()
  })

  it('reloads page 0 with the new mode when the feed query changes', async () => {
    routeMock.query = { feed: 'subscription' }
    feedListMock.mockResolvedValue({ success: true, data: [gallery()], total: 1 })
    wrapper = mount(HomeView)
    await flushPromises()
    expect(galleryApi.feed).toHaveBeenCalledWith('subscription', 0, 25)

    // Mutate through Vue's cached proxy so the query watcher re-fires
    // (raw mutation of routeMock would bypass reactivity).
    reactive(routeMock).query = { feed: 'toplist' }
    await flushPromises()
    expect(galleryApi.feed).toHaveBeenLastCalledWith('toplist', 0, 25)
  })

  it('leaves the feed through ?keyword= when searching on a feed page (C2)', async () => {
    routeMock.query = { feed: 'popular' }
    feedListMock.mockResolvedValue({ success: true, data: [gallery()], total: 1 })
    wrapper = mount(HomeView)
    await flushPromises()

    // Frozen feed 分支不拿关键词发请求——搜索词改为 replace 到 `/?keyword=`
    // 深链形态（离开 feed、意图可见，由 keyword watcher 执行搜索）。
    wrapper.findComponent(SearchBar).vm.$emit('search', 'mahua')
    await flushPromises()
    expect(replaceMock).toHaveBeenCalledWith({ path: '/', query: { keyword: 'mahua' } })
    expect(galleryApi.search).not.toHaveBeenCalled()
  })

  it('runs the search when mounted with a ?keyword= deep link (C2)', async () => {
    routeMock.query = { keyword: 'artist:someone' }
    vi.mocked(galleryApi.search).mockResolvedValue({ success: true, data: [gallery()], total: 1 })
    wrapper = mount(HomeView)
    await flushPromises()

    expect(galleryApi.search).toHaveBeenCalledWith('artist:someone', undefined, 0, 25, undefined)
    expect(galleryApi.feed).not.toHaveBeenCalled()
  })

  it('re-runs the search when route.query.keyword changes (C2 — detail tag links)', async () => {
    vi.mocked(galleryApi.search).mockResolvedValue({ success: true, data: [], total: 0 })
    wrapper = mount(HomeView)
    await flushPromises()
    vi.mocked(galleryApi.search).mockClear()

    reactive(routeMock).query = { keyword: 'female:big' }
    await flushPromises()
    expect(galleryApi.search).toHaveBeenCalledWith('female:big', undefined, 0, 25, undefined)
  })

  it('debounces rapid filter edits into a single search (C6)', async () => {
    vi.useFakeTimers()
    await mountHome([gallery()])
    vi.mocked(galleryApi.search).mockClear()

    const panel = wrapper.findComponent(FilterPanel)
    panel.vm.$emit('update:filters', { sort: 2 })
    panel.vm.$emit('update:filters', { sort: 3 })
    await vi.advanceTimersByTimeAsync(400)
    expect(galleryApi.search).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(200)
    expect(galleryApi.search).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })

  it('saves a quick search from the FilterPanel action (C3 wiring)', async () => {
    vi.mocked(galleryApi.createQuickSearch).mockResolvedValue({
      id: 7,
      name: 'My preset',
      mode: 0,
      category: 0,
      keyword: '',
      advanceSearch: 0,
      minRating: 0,
      pageFrom: 0,
      pageTo: 0,
    })
    await mountHome([gallery()])

    wrapper.findComponent(FilterPanel).vm.$emit('save-quick-search')
    await flushPromises()
    // Save dialog teleports to body.
    const scrim = document.querySelector('.dialog-scrim')
    expect(scrim).not.toBeNull()
    await new DOMWrapper(scrim!.querySelector('.dialog__input')!).setValue('My preset')
    await new DOMWrapper(scrim!.querySelector('.dialog__btn--primary')!).trigger('click')
    await flushPromises()

    expect(galleryApi.createQuickSearch).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'My preset', keyword: '', mode: 0 }),
    )
    // 成功后对话框关闭。
    expect(document.querySelector('.dialog-scrim')).toBeNull()
  })

  it('resets loadingMore when an in-flight append is superseded by a new search (F5)', async () => {
    // Page 0 resolves; the page-1 append hangs until the test releases it.
    let releaseAppend: (value: GalleryListResponse) => void = () => {}
    vi.mocked(galleryApi.search).mockImplementation(
      (_kw?: string, _category?: number, page?: number): Promise<GalleryListResponse> => {
        if ((page ?? 0) === 0) {
          return Promise.resolve({ success: true, data: [gallery()], total: 50 })
        }
        return new Promise<GalleryListResponse>((resolve) => {
          releaseAppend = resolve
        })
      },
    )
    wrapper = mount(HomeView)
    await flushPromises()
    expect(wrapper.find('[data-testid="content-state-content"]').exists()).toBe(true)

    // 滚动近底 → 触发 append（第 2 页，挂起中）。
    const scroller = wrapper.find('.fast-scroller__container').element as HTMLElement
    scroller.dispatchEvent(new Event('scroll'))
    await flushPromises()
    expect(wrapper.find('[data-testid="content-loading-more"]').exists()).toBe(true)

    // 飞行中的 append 被新的搜索（replace，seq 作废旧请求）取代。
    wrapper.findComponent(SearchBar).vm.$emit('search', 'new-keyword')
    await flushPromises()

    // 释放被作废的 append——loadingMore 必须复位（页脚 spinner 消失）。
    releaseAppend({ success: true, data: [], total: 0 })
    await flushPromises()
    expect(wrapper.find('[data-testid="content-loading-more"]').exists()).toBe(false)
  })
})

describe('HomeView (B-1 localStorage → preferences listMode migration)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    pushMock.mockClear()
    routeMock.query = {}
    vi.mocked(galleryApi.getQuickSearches).mockResolvedValue({ success: true, data: [] })
    vi.mocked(galleryApi.search).mockResolvedValue({ success: true, data: [gallery()], total: 1 })
    vi.mocked(preferencesApi.update).mockResolvedValue(makePrefs({}))
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  async function mountHomeWithPrefs(general: Record<string, unknown>) {
    vi.mocked(preferencesApi.get).mockResolvedValue(makePrefs(general))
    wrapper = mount(HomeView)
    await flushPromises()
    return wrapper
  }

  it('writes the legacy grid value into preferences and clears localStorage', async () => {
    vi.useFakeTimers()
    localStorage.setItem(LIST_MODE_KEY, 'grid')
    await mountHomeWithPrefs({ listMode: 'list' }) // server value differs

    // Migration applied the legacy value to the store…
    expect(usePreferencesStore().prefs?.general.listMode).toBe('grid')
    // …cleared the legacy key…
    expect(localStorage.getItem(LIST_MODE_KEY)).toBeNull()
    // …and persists it through the preferences API (debounced PUT).
    await vi.advanceTimersByTimeAsync(600)
    expect(preferencesApi.update).toHaveBeenCalledTimes(1)
    expect(preferencesApi.update).toHaveBeenCalledWith({
      general: expect.objectContaining({ listMode: 'grid' }),
    })
  })

  it('migrates the legacy list value too', async () => {
    vi.useFakeTimers()
    localStorage.setItem(LIST_MODE_KEY, 'list')
    await mountHomeWithPrefs({ listMode: 'grid' })

    expect(usePreferencesStore().prefs?.general.listMode).toBe('list')
    expect(localStorage.getItem(LIST_MODE_KEY)).toBeNull()
  })

  it('normalizes an unrecognized legacy value to list', async () => {
    vi.useFakeTimers()
    localStorage.setItem(LIST_MODE_KEY, 'table')
    await mountHomeWithPrefs({ listMode: 'grid' })

    // Legacy semantics: anything that is not exactly "grid" was list mode.
    expect(usePreferencesStore().prefs?.general.listMode).toBe('list')
    expect(localStorage.getItem(LIST_MODE_KEY)).toBeNull()
  })

  it('leaves preferences untouched when no legacy value is stored', async () => {
    vi.useFakeTimers()
    await mountHomeWithPrefs({ listMode: 'list' })

    expect(usePreferencesStore().prefs?.general.listMode).toBe('list')
    await vi.advanceTimersByTimeAsync(600)
    expect(preferencesApi.update).not.toHaveBeenCalled()
  })

  it('migrates exactly once', async () => {
    vi.useFakeTimers()
    localStorage.setItem(LIST_MODE_KEY, 'grid')
    await mountHomeWithPrefs({ listMode: 'list' })
    await vi.advanceTimersByTimeAsync(600)

    // The key is gone; a later preferences change is not re-migrated.
    const store = usePreferencesStore()
    store.updateGeneral({ listMode: 'list' })
    await vi.advanceTimersByTimeAsync(600)
    expect(store.prefs?.general.listMode).toBe('list')
    expect(localStorage.getItem(LIST_MODE_KEY)).toBeNull()
  })
})

describe('HomeView — EH 熔断（plan-2026-08-30 §0）', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    // 迁移 describe 共用同一外层 beforeEach？不——此处建立独立前置。
    setActivePinia(createPinia())
    localStorage.clear()
    pushMock.mockClear()
    routeMock.query = {}
    availability.state = null
    availability.downAt = null
    availability.lastReason = null
    availability.lastLoadedAt = null
    markUnknown()
    vi.mocked(galleryApi.getQuickSearches).mockResolvedValue({ success: true, data: [] })
    vi.mocked(preferencesApi.get).mockResolvedValue(makePrefs({ listMode: 'grid' }))
    vi.mocked(preferencesApi.update).mockResolvedValue(makePrefs({}))
    vi.mocked(siteApi.getAvailability).mockResolvedValue({ state: 'UNKNOWN' })
    vi.mocked(siteApi.probeAvailability).mockResolvedValue({ state: 'UP' })
  })

  afterEach(() => {
    wrapper?.unmount()
    availability.state = null
    availability.downAt = null
    availability.lastReason = null
    availability.lastLoadedAt = null
    vi.clearAllMocks()
  })

  /** 当 EH 不可达时搜索以 404 信封形式快速失败（服务端已短路）。 */
  function ehError() {
    return {
      response: {
        status: 404,
        data: {
          error: {
            code: 'EH_UNAVAILABLE',
            message: 'EH 平台当前不可达，仅显示本地内容',
          },
        },
      },
    }
  }

  it('defers to the local-only tip when the feed fails with EH_UNAVAILABLE', async () => {
    vi.mocked(galleryApi.search).mockRejectedValue(ehError())
    wrapper = mount(HomeView)
    await flushPromises()

    expect(wrapper.find('[data-testid="content-state-error"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('EH 平台当前不可达，仅显示本地内容')
    expect(wrapper.text()).not.toContain('加载失败，请稍后重试')
  })

  it('shows the banner at the top when the availability state is down', async () => {
    vi.mocked(galleryApi.search).mockResolvedValue({ success: true, data: [gallery()], total: 1 })
    // 服务器自身判定 DOWN。
    vi.mocked(siteApi.getAvailability).mockResolvedValue({
      state: 'DOWN',
      downAt: 9,
      lastReason: 'probe failed',
    })
    wrapper = mount(HomeView)
    await flushPromises()
    await flushPromises()

    const banner = wrapper.find('[data-testid="availability-banner"]')
    expect(banner.exists()).toBe(true)
    expect(banner.text()).toContain('重新连接')
    // 内容区不受影响（本地内容照常）。
    expect(wrapper.find('[data-testid="content-state-content"]').exists()).toBe(true)
  })

  it('hides the banner and refreshes the list after a successful reconnect', async () => {
    vi.mocked(galleryApi.search).mockResolvedValue({ success: true, data: [gallery()], total: 1 })
    vi.mocked(siteApi.getAvailability).mockResolvedValue({ state: 'DOWN' })
    wrapper = mount(HomeView)
    await flushPromises()
    await flushPromises()
    expect(wrapper.find('[data-testid="availability-banner"]').exists()).toBe(true)

    await wrapper.find('[data-testid="availability-banner"] button').trigger('click')
    await flushPromises()
    await flushPromises()

    // 探测成功 → 状态 UP → 横幅消失 + 父视图刷新（再次加载第 0 页）。
    expect(wrapper.find('[data-testid="availability-banner"]').exists()).toBe(false)
    expect(galleryApi.search).toHaveBeenCalledTimes(2)
  })
})
