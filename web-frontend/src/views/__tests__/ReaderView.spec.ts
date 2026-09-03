import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ReaderView from '../ReaderView.vue'
import { galleryApi } from '@/api/gallery'
import { usePreferencesStore } from '@/stores/preferences'
import { DEFAULT_PREFERENCES, DEFAULT_READER_PREFERENCES } from '@/api/preferences'
import { spreadIndexOf, firstPageOfSpread } from '@/components/reader/PageMode.vue'

const { pushMock, replaceMock, routeParams, routeQuery } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  replaceMock: vi.fn(),
  routeParams: { gid: '123456', page: undefined as string | undefined },
  routeQuery: { token: undefined as string | undefined },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams, query: routeQuery }),
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
}))

vi.mock('@/api/gallery', () => ({
  galleryApi: { getDetail: vi.fn(), addHistory: vi.fn() },
}))

// NOTE (T-F2): useKeyboardNav is intentionally NOT mocked here — the keyboard
// suites below dispatch real `keydown` events against the composable's
// window listener. The WebSocket-backed useEnhancedImage stays mocked.

vi.mock('@/composables/useEnhancedImage', () => ({
  useEnhancedImage: () => ({ enhancedUrls: {}, connect: vi.fn() }),
}))

/**
 * ImageReader stub — F1's spec is about the writeback wiring, not the reader
 * chrome. Page flips are driven by emitting `update:current-page`, the same
 * contract the real chrome uses. T-F2 adds pass-through rendering of the
 * auto-play state as data-attributes (text content unchanged) so the autoplay
 * suite can observe start/stop without a DOM fork.
 */
vi.mock('@/components/reader/ImageReader.vue', () => ({
  default: {
    name: 'ImageReader',
    props: [
      'gid',
      'title',
      'totalPages',
      'currentPage',
      'autoPlay',
      'autoPlayProgress',
      'pageMode',
      'direction',
      'brightness',
    ],
    emits: ['update:current-page'],
    methods: {
      // ReaderView calls readerRef.toggleChrome() on Space/Esc.
      toggleChrome() {},
    },
    template:
      '<div class="image-reader-stub" :data-enabled="autoPlay && autoPlay.enabled ? \'on\' : \'off\'" :data-progress="String(autoPlayProgress ?? 0)" :data-total="String(totalPages ?? 0)" :data-page-mode="String(pageMode ?? \'\')" :data-direction="String(direction ?? \'\')" :data-brightness="String(brightness ?? 0)">{{ currentPage }}</div>',
  },
}))

function detailFixture(overrides: Record<string, unknown> = {}) {
  return {
    gid: 123456,
    token: 'a1b2c3d4e5',
    title: 'Sample Gallery',
    titleJpn: '',
    thumb: '',
    category: 2,
    posted: '0',
    uploader: '',
    rating: 4.5,
    rated: false,
    simpleLanguage: '',
    simpleTags: [],
    thumbWidth: 100,
    thumbHeight: 140,
    pages: 50,
    favoriteSlot: -1,
    favoriteName: '',
    tags: [],
    imageUrl: '',
    ...overrides,
  }
}

/** Emit a page change from the reader chrome (clamped by the view). */
function flipTo(wrapper: VueWrapper, page: number): void {
  const reader = wrapper.findComponent({ name: 'ImageReader' })
  ;(reader.vm as unknown as { $emit: (event: string, ...args: unknown[]) => void }).$emit(
    'update:current-page',
    page,
  )
}

/**
 * W4 (plan-2026-09-02): the reader now restores the initial page from the
 * persisted `readProgress:{gid}` localStorage key, so every test must start
 * from a clean storage — otherwise a prior test's flush (e.g. the
 * unknown-page-counts test flipping to page 1 before unmount) leaks into the
 * next mount's restore.
 */
beforeEach(() => {
  localStorage.clear()
})

describe('ReaderView F1 — history writeback (POST /gallery/history/{gid})', () => {
  let wrapper: VueWrapper | undefined
  let warnSpy: ReturnType<typeof vi.spyOn>

  async function mountReader(): Promise<VueWrapper> {
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    const mounted = mount(ReaderView)
    await flushPromises()
    return mounted
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    routeParams.gid = '123456'
    delete routeParams.page
    // afterEach 的 restoreAllMocks 会清掉 hoisted mock 的实现，逐例重置。
    replaceMock.mockReset()
    replaceMock.mockResolvedValue(undefined)
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(async () => {
    wrapper?.unmount()
    wrapper = undefined
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('records the visit exactly once with token+title+page when entering the reader', async () => {
    wrapper = await mountReader()
    await flushPromises()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1)
    expect(galleryApi.addHistory).toHaveBeenCalledWith(123456, {
      token: 'a1b2c3d4e5',
      title: 'Sample Gallery',
      page: 0,
    })
  })

  it('does not double-record on entry when deep-linked mid-gallery (start page ≥ stride)', async () => {
    routeParams.page = '30'
    wrapper = await mountReader()
    await flushPromises()
    await flushPromises()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.image-reader-stub').text()).toBe('30')
  })

  it('stays silent within the throttle window (flips <10 pages and <30s)', async () => {
    vi.useFakeTimers()
    wrapper = await mountReader()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1)

    for (const page of [1, 4, 9]) {
      flipTo(wrapper, page)
      await flushPromises()
    }
    vi.advanceTimersByTime(29_999)
    await flushPromises()

    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1)
  })

  it('writes back once ≥10 pages flipped since the last write (stride path)', async () => {
    vi.useFakeTimers()
    wrapper = await mountReader()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1)

    flipTo(wrapper, 9)
    await flushPromises()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1) // 9 < 10 → throttled

    flipTo(wrapper, 10)
    await flushPromises()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(2) // stride reached
    expect(galleryApi.addHistory).toHaveBeenLastCalledWith(123456, {
      token: 'a1b2c3d4e5',
      title: 'Sample Gallery',
      page: 10,
    })
  })

  it('writes back a slow reader once 30s elapsed since the last write (interval path)', async () => {
    vi.useFakeTimers()
    wrapper = await mountReader()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(30_000)
    flipTo(wrapper, 1)
    await flushPromises()

    expect(galleryApi.addHistory).toHaveBeenCalledTimes(2)
  })

  it('flushes the tail position once on unmount beyond the throttle window', async () => {
    vi.useFakeTimers()
    wrapper = await mountReader()
    flipTo(wrapper, 2)
    await flushPromises()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1) // dirty but throttled

    wrapper.unmount()
    wrapper = undefined

    expect(galleryApi.addHistory).toHaveBeenCalledTimes(2)
  })

  it('flushes via pagehide when the tab closes (best-effort final write)', async () => {
    wrapper = await mountReader()
    flipTo(wrapper, 2)
    await flushPromises()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1)

    window.dispatchEvent(new Event('pagehide'))
    await flushPromises()

    expect(galleryApi.addHistory).toHaveBeenCalledTimes(2)
  })

  it('degrades silently to console.warn when the writeback fails (reading unaffected)', async () => {
    vi.mocked(galleryApi.addHistory).mockRejectedValue(new Error('network down'))
    wrapper = await mountReader()
    await flushPromises()

    expect(warnSpy).toHaveBeenCalledWith(
      '[reader] history writeback failed (gid=123456)',
      expect.any(Error),
    )
    // The reader stays up — no error state swapped in for a history outage.
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('.image-reader-stub').exists()).toBe(true)
  })
})

/* ═══════════════════════════════════════════════════════════════════════
 * T-F2 additions — keyboard navigation / dual-page spread / auto-play.
 * The real useKeyboardNav composable is active (not mocked): key events are
 * dispatched against window and observed through the ImageReader stub.
 * ═══════════════════════════════════════════════════════════════════════ */

const READER_SETTINGS_KEY = 'anotherviewer-web.reader-settings'

/** Emit an arbitrary event from the reader chrome stub. */
function emitReader(w: VueWrapper, event: string, ...args: unknown[]): void {
  const reader = w.findComponent({ name: 'ImageReader' })
  ;(reader.vm as unknown as { $emit: (e: string, ...a: unknown[]) => void }).$emit(
    event,
    ...args,
  )
}

function pressKey(key: string): void {
  window.dispatchEvent(new KeyboardEvent('keydown', { key }))
}

function setReaderSettings(settings: Record<string, unknown>): void {
  // 当前载荷版本（v2）——与 ReaderView.SETTINGS_VERSION 对齐；缺 v 的
  // 旧载荷会被迁移逻辑丢弃 pageMode。
  localStorage.setItem(READER_SETTINGS_KEY, JSON.stringify({ v: 2, ...settings }))
}

function prefsWithReader(patch: Partial<typeof DEFAULT_READER_PREFERENCES>): void {
  const store = usePreferencesStore()
  store.prefs = {
    general: { ...DEFAULT_PREFERENCES.general },
    reader: { ...DEFAULT_READER_PREFERENCES, ...patch },
    privacy: { ...DEFAULT_PREFERENCES.privacy },
  }
}

describe('ReaderView 统一阅读器 — detail 失败仍打开（degraded open）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeParams.gid = '123456'
    delete routeParams.page
    replaceMock.mockReset()
    replaceMock.mockResolvedValue(undefined)
    pushMock.mockReset()
    vi.mocked(galleryApi.getDetail).mockReset()
    vi.mocked(galleryApi.addHistory).mockReset()
    vi.mocked(galleryApi.addHistory).mockResolvedValue(undefined as never)
  })

  it('opens the reader shell with a degraded banner when detail fetch fails', async () => {
    vi.mocked(galleryApi.getDetail).mockRejectedValue(new Error('site unreachable'))
    const wrapper = mount(ReaderView)
    await flushPromises()

    // 阅读器壳已挂载（ImageReader stub 渲染），而非全屏错误
    expect(wrapper.find('.image-reader-stub').exists()).toBe(true)
    expect(wrapper.find('[data-testid="reader-degraded-banner"]').exists()).toBe(true)
    expect(wrapper.find('.image-reader-stub').attributes('data-total')).toBe('1')
    // 无 token 不做历史回写
    expect(vi.mocked(galleryApi.addHistory)).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('enters unknown-page-counts mode (no degraded banner) when pages=0 with a gallery row', async () => {
    // 导入 .db：detail 返回 pages=0（行存在但 total 缺失、token 失效）——不再是
    // 「detail 拉不到」的降级（total=1 卡死），进入未知页数模式：
    // 阅读器壳打开、无降级横幅、totalPages=0（PageMode 逐页驱动）、翻页可前进。
    vi.mocked(galleryApi.getDetail).mockResolvedValue(
      detailFixture({ pages: 0, title: 'Imported Gallery' }),
    )
    const wrapper = mount(ReaderView)
    await flushPromises()

    const stub = wrapper.find('.image-reader-stub')
    expect(stub.exists()).toBe(true)
    expect(stub.attributes('data-total')).toBe('0')
    expect(wrapper.find('[data-testid="reader-degraded-banner"]').exists()).toBe(false)
    expect(stub.text()).toContain('0')
    // 翻页不被 totalPages=1 卡死：ArrowRight → nextPage() 到第 1 页。
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }))
    await flushPromises()
    expect(stub.text()).toContain('1')
    wrapper.unmount()
  })

    it('does not show the degraded banner when detail succeeds', async () => {
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    const wrapper = mount(ReaderView)
    await flushPromises()

    expect(wrapper.find('[data-testid="reader-degraded-banner"]').exists()).toBe(false)

    wrapper.unmount()
  })
})

describe('ReaderView (T-F2) — keyboard navigation', () => {
  let wrapper: VueWrapper | undefined

  async function mountReady(): Promise<VueWrapper> {
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    const mounted = mount(ReaderView)
    await flushPromises()
    return mounted
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    routeParams.gid = '123456'
    delete routeParams.page
    replaceMock.mockReset().mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.removeItem(READER_SETTINGS_KEY)
    vi.restoreAllMocks()
  })

  it('turns pages with ←/→ and clamps at both ends', async () => {
    // Pin single mode so every step is exactly ±1 page.
    setReaderSettings({ direction: 'ltr', pageMode: 'single', brightness: 0 })
    wrapper = await mountReady()
    const stub = () => wrapper!.find('.image-reader-stub')
    expect(stub().text()).toBe('0')

    pressKey('ArrowRight')
    await flushPromises()
    expect(stub().text()).toBe('1')

    pressKey('ArrowLeft')
    await flushPromises()
    expect(stub().text()).toBe('0')

    // Already on the first page — no wrap-around.
    pressKey('ArrowLeft')
    await flushPromises()
    expect(stub().text()).toBe('0')

    pressKey('End')
    await flushPromises()
    expect(stub().text()).toBe('49')

    // Last page: → and End are no-ops, ← still steps back.
    pressKey('ArrowRight')
    pressKey('End')
    await flushPromises()
    expect(stub().text()).toBe('49')
    pressKey('ArrowLeft')
    await flushPromises()
    expect(stub().text()).toBe('48')

    pressKey('Home')
    await flushPromises()
    expect(stub().text()).toBe('0')
  })

  it("accepts vim-style a/d aliases for ←/→", async () => {
    wrapper = await mountReady()
    pressKey('d')
    await flushPromises()
    expect(wrapper.find('.image-reader-stub').text()).toBe('1')

    pressKey('a')
    await flushPromises()
    expect(wrapper.find('.image-reader-stub').text()).toBe('0')
  })

  it('mirrors ←/→ under RTL direction', async () => {
    setReaderSettings({ direction: 'rtl', pageMode: 'single', brightness: 0 })
    wrapper = await mountReady()

    emitReader(wrapper, 'update:current-page', 5)
    await flushPromises()

    pressKey('ArrowRight') // RTL: right arrow goes back
    await flushPromises()
    expect(wrapper.find('.image-reader-stub').text()).toBe('4')

    pressKey('ArrowLeft') // RTL: left arrow advances
    await flushPromises()
    expect(wrapper.find('.image-reader-stub').text()).toBe('5')
  })

  it('disables arrow/space paging when keyboardPaging preference is off', async () => {
    prefsWithReader({ keyboardPaging: false })
    wrapper = await mountReady()

    pressKey('ArrowRight')
    pressKey('d')
    pressKey(' ')
    await flushPromises()
    expect(wrapper.find('.image-reader-stub').text()).toBe('0')

    // Non-paging keys stay live regardless of the preference.
    pressKey('End')
    await flushPromises()
    expect(wrapper.find('.image-reader-stub').text()).toBe('49')
  })

  it('pages with Space while keyboardPaging is on', async () => {
    wrapper = await mountReady()

    pressKey(' ')
    await flushPromises()
    expect(wrapper.find('.image-reader-stub').text()).toBe('1')
  })
})

describe('ReaderView (T-F2) — dual-page spread index calculation', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.removeItem(READER_SETTINGS_KEY)
  })

  it('pairs pages cover-alone then (2,3), (4,5)… in spread indices', () => {
    // Page 0 (cover) alone; 1-based pairs (2,3) → spreads of two.
    expect(spreadIndexOf(0)).toBe(0)
    expect(spreadIndexOf(1)).toBe(1)
    expect(spreadIndexOf(2)).toBe(1)
    expect(spreadIndexOf(3)).toBe(2)
    expect(spreadIndexOf(4)).toBe(2)
    expect(spreadIndexOf(49)).toBe(25)

    expect(firstPageOfSpread(0)).toBe(0)
    expect(firstPageOfSpread(1)).toBe(1)
    expect(firstPageOfSpread(2)).toBe(3)
    expect(firstPageOfSpread(25)).toBe(49)
  })

  it('steps whole spreads in dual mode: cover alone, then paired jumps', async () => {
    setActivePinia(createPinia())
    routeParams.gid = '123456'
    delete routeParams.page
    replaceMock.mockReset().mockResolvedValue(undefined)
    setReaderSettings({ direction: 'ltr', pageMode: 'dual', brightness: 0 })

    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    const mounted = mount(ReaderView)
    await flushPromises()
    wrapper = mounted

    const page = () => wrapper!.find('.image-reader-stub').text()
    expect(page()).toBe('0')

    emitReader(wrapper, 'next')
    await flushPromises()
    expect(page()).toBe('1') // cover → start of spread 1

    emitReader(wrapper, 'next')
    await flushPromises()
    expect(page()).toBe('3') // spread 1 → spread 2

    emitReader(wrapper, 'prev')
    await flushPromises()
    expect(page()).toBe('1')

    emitReader(wrapper, 'prev')
    await flushPromises()
    expect(page()).toBe('0') // back to the lone cover

    emitReader(wrapper, 'prev')
    await flushPromises()
    expect(page()).toBe('0') // clamped before the cover

    // From mid-spread the previous step lands on the START of the prior spread.
    emitReader(wrapper, 'update:current-page', 4)
    await flushPromises()
    emitReader(wrapper, 'prev')
    await flushPromises()
    expect(page()).toBe('1')

    wrapper.unmount()
    wrapper = undefined
  })

  it('refuses to step past the final partial spread in dual mode', async () => {
    setActivePinia(createPinia())
    routeParams.gid = '123456'
    delete routeParams.page
    replaceMock.mockReset().mockResolvedValue(undefined)
    setReaderSettings({ direction: 'ltr', pageMode: 'dual', brightness: 0 })

    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    const mounted = mount(ReaderView)
    await flushPromises()
    wrapper = mounted

    emitReader(wrapper, 'update:current-page', 49) // last page = lone tail spread
    await flushPromises()

    emitReader(wrapper, 'next')
    await flushPromises()
    expect(wrapper.find('.image-reader-stub').text()).toBe('49')

    wrapper.unmount()
    wrapper = undefined
  })
})

describe('ReaderView (T-F2) — auto-play timer', () => {
  let wrapper: VueWrapper | undefined

  async function mountReady(): Promise<VueWrapper> {
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    const mounted = mount(ReaderView)
    await flushPromises()
    return mounted
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    routeParams.gid = '123456'
    delete routeParams.page
    replaceMock.mockReset().mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    delete (document as unknown as Record<string, unknown>).hidden
    localStorage.removeItem(READER_SETTINGS_KEY)
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('advances one page per interval and drives the progress bar', async () => {
    vi.useFakeTimers()
    wrapper = await mountReady()
    const stub = () => wrapper!.find('.image-reader-stub')

    emitReader(wrapper, 'update:auto-play', { enabled: true, intervalMs: 2000 })
    await flushPromises()
    expect(stub().attributes('data-enabled')).toBe('on')

    vi.advanceTimersByTime(1000)
    await flushPromises()
    expect(stub().text()).toBe('0')
    expect(stub().attributes('data-progress')).toBe('0.5')

    // Cycle completes at t=2000 (progress peaks at 1), the next tick wraps it.
    vi.advanceTimersByTime(1100)
    await flushPromises()
    expect(stub().text()).toBe('1')
    expect(stub().attributes('data-progress')).toBe('0.05')
  })

  it('pauses ticking while the tab is hidden', async () => {
    vi.useFakeTimers()
    Object.defineProperty(document, 'hidden', { configurable: true, value: true })
    wrapper = await mountReady()
    const stub = () => wrapper!.find('.image-reader-stub')

    emitReader(wrapper, 'update:auto-play', { enabled: true, intervalMs: 2000 })
    await flushPromises()

    vi.advanceTimersByTime(10_000)
    await flushPromises()
    expect(stub().text()).toBe('0')
    expect(stub().attributes('data-progress')).toBe('0')
  })

  it('stops advancing after being disabled (timer torn down)', async () => {
    vi.useFakeTimers()
    wrapper = await mountReady()
    const stub = () => wrapper!.find('.image-reader-stub')

    emitReader(wrapper, 'update:auto-play', { enabled: true, intervalMs: 2000 })
    await flushPromises()
    vi.advanceTimersByTime(1000)
    await flushPromises()

    emitReader(wrapper, 'update:auto-play', { enabled: false, intervalMs: 2000 })
    await flushPromises()
    expect(stub().attributes('data-enabled')).toBe('off')
    // Disabling resets the progress bar immediately.
    expect(stub().attributes('data-progress')).toBe('0')

    vi.advanceTimersByTime(10_000)
    await flushPromises()
    expect(stub().text()).toBe('0')
  })

  it('restarts cleanly when the interval changes mid-run', async () => {
    vi.useFakeTimers()
    wrapper = await mountReady()
    const stub = () => wrapper!.find('.image-reader-stub')

    emitReader(wrapper, 'update:auto-play', { enabled: true, intervalMs: 2000 })
    await flushPromises()
    vi.advanceTimersByTime(1500)
    await flushPromises()

    emitReader(wrapper, 'update:auto-play', { enabled: true, intervalMs: 8000 })
    await flushPromises()
    // Elapsed time discarded on restart — old cycle never fires.
    expect(stub().attributes('data-progress')).toBe('0')

    vi.advanceTimersByTime(7900)
    await flushPromises()
    expect(stub().text()).toBe('0')
    expect(stub().attributes('data-progress')).toBe('0.9875')

    vi.advanceTimersByTime(200)
    await flushPromises()
    expect(stub().text()).toBe('1')
  })
  it('switches itself off at the end of the gallery', async () => {
    vi.useFakeTimers()
    wrapper = await mountReady()
    const stub = () => wrapper!.find('.image-reader-stub')

    emitReader(wrapper, 'update:current-page', 49)
    await flushPromises()
    emitReader(wrapper, 'update:auto-play', { enabled: true, intervalMs: 2000 })
    await flushPromises()

    vi.advanceTimersByTime(2100)
    await flushPromises()

    // nextPage() returned false → enabled flipped off, page unchanged.
    expect(stub().text()).toBe('49')
    expect(stub().attributes('data-enabled')).toBe('off')
    expect(stub().attributes('data-progress')).toBe('0')
  })
})

describe('ReaderView — P-B 入口 token 透传（plan-2026-08-30 §3.4.0）', () => {
  let wrapper: VueWrapper | undefined

  async function mountReader(): Promise<VueWrapper> {
    const mounted = mount(ReaderView)
    await flushPromises()
    return mounted
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    routeParams.gid = '123456'
    delete routeParams.page
    routeQuery.token = undefined
    replaceMock.mockReset().mockResolvedValue(undefined)
    vi.mocked(galleryApi.getDetail).mockReset()
    vi.mocked(galleryApi.addHistory).mockReset()
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    routeQuery.token = undefined
    vi.restoreAllMocks()
  })

  it('passes the route token into getDetail', async () => {
    routeQuery.token = 'tokFromList'
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    wrapper = await mountReader()
    expect(galleryApi.getDetail).toHaveBeenCalledWith(123456, 'tokFromList')
  })

  it('calls getDetail without a token when the query carries none', async () => {
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    wrapper = await mountReader()
    expect(galleryApi.getDetail).toHaveBeenCalledWith(123456, undefined)
  })

  it('re-derives the token from the query on each load (token 只从 query 一次取)', async () => {
    // 每次 load 独立读取 route.query.token——resetReaderState 不携带 token 状态，
    // 新加载以查询参数为准（route.params.page 的 watch 亦从不改动它）。
    routeQuery.token = 'tokA'
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    wrapper = await mountReader()
    expect(galleryApi.getDetail).toHaveBeenLastCalledWith(123456, 'tokA')
    wrapper.unmount()
    wrapper = undefined

    routeQuery.token = 'tokB'
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    wrapper = await mountReader()
    expect(galleryApi.getDetail).toHaveBeenLastCalledWith(123456, 'tokB')
  })
})

describe('ReaderView — 阅读设置统一（服务器基底 + 本地覆盖 + v1 迁移）', () => {
  let wrapper: VueWrapper | undefined

  function stub(): VueWrapper {
    return wrapper!.findComponent({ name: 'ImageReader' })
  }

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.removeItem(READER_SETTINGS_KEY)
    vi.restoreAllMocks()
  })

  it('v1 本地载荷的 pageMode 被迁移丢弃，回落服务器偏好 auto', async () => {
    // 旧手机上遗留的 v1 载荷固化了过期默认 dual。
    localStorage.setItem(
      READER_SETTINGS_KEY,
      JSON.stringify({ direction: 'ltr', pageMode: 'dual', brightness: 0 }),
    )
    prefsWithReader({ pageMode: 'auto' })
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    wrapper = mount(ReaderView)
    await flushPromises()

    expect(stub().attributes('data-page-mode')).toBe('auto')
    // 迁移后立即以当前生效值重写为 v2。
    const stored = JSON.parse(localStorage.getItem(READER_SETTINGS_KEY)!)
    expect(stored.v).toBe(2)
    expect(stored.pageMode).toBe('auto')
  })

  it('无本地载荷时以服务器偏好为基底', async () => {
    prefsWithReader({ pageMode: 'single', readingDirection: 'rtl' })
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    wrapper = mount(ReaderView)
    await flushPromises()

    expect(stub().attributes('data-page-mode')).toBe('single')
    expect(stub().attributes('data-direction')).toBe('rtl')
  })

  it('v2 本地覆盖优先于服务器偏好', async () => {
    setReaderSettings({ direction: 'ltr', pageMode: 'dual', brightness: 30 })
    prefsWithReader({ pageMode: 'auto', brightness: 0 })
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    wrapper = mount(ReaderView)
    await flushPromises()

    expect(stub().attributes('data-page-mode')).toBe('dual')
    expect(stub().attributes('data-brightness')).toBe('30')
  })

  it('阅读器内改动回写服务器偏好状态', async () => {
    prefsWithReader({ pageMode: 'auto' })
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture())
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    wrapper = mount(ReaderView)
    await flushPromises()

    const reader = wrapper.findComponent({ name: 'ImageReader' })
    ;(reader.vm as unknown as { $emit: (e: string, ...a: unknown[]) => void }).$emit(
      'update:page-mode',
      'single',
    )
    await flushPromises()

    const store = usePreferencesStore()
    expect(store.prefs?.reader.pageMode).toBe('single')
    const stored = JSON.parse(localStorage.getItem(READER_SETTINGS_KEY)!)
    expect(stored.pageMode).toBe('single')
  })
})

/* ═══════════════════════════════════════════════════════════════════════
 * W4 additions (plan-2026-09-02 §5.3 W4 / §8 前端) — 阅读进度恢复与回写：
 * 初始页优先级（深链 > detail.readProgress > 0）、未知页数不钳上界、
 * 回写 payload 携带 page、降级态 localStorage 读写。
 * ═══════════════════════════════════════════════════════════════════════ */

describe('ReaderView W4 — 阅读进度恢复（初始页优先级）', () => {
  let wrapper: VueWrapper | undefined

  async function mountReader(overrides: Record<string, unknown> = {}): Promise<VueWrapper> {
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture(overrides))
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    const mounted = mount(ReaderView)
    await flushPromises()
    return mounted
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    routeParams.gid = '123456'
    delete routeParams.page
    replaceMock.mockReset().mockResolvedValue(undefined)
    vi.mocked(galleryApi.getDetail).mockReset()
    vi.mocked(galleryApi.addHistory).mockReset()
    localStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('restores the initial page from detail.readProgress when no deep link', async () => {
    wrapper = await mountReader({ readProgress: 12 })
    expect(wrapper.find('.image-reader-stub').text()).toBe('12')
  })

  it('starts at 0 when readProgress is absent (legacy-server tolerance)', async () => {
    wrapper = await mountReader()
    expect(wrapper.find('.image-reader-stub').text()).toBe('0')
  })

  it('treats an explicit readProgress 0 as "start from the beginning"', async () => {
    wrapper = await mountReader({ readProgress: 0 })
    expect(wrapper.find('.image-reader-stub').text()).toBe('0')
  })

  it('gives the deep-link route param priority over readProgress', async () => {
    routeParams.page = '30'
    wrapper = await mountReader({ readProgress: 12 })
    expect(wrapper.find('.image-reader-stub').text()).toBe('30')
  })

  it('clamps a restored readProgress to the last known page', async () => {
    wrapper = await mountReader({ readProgress: 100 })
    expect(wrapper.find('.image-reader-stub').text()).toBe('49')
  })

  it('restores unknown-page-count progress without an upper clamp (§10.7)', async () => {
    // 导入 .db（pages=0）→ unknownPageCounts 模式：readProgress=7 恢复到第 7
    // 页——若误用 Math.max(0, totalPages-1) 钳上界，进度会被压平成 0。
    wrapper = await mountReader({ pages: 0, readProgress: 7, title: 'Imported Gallery' })
    const stub = wrapper.find('.image-reader-stub')
    expect(stub.attributes('data-total')).toBe('0')
    expect(stub.text()).toBe('7')
  })

  it('backs unknown-page-count restore with localStorage when the server has none', async () => {
    localStorage.setItem('readProgress:123456', '9')
    wrapper = await mountReader({ pages: 0, title: 'Imported Gallery' })
    expect(wrapper.find('.image-reader-stub').text()).toBe('9')
  })

  it('prefers server progress (incl. explicit 0) over stale localStorage for token galleries', async () => {
    localStorage.setItem('readProgress:123456', '9')
    wrapper = await mountReader({ readProgress: 0 })
    // D2: detail.readProgress 是唯一权威来源——显式 REST 写 0 的重读语义
    // 不被本机陈旧进度覆盖。
    expect(wrapper.find('.image-reader-stub').text()).toBe('0')
  })

  it('prefers server progress over localStorage when both exist', async () => {
    localStorage.setItem('readProgress:123456', '3')
    wrapper = await mountReader({ readProgress: 20 })
    expect(wrapper.find('.image-reader-stub').text()).toBe('20')
  })
})

describe('ReaderView W4 — 回写 payload 携带 page（防抖策略不变）', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    setActivePinia(createPinia())
    routeParams.gid = '123456'
    delete routeParams.page
    replaceMock.mockReset().mockResolvedValue(undefined)
    vi.mocked(galleryApi.addHistory).mockReset()
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    localStorage.clear()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.clear()
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  async function mountReader(overrides: Record<string, unknown> = {}): Promise<VueWrapper> {
    vi.mocked(galleryApi.getDetail).mockResolvedValue(detailFixture(overrides))
    const mounted = mount(ReaderView)
    await flushPromises()
    return mounted
  }

  it('includes page in the entry writeback payload', async () => {
    wrapper = await mountReader({ readProgress: 4 })
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1)
    expect(galleryApi.addHistory).toHaveBeenCalledWith(123456, {
      token: 'a1b2c3d4e5',
      title: 'Sample Gallery',
      page: 4,
    })
    // 回写走 REST——不写本机 localStorage。
    expect(localStorage.getItem('readProgress:123456')).toBeNull()
  })

  it('carries the flipped page in throttled mid-reading writebacks', async () => {
    vi.useFakeTimers()
    wrapper = await mountReader({ readProgress: 4 })
    flipTo(wrapper, 14) // 14-4 = 10 ≥ stride
    await flushPromises()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(2)
    expect(galleryApi.addHistory).toHaveBeenLastCalledWith(123456, {
      token: 'a1b2c3d4e5',
      title: 'Sample Gallery',
      page: 14,
    })
  })
})

describe('ReaderView W4 — 降级态 localStorage 读写（readProgress:{gid}）', () => {
  const PROGRESS_KEY = 'readProgress:123456'
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    setActivePinia(createPinia())
    routeParams.gid = '123456'
    delete routeParams.page
    replaceMock.mockReset().mockResolvedValue(undefined)
    vi.mocked(galleryApi.getDetail).mockReset()
    vi.mocked(galleryApi.addHistory).mockReset()
    vi.mocked(galleryApi.addHistory).mockResolvedValue({ success: true })
    localStorage.clear()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.clear()
    vi.restoreAllMocks()
  })

  async function mountDegraded(): Promise<VueWrapper> {
    vi.mocked(galleryApi.getDetail).mockRejectedValue(new Error('site unreachable'))
    const mounted = mount(ReaderView)
    await flushPromises()
    return mounted
  }

  it('restores the degraded position from localStorage (same priority as readProgress)', async () => {
    localStorage.setItem(PROGRESS_KEY, '8')
    wrapper = await mountDegraded()
    // 降级壳恢复本机进度；totalPages=1 是假值，不做上界钳制。
    expect(wrapper.find('.image-reader-stub').text()).toBe('8')
    expect(wrapper.find('[data-testid="reader-degraded-banner"]').exists()).toBe(true)
    // 降级态不碰服务器。
    expect(galleryApi.addHistory).not.toHaveBeenCalled()
  })

  it('gives the deep link priority over the localStorage fallback in degraded mode', async () => {
    routeParams.page = '2'
    localStorage.setItem(PROGRESS_KEY, '8')
    wrapper = await mountDegraded()
    expect(wrapper.find('.image-reader-stub').text()).toBe('2')
  })

  it('writes the position back to localStorage on unmount flush', async () => {
    wrapper = await mountDegraded()
    expect(localStorage.getItem(PROGRESS_KEY)).toBeNull()
    // 降级壳里翻页信号被钳到 0（totalPages=1 假值）——离开时 flush 把
    // 当前位置写进 localStorage（W4④：flush/leave 时写入）。
    flipTo(wrapper, 5)
    await flushPromises()
    wrapper.unmount()
    wrapper = undefined
    expect(localStorage.getItem(PROGRESS_KEY)).toBe('0')
  })

  it('rewrites the restored value on leave so the session position persists', async () => {
    localStorage.setItem(PROGRESS_KEY, '8')
    wrapper = await mountDegraded()
    // 未做任何翻页：leave flush 以恢复值重写（8 !== lastHistoryPage(-1)）。
    wrapper.unmount()
    wrapper = undefined
    expect(localStorage.getItem(PROGRESS_KEY)).toBe('8')
  })

  it('flushes the degraded position via pagehide', async () => {
    localStorage.setItem(PROGRESS_KEY, '8')
    wrapper = await mountDegraded()
    window.dispatchEvent(new Event('pagehide'))
    await flushPromises()
    expect(localStorage.getItem(PROGRESS_KEY)).toBe('8')
  })
})
