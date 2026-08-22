import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ReaderView from '../ReaderView.vue'
import { galleryApi } from '@/api/gallery'

const { pushMock, replaceMock, routeParams } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  replaceMock: vi.fn(),
  routeParams: { gid: '123456', page: undefined as string | undefined },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: routeParams }),
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
}))

vi.mock('@/api/gallery', () => ({
  galleryApi: { getDetail: vi.fn(), addHistory: vi.fn() },
}))

vi.mock('@/composables/useKeyboardNav', () => ({
  useKeyboardNav: () => {},
}))

vi.mock('@/composables/useEnhancedImage', () => ({
  useEnhancedImage: () => ({ enhancedUrls: {}, connect: vi.fn() }),
}))

/**
 * ImageReader stub — F1's spec is about the writeback wiring, not the reader
 * chrome. Page flips are driven by emitting `update:current-page`, the same
 * contract the real chrome uses.
 */
vi.mock('@/components/reader/ImageReader.vue', () => ({
  default: {
    name: 'ImageReader',
    props: ['gid', 'title', 'totalPages', 'currentPage'],
    emits: ['update:current-page'],
    template: '<div class="image-reader-stub">{{ currentPage }}</div>',
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

  it('records the visit exactly once with token+title when entering the reader', async () => {
    wrapper = await mountReader()
    await flushPromises()
    expect(galleryApi.addHistory).toHaveBeenCalledTimes(1)
    expect(galleryApi.addHistory).toHaveBeenCalledWith(123456, {
      token: 'a1b2c3d4e5',
      title: 'Sample Gallery',
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
