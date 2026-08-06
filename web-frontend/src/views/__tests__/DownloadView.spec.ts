import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import DownloadView from '../DownloadView.vue'
import { downloadApi } from '@/api/download'
import type { DownloadItem } from '@/api/download'

vi.mock('@/api/download', () => ({
  downloadApi: {
    list: vi.fn(),
    getInfo: vi.fn(),
    add: vi.fn(),
    start: vi.fn(),
    startAll: vi.fn(),
    pause: vi.fn(),
    cancel: vi.fn(),
    delete: vi.fn(),
    createLabel: vi.fn(),
    deleteLabel: vi.fn(),
  },
}))

/**
 * WebSocket stub — the view only uses connected/connect/subscribeAll.
 * `connected` is a real Vue ref (the view watches it); the factory below
 * swaps the plain holder for the ref so tests can flip it before mounting.
 */
const ws = vi.hoisted(() => {
  const connected: { value: boolean } = { value: false }
  const subscribeAll = vi.fn()
  const connect = vi.fn()
  const disconnect = vi.fn()
  return { connected, subscribeAll, connect, disconnect }
})

vi.mock('@/composables/useWebSocket', async () => {
  const { ref } = await import('vue')
  const connected = ref(false)
  ws.connected = connected
  return {
    useWebSocket: vi.fn(() => ({
      connectionState: { value: 'disconnected' },
      lastError: { value: null },
      authError: { value: null },
      connected,
      connect: ws.connect,
      disconnect: ws.disconnect,
      subscribe: vi.fn(() => vi.fn()),
      subscribeDownload: vi.fn(() => undefined),
      subscribeAll: ws.subscribeAll,
      subscribeProcessing: vi.fn(() => vi.fn()),
      subscribeJob: vi.fn(() => vi.fn()),
      subscribeJobs: vi.fn(() => vi.fn()),
    })),
  }
})

/**
 * ResizeObserver stub: happy-dom's implementation is a no-op that never
 * delivers entries, and @tanstack/virtual-core skips range calculation while
 * the observed rect is zero-sized (`calculateRange` returns null when
 * `outerSize === 0`). Delivering an 800px viewport on `observe()` makes the
 * virtualizer behave like a real browser (a generous desktop list height).
 */
function stubResizeObserver(): void {
  class FakeResizeObserver {
    private readonly callback: (entries: Array<{ borderBoxSize: Array<{ inlineSize: number; blockSize: number }> }>) => void

    constructor(callback: (entries: Array<{ borderBoxSize: Array<{ inlineSize: number; blockSize: number }> }>) => void) {
      this.callback = callback
    }

    observe(): void {
      this.callback([{ borderBoxSize: [{ inlineSize: 800, blockSize: 800 }] }])
    }

    unobserve(): void {}
    disconnect(): void {}
  }
  vi.stubGlobal('ResizeObserver', FakeResizeObserver)
}

/** Download row fixture; `overrides` patches individual fields. */
function makeDownload(id: number, overrides: Partial<DownloadItem> = {}): DownloadItem {
  return {
    id,
    gid: 9000 + id,
    token: `tok${id}`,
    title: `Dl ${id}`,
    titleJpn: null,
    thumb: null,
    category: 0,
    state: 0,
    total: 10,
    done: 0,
    label: 0,
    downloadDir: null,
    error: null,
    ...overrides,
  }
}

/** `total` rows split into pages of `limit`, ids starting at 1. */
function pages(total: number): (offset: number, limit: number) => DownloadItem[] {
  return (offset, limit) =>
    Array.from({ length: Math.min(limit, total - offset) }, (_, i) => makeDownload(offset + i + 1))
}

describe('DownloadView (虚拟滚动 + 分页加载, plan-2026-08-06 A5/A7)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    localStorage.clear()
    vi.mocked(downloadApi.list).mockResolvedValue({ downloads: [], labels: [], total: 0 })
    ws.subscribeAll.mockClear()
    ws.connected.value = false
    stubResizeObserver()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  /** Mount the view and settle every microtask + reactive flush (virtualizer
      subscribes to the scroller after the content state mounts). */
  async function mountView(): Promise<VueWrapper> {
    wrapper = mount(DownloadView)
    await flushPromises()
    await flushPromises()
    await nextTick()
    await flushPromises()
    return wrapper
  }

  /** Simulate the user scrolling the FastScroller container to `scrollTop`. */
  async function scrollTo(el: HTMLElement, scrollTop: number): Promise<void> {
    Object.defineProperty(el, 'scrollTop', { value: scrollTop, configurable: true })
    Object.defineProperty(el, 'clientHeight', { value: 800, configurable: true })
    el.dispatchEvent(new Event('scroll'))
    await flushPromises()
    await flushPromises()
  }

  /** The scroller ContentLayout's FastScroller owns. */
  function scroller(): HTMLElement {
    return wrapper.find('.fast-scroller__container').element as HTMLElement
  }

  it('loads the first page with offset 0 and mounts only the virtual window', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 100) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()

    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 0, 100)
    expect(wrapper.find('[data-testid="content-state-content"]').exists()).toBe(true)
    const items = wrapper.findAll('.download-list__item')
    // Virtualized: only the rows around the window are mounted — without
    // virtualization all 100 loaded rows would be in the DOM.
    expect(items.length).toBeGreaterThan(0)
    expect(items.length).toBeLessThan(50)
    expect(wrapper.text()).toContain('Dl 1')
  })

  it('appends pages on scroll-to-bottom until the total is reached', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 100) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()
    expect(downloadApi.list).toHaveBeenCalledTimes(1)

    // 100 rows × 160px estimate → last row starts at 99 × 160 = 15840.
    const el = scroller()
    await scrollTo(el, 99 * 160)
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 100, 100)
    expect(downloadApi.list).toHaveBeenCalledTimes(2)

    // Second page lands (200 rows); the tail moved to 199 × 160.
    await scrollTo(el, 199 * 160)
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 200, 100)
    expect(downloadApi.list).toHaveBeenCalledTimes(3)

    // 250 total reached — further scrolling must not request page 4.
    await scrollTo(el, 249 * 160)
    expect(downloadApi.list).toHaveBeenCalledTimes(3)

    // The DOM stays bounded to the virtual window throughout.
    expect(wrapper.findAll('.download-list__item').length).toBeLessThan(50)
  })

  it('resets pagination when switching label tabs', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (label, offset = 0, limit = 100) => {
      if (label === 5) {
        return {
          downloads: pages(160)(offset, limit).map((d) => makeDownload(d.id, { ...d, label: 5 })),
          labels: [{ id: 5, label: 'Work', time: 1 }],
          total: 160,
        }
      }
      return { downloads: pages(250)(offset, limit), labels: [{ id: 5, label: 'Work', time: 1 }], total: 250 }
    })
    await mountView()
    expect(wrapper.findAll('.label-tabs__tab').map((t) => t.text())).toContain('Work')

    await wrapper.findAll('.label-tabs__tab').find((t) => t.text() === 'Work')!.trigger('click')
    await flushPromises()
    await flushPromises()
    await nextTick()
    await flushPromises()

    // Label switch restarts at offset 0 with the label param.
    expect(downloadApi.list).toHaveBeenLastCalledWith(5, 0, 100)
    expect(wrapper.text()).toContain('Dl 1')
    // Scrolling to the (160-row) labeled tail loads its remaining page.
    await scrollTo(scroller(), 99 * 160)
    expect(downloadApi.list).toHaveBeenLastCalledWith(5, 100, 100)
    expect(downloadApi.list).toHaveBeenCalledTimes(3)
  })

  it('rewrites external thumbnails through the image proxy in rendered rows', async () => {
    const thumb = 'https://ehgt.org/t/1/cover.jpg'
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 100) => ({
      downloads: pages(50)(offset, limit).map((d) => (d.id === 1 ? { ...d, thumb } : d)),
      labels: [],
      total: 50,
    }))
    await mountView()

    const img = wrapper.find('.download-item__thumb img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe(`/api/v1/image/proxy?url=${encodeURIComponent(thumb)}`)
  })

  it('updates a rendered row from the WebSocket progress feed', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 100) => ({
      downloads: pages(50)(offset, limit),
      labels: [],
      total: 50,
    }))
    ws.connected.value = true
    await mountView()
    expect(ws.subscribeAll).toHaveBeenCalledTimes(1)

    const handleProgress = ws.subscribeAll.mock.calls[0]![0] as (p: {
      gid: number
      state: number
      downloaded: number
      total: number
      speed: number
      label: number
    }) => void
    // Row 1 (gid 9001) is inside the virtual window at the top.
    handleProgress({ gid: 9001, state: 2, downloaded: 5, total: 10, speed: 0, label: 0 })
    await nextTick()

    expect(wrapper.text()).toContain('5/10 pages')
    expect(wrapper.text()).toContain('Downloading')
  })

  it('shows the empty state when the list has no rows', async () => {
    vi.mocked(downloadApi.list).mockResolvedValue({ downloads: [], labels: [], total: 0 })
    await mountView()
    expect(wrapper.find('[data-testid="content-state-empty"]').exists()).toBe(true)
  })
})
