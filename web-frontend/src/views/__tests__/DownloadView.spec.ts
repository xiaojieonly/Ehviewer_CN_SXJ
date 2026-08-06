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
    startRange: vi.fn(),
    stopRange: vi.fn(),
    deleteRange: vi.fn(),
    move: vi.fn(),
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
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()

    // 默认偏好：每页 50 条（与 Android 一致）+ 添加时间倒序（最新在前）。
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 0, 50, 'time_desc')
    expect(wrapper.find('[data-testid="content-state-content"]').exists()).toBe(true)
    const items = wrapper.findAll('.download-list__item')
    // Virtualized: only the rows around the window are mounted — without
    // virtualization all 50 loaded rows would be in the DOM.
    expect(items.length).toBeGreaterThan(0)
    expect(items.length).toBeLessThan(50)
    expect(wrapper.text()).toContain('Dl 1')
  })

  it('appends pages on scroll-to-bottom until the total is reached', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()
    expect(downloadApi.list).toHaveBeenCalledTimes(1)

    // 50 rows × 160px estimate → last row starts at 49 × 160 = 7840.
    const el = scroller()
    await scrollTo(el, 49 * 160)
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 50, 50, 'time_desc')
    expect(downloadApi.list).toHaveBeenCalledTimes(2)

    // 逐段下探：每页 50 行 → 100/150/200 行，最后 250 total 封顶不再请求。
    await scrollTo(el, 99 * 160)
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 100, 50, 'time_desc')
    expect(downloadApi.list).toHaveBeenCalledTimes(3)

    await scrollTo(el, 149 * 160)
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 150, 50, 'time_desc')
    expect(downloadApi.list).toHaveBeenCalledTimes(4)

    await scrollTo(el, 199 * 160)
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 200, 50, 'time_desc')
    expect(downloadApi.list).toHaveBeenCalledTimes(5)

    // 250 total reached — further scrolling must not request page 6.
    await scrollTo(el, 249 * 160)
    expect(downloadApi.list).toHaveBeenCalledTimes(5)

    // The DOM stays bounded to the virtual window throughout.
    expect(wrapper.findAll('.download-list__item').length).toBeLessThan(50)
  })

  it('resets pagination when switching label tabs', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (label, offset = 0, limit = 50) => {
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
    expect(downloadApi.list).toHaveBeenLastCalledWith(5, 0, 50, 'time_desc')
    expect(wrapper.text()).toContain('Dl 1')
    // Scrolling to the (160-row) labeled tail loads its remaining pages.
    await scrollTo(scroller(), 49 * 160)
    expect(downloadApi.list).toHaveBeenLastCalledWith(5, 50, 50, 'time_desc')
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
  /* ---------------- multi-select (Android choice mode) ---------------- */

  it('enters multi-select mode on right-click and toggles the row', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()
    expect(wrapper.find('.select-bar').exists()).toBe(false)

    const row = wrapper.find('.download-item')
    await row.trigger('contextmenu')

    expect(wrapper.find('.select-bar').exists()).toBe(true)
    expect(wrapper.find('.select-bar__count').text()).toBe('已选 1 项')
    expect(row.classes()).toContain('download-item--selected')
    // FAB cluster hidden while selecting (Android choice mode).
    expect(wrapper.find('.fab-layout').exists()).toBe(false)

    // Clicking the row body toggles the selection off.
    await row.trigger('click')
    expect(wrapper.find('.select-bar__count').text()).toBe('已选 0 项')
  })

  it('selects all loaded rows and exits via the close button', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()
    await wrapper.find('.download-item').trigger('contextmenu')

    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '全选')!.trigger('click')
    expect(wrapper.find('.select-bar__count').text()).toBe('已全选')

    await wrapper.find('.select-bar__close').trigger('click')
    expect(wrapper.find('.select-bar').exists()).toBe(false)
    expect(wrapper.find('.fab-layout').exists()).toBe(true)
  })

  it('batch start resumes the selected idle rows', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: [
        makeDownload(1, { state: 0 }),
        makeDownload(2, { state: 4 }),
        makeDownload(3, { state: 2 }),
      ],
      labels: [],
      total: 3,
    }))
    vi.mocked(downloadApi.startRange).mockResolvedValue(2)
    await mountView()

    await wrapper.find('.download-item').trigger('contextmenu')
    // Select all three (2 is downloading → not startable).
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '全选')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '开始')!.trigger('click')
    await flushPromises()

    expect(downloadApi.startRange).toHaveBeenCalledWith([1, 2])
    expect(downloadApi.startRange).toHaveBeenCalledTimes(1)
    // Toast teleports to body — assert on document.
    expect(document.querySelector('.toast')?.textContent).toContain('Started 2 downloads')
    // Optimistic state flips; select mode exits.
    expect(wrapper.find('.select-bar').exists()).toBe(false)
  })

  it('batch stop pauses the selected active rows', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: [
        makeDownload(1, { state: 1 }),
        makeDownload(2, { state: 2 }),
        makeDownload(3, { state: 3 }),
      ],
      labels: [],
      total: 3,
    }))
    vi.mocked(downloadApi.stopRange).mockResolvedValue(2)
    await mountView()

    await wrapper.find('.download-item').trigger('contextmenu')
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '全选')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '停止')!.trigger('click')
    await flushPromises()

    expect(downloadApi.stopRange).toHaveBeenCalledWith([1, 2])
    expect(document.querySelector('.toast')?.textContent).toContain('Stopped 2 downloads')
  })

  it('batch delete confirms, removes the rows and clamps the total', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: [makeDownload(1), makeDownload(2), makeDownload(3)],
      labels: [],
      total: 3,
    }))
    vi.mocked(downloadApi.deleteRange).mockResolvedValue(2)
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    await mountView()

    await wrapper.find('.download-item').trigger('contextmenu')
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '删除')!.trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('删除选中的 1 项'))
    expect(downloadApi.deleteRange).toHaveBeenCalledWith([1])
    expect(wrapper.find('.select-bar').exists()).toBe(false)
    confirm.mockRestore()
  })

  it('move opens the label picker and assigns the target label', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: [makeDownload(1), makeDownload(2)],
      labels: [{ id: 5, label: 'Work', time: 1 }],
      total: 2,
    }))
    vi.mocked(downloadApi.move).mockResolvedValue(1)
    await mountView()

    await wrapper.find('.download-item').trigger('contextmenu')
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '移动')!.trigger('click')
    await flushPromises()
    // Dialog teleports to body.
    expect(document.querySelector('.dialog__label-list')).not.toBeNull()
    expect(document.querySelector('.dialog__label-list')?.textContent).toContain('Work')

    const workOption = Array.from(document.querySelectorAll('.dialog__label-option')!)
      .find((b) => b.textContent === 'Work')!
    ;(workOption as HTMLElement).click()
    await flushPromises()

    expect(downloadApi.move).toHaveBeenCalledWith([1], 5)
    expect(document.querySelector('.dialog__label-list')).toBeNull()
})
})
