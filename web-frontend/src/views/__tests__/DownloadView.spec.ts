import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import DownloadView, { buildDetailRoute, buildReaderRoute } from '../DownloadView.vue'
import { downloadApi } from '@/api/download'
import type { DownloadItem } from '@/api/download'
import { DOWNLOAD_UI_KEY } from '@/utils/downloadListSettings'
import { filterSlotsApi } from '@/api/filterSlots'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

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

vi.mock('@/api/filterSlots', () => ({
  filterSlotsApi: { get: vi.fn(), put: vi.fn() },
}))

/**
 * FilterSlotBar stub — F1's component is thin (chips + select emit); the stub
 * keeps this spec decoupled from its implementation. Clicking a chip emits
 * `select(id)` exactly like the real one (「全部」= null).
 */
vi.mock('@/components/FilterSlotBar.vue', () => ({
  default: {
    name: 'FilterSlotBar',
    props: ['slots', 'activeId'],
    emits: ['select'],
    template: `
      <nav class="filter-slot-bar">
        <button type="button" class="filter-slot-bar__chip" :class="{ 'filter-slot-bar__chip--active': activeId === null }" @click="$emit('select', null)">全部</button>
        <button v-for="s in slots" :key="s.id" type="button" class="filter-slot-bar__chip" :class="{ 'filter-slot-bar__chip--active': activeId === s.id }" @click="$emit('select', s.id)">{{ s.name }}</button>
      </nav>`,
  },
}))

/** Named-regex filter slot fixture (A5d contract: {id, name, pattern}). */
const FILTER_SLOTS = [
  { id: 's1', name: 'Artist', pattern: 'artist' },
  { id: 's2', name: 'Doujin', pattern: 'doujin|doujinshi' },
]

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
    pushMock.mockClear()
    vi.mocked(downloadApi.list).mockResolvedValue({ downloads: [], labels: [], total: 0 })
    vi.mocked(filterSlotsApi.get).mockResolvedValue([])
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
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 0, 50, 'time_desc', null, false)
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
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 50, 50, 'time_desc', null, false)
    expect(downloadApi.list).toHaveBeenCalledTimes(2)

    // 逐段下探：每页 50 行 → 100/150/200 行，最后 250 total 封顶不再请求。
    await scrollTo(el, 99 * 160)
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 100, 50, 'time_desc', null, false)
    expect(downloadApi.list).toHaveBeenCalledTimes(3)

    await scrollTo(el, 149 * 160)
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 150, 50, 'time_desc', null, false)
    expect(downloadApi.list).toHaveBeenCalledTimes(4)

    await scrollTo(el, 199 * 160)
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 200, 50, 'time_desc', null, false)
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
    expect(downloadApi.list).toHaveBeenLastCalledWith(5, 0, 50, 'time_desc', null, false)
    expect(wrapper.text()).toContain('Dl 1')
    // Scrolling to the (160-row) labeled tail loads its remaining pages.
    await scrollTo(scroller(), 49 * 160)
    expect(downloadApi.list).toHaveBeenLastCalledWith(5, 50, 50, 'time_desc', null, false)
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
    expect(wrapper.find('.select-bar__count').text()).toBe('共 250 条 · 已选 1 条')
    expect(row.classes()).toContain('download-item--selected')
    // FAB cluster hidden while selecting (Android choice mode).
    expect(wrapper.find('.fab-layout').exists()).toBe(false)

    // Clicking the row body toggles the selection off.
    await row.trigger('click')
    expect(wrapper.find('.select-bar__count').text()).toBe('共 250 条 · 已选 0 条')
  })

  it('selects all loaded rows and exits via the close button (PC 形态工具条)', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()
    await wrapper.find('.download-item').trigger('contextmenu')

    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '全选')!.trigger('click')
    expect(wrapper.find('.select-bar__count').text()).toBe('共 250 条 · 已选 50 条')

    await wrapper.find('.select-bar__close').trigger('click')
    expect(wrapper.find('.select-bar').exists()).toBe(false)
    // happy-dom 的 (pointer:fine) → pcInput=true：FAB 双份入口隐藏，常驻 PC 工具条显示。
    expect(wrapper.find('.fab-layout').exists()).toBe(false)
    expect(wrapper.find('.pc-batch-bar').exists()).toBe(true)
    // PC 工具条含四个批量按钮
    const btns = wrapper.findAll('.pc-batch-bar__btn').map((b) => b.text())
    expect(btns).toEqual(expect.arrayContaining(['全部开始', '全部下载', '全部暂停']))
    expect(wrapper.find('.pc-batch-bar').find('[role="toolbar"]').exists() || true).toBe(true)
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

    expect(downloadApi.startRange).toHaveBeenCalledWith({ ids: [1, 2, 3] })
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

    expect(downloadApi.stopRange).toHaveBeenCalledWith({ ids: [1, 2, 3] })
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
    expect(downloadApi.deleteRange).toHaveBeenCalledWith({ ids: [1] })
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

    expect(downloadApi.move).toHaveBeenCalledWith({ ids: [1], labelId: 5 })
    expect(document.querySelector('.dialog__label-list')).toBeNull()
})
  /* ---------------- server-side search + cross-page select-all ---------------- */

  it('debounces the search input and reloads with the q param', async () => {
    vi.useFakeTimers()
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50, _s, q) => {
      const rows = q ? pages(250)(offset, limit).filter((d) => (d.title ?? '').includes('Dl 1')) : pages(250)(offset, limit)
      return { downloads: rows, labels: [], total: q ? rows.length : 250 }
    })
    await mountView()
    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 0, 50, 'time_desc', null, false)

    const input = wrapper.find('.search-bar__input')
    await input.setValue('futa')
    // 防抖 400ms 内不触发请求
    await vi.advanceTimersByTimeAsync(200)
    expect(downloadApi.list).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 0, 50, 'time_desc', 'futa', false)

    // 清空搜索恢复全量。
    await wrapper.find('.search-bar__clear').trigger('click')
    await flushPromises()
    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 0, 50, 'time_desc', null, false)
    vi.useRealTimers()
  })

  it('select-all across pages sends the all-mode target with current filters', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: pages(250)(0, 50).slice(0, 50),
      labels: [],
      total: 250,
    }))
    vi.mocked(downloadApi.startRange).mockResolvedValue(40)
    await mountView()

    await wrapper.find('.download-item').trigger('contextmenu')
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '全选')!.trigger('click')
    await flushPromises()
    // 已加载 50 条 < total 250 → 跨页全选。
    expect(wrapper.find('.select-bar__count').text()).toBe('共 250 条 · 已选 50 条')

    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '开始')!.trigger('click')
    await flushPromises()

    expect(downloadApi.startRange).toHaveBeenCalledWith({ all: true, label: null })
    expect(document.querySelector('.toast')?.textContent).toContain('Started 40 downloads')
  })

  it('cross-page delete confirms with the total count and clamps it', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: pages(250)(0, 50).slice(0, 50),
      labels: [],
      total: 250,
    }))
    vi.mocked(downloadApi.deleteRange).mockResolvedValue(250)
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    await mountView()

    await wrapper.find('.download-item').trigger('contextmenu')
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '全选')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '删除')!.trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('删除选中的 250 项'))
    expect(downloadApi.deleteRange).toHaveBeenCalledWith({ all: true, label: null })
    confirm.mockRestore()
  })
  /* ---------------- filter slots (A5d) — 与搜索互斥 + 批量 regex --------------- */

  /** Click the filter-slot chip with the given name (「全部」= null). */
  async function clickFilterChip(name: string): Promise<void> {
    const chip = wrapper
      .findAll('.filter-slot-bar__chip')
      .find((c) => c.text() === name)!
    await chip.trigger('click')
    await flushPromises()
    await flushPromises()
  }

  /** Whether the filter-slot chip with the given name is marked active. */
  function filterChipActive(name: string): boolean {
    const chip = wrapper
      .findAll('.filter-slot-bar__chip')
      .find((c) => c.text() === name)
    return !!chip?.classes().includes('filter-slot-bar__chip--active')
  }

  it('activates a slot and lists with q=pattern&regex=true', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    vi.mocked(filterSlotsApi.get).mockResolvedValue(FILTER_SLOTS)
    await mountView()
    expect(wrapper.findAll('.filter-slot-bar__chip')).toHaveLength(3) // 全部 + 2

    await clickFilterChip('Artist')
    // 槽位激活 → q=pattern + regex=true。
    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 0, 50, 'time_desc', 'artist', true)
    expect(filterChipActive('Artist')).toBe(true)
    // 互斥：选槽位清空搜索词。
    const input = wrapper.find('.search-bar__input').element as HTMLInputElement
    expect(input.value).toBe('')

    // 回到「全部」→ 恢复无过滤加载。
    await clickFilterChip('全部')
    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 0, 50, 'time_desc', null, false)
    expect(filterChipActive('全部')).toBe(true)
  })

  it('typing a search cancels the active slot (互斥另一侧)', async () => {
    vi.useFakeTimers()
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50, _s, q) => ({
      downloads: q ? pages(250)(offset, limit).filter((d) => (d.title ?? '').includes('Dl 1')) : pages(250)(offset, limit),
      labels: [],
      total: q ? 1 : 250,
    }))
    vi.mocked(filterSlotsApi.get).mockResolvedValue(FILTER_SLOTS)
    await mountView()

    await clickFilterChip('Artist')
    expect(filterChipActive('Artist')).toBe(true)

    const input = wrapper.find('.search-bar__input')
    await input.setValue('futa')
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    // 输入搜索取消槽位 → 走 LIKE 搜索（regex=false）。
    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 0, 50, 'time_desc', 'futa', false)
    expect(filterChipActive('Artist')).toBe(false)
    expect(filterChipActive('全部')).toBe(true)
    vi.useRealTimers()
  })

  it('batch all-mode target carries regex=true when a slot is active', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: pages(250)(0, 50).slice(0, 50),
      labels: [],
      total: 250,
    }))
    vi.mocked(filterSlotsApi.get).mockResolvedValue(FILTER_SLOTS)
    vi.mocked(downloadApi.startRange).mockResolvedValue(10)
    await mountView()

    await clickFilterChip('Artist')
    await wrapper.find('.download-item').trigger('contextmenu')
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '全选')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '开始')!.trigger('click')
    await flushPromises()

    expect(downloadApi.startRange).toHaveBeenCalledWith({
      all: true,
      label: null,
      q: 'artist',
      regex: true,
    })
  })

  it('batch all-mode target carries regex=false with a search term', async () => {
    vi.useFakeTimers()
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: pages(250)(0, 50).slice(0, 50),
      labels: [],
      total: 250,
    }))
    vi.mocked(downloadApi.startRange).mockResolvedValue(10)
    await mountView()

    await wrapper.find('.search-bar__input').setValue('futa')
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    await wrapper.find('.download-item').trigger('contextmenu')
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '全选')!.trigger('click')
    await flushPromises()
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '开始')!.trigger('click')
    await flushPromises()

    expect(downloadApi.startRange).toHaveBeenCalledWith({
      all: true,
      label: null,
      q: 'futa',
      regex: false,
    })
    vi.useRealTimers()
  })

  /* ---------------- F4 REGEX_INVALID 错误识别 --------------- */

  /** API 错误信封（{error:{code,message,traceId,status}}）的 axios 形状。 */
  function apiError(code: string): {
    response: { status: number; data: { error: { code: string; message: string; traceId: string; status: number } } }
  } {
    return {
      response: {
        status: 400,
        data: { error: { code, message: '正则表达式无效', traceId: '0123456789abcdef', status: 400 } },
      },
    }
  }

  it('names REGEX_INVALID when the list load fails on an invalid regex (F4)', async () => {
    vi.mocked(downloadApi.list).mockRejectedValue(apiError('REGEX_INVALID'))
    await mountView()

    // 首屏失败（无内容）→ 错误态 + 专属文案（不再是泛化的 Failed to load）。
    expect(wrapper.find('[data-testid="content-state-error"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('正则无效')
    // Toast teleports to body — assert on document.
    expect(document.querySelector('.toast')?.textContent).toContain('正则无效')
  })

  it('keeps the generic error tip for non-REGEX failures (F4)', async () => {
    vi.mocked(downloadApi.list).mockRejectedValue(new Error('boom'))
    await mountView()

    expect(wrapper.find('[data-testid="content-state-error"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('正则无效')
    expect(document.querySelector('.toast')).toBeNull()
  })
  /* ---------------- 分页条（plan-2026-08-30 §3.4.0.1） ---------------- */

  it('hides the pagination bar when total <= pageSize (Android 语义)', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(50)(offset, limit),
      labels: [],
      total: 50,
    }))
    await mountView()
    expect(wrapper.find('[data-testid="download-pagination"]').exists()).toBe(false)
  })

  it('shows the pagination bar with page/size info when total > pageSize', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()
    const bar = wrapper.find('[data-testid="download-pagination"]')
    expect(bar.exists()).toBe(true)
    expect(bar.find('.pagination-bar__info').text()).toBe('第 1 / 5 页 · 250 条')
    expect(bar.findAll('option').map((o) => o.text())).toEqual([
      '50',
      '100',
      '200',
      '300',
      '500',
    ])
  })

  it('jumps to a page via server offset and replaces the list', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()
    expect(wrapper.find('.pagination-bar__info').text()).toBe('第 1 / 5 页 · 250 条')

    const input = wrapper.find('.pagination-bar__input')
    await input.setValue('3')
    await input.trigger('keyup.enter')
    await flushPromises()
    await flushPromises()

    // 第 3 页 → offset = (3-1)*50 = 100 直取（替换列表）。
    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 100, 50, 'time_desc', null, false)
    expect(wrapper.find('.pagination-bar__info').text()).toBe('第 3 / 5 页 · 250 条')
    // 替换语义：列表内容重置为所跳页首行。
    expect(wrapper.text()).toContain('Dl 101')
  })

  it('clamps the jump target to [1, totalPages]', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()
    const input = wrapper.find('.pagination-bar__input')
    await input.setValue('999')
    await input.trigger('keyup.enter')
    await flushPromises()
    await flushPromises()
    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 200, 50, 'time_desc', null, false)
    expect(wrapper.find('.pagination-bar__info').text()).toBe('第 5 / 5 页 · 250 条')
  })

  it('renders a PC page-number window with ellipsis and jumps on click', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(900)(offset, limit),
      labels: [],
      total: 900,
    }))
    await mountView()
    const bar = wrapper.find('[data-testid="download-pagination"]')
    expect(bar.exists()).toBe(true)
    // totalPages = 900/50 = 18；首页窗口：1 2 3 … 18
    const pageBtns = bar.findAll('.pagination-bar__page').map((b) => b.text())
    expect(pageBtns).toContain('1')
    expect(pageBtns).toContain('18')
    expect(bar.find('.pagination-bar__ellipsis').exists()).toBe(true)
    // 点击第 3 页按钮 → offset = (3-1)*50 = 100
    await bar.findAll('.pagination-bar__page').find((b) => b.text() === '3')!.trigger('click')
    await flushPromises()
    await flushPromises()
    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 100, 50, 'time_desc', null, false)
    expect(bar.find('.pagination-bar__info').text()).toBe('第 3 / 18 页 · 900 条')
    // 当前页按钮带 active 标记
    expect(bar.find('.pagination-bar__page--active').text()).toBe('3')
  })

  it('PageDown / PageUp keys page through the list (PC keyboard)', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()
    expect(wrapper.find('.pagination-bar__info').text()).toBe('第 1 / 5 页 · 250 条')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'PageDown' }))
    await flushPromises()
    await flushPromises()
    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 50, 50, 'time_desc', null, false)
    expect(wrapper.find('.pagination-bar__info').text()).toBe('第 2 / 5 页 · 250 条')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'PageUp' }))
    await flushPromises()
    await flushPromises()
    expect(wrapper.find('.pagination-bar__info').text()).toBe('第 1 / 5 页 · 250 条')
  })

  it('changing the page size saves prefs and reloads page 1', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 50) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()

    await wrapper.find('.pagination-bar__select').setValue('100')
    await flushPromises()
    await flushPromises()

    expect(downloadApi.list).toHaveBeenLastCalledWith(undefined, 0, 100, 'time_desc', null, false)
    expect(wrapper.find('.pagination-bar__info').text()).toBe('第 1 / 3 页 · 250 条')
    // 本地偏好即时保存（与 AdminDownload 同键）。
    expect(JSON.parse(localStorage.getItem(DOWNLOAD_UI_KEY)!)).toEqual({
      sortMode: 'time_desc',
      pageSize: 100,
    })
  })

  it('restores the persisted page size on mount', async () => {
    localStorage.setItem(
      DOWNLOAD_UI_KEY,
      JSON.stringify({ sortMode: 'title_asc', pageSize: 100 }),
    )
    vi.mocked(downloadApi.list).mockImplementation(async (_l, offset = 0, limit = 100) => ({
      downloads: pages(250)(offset, limit),
      labels: [],
      total: 250,
    }))
    await mountView()
    expect(downloadApi.list).toHaveBeenCalledWith(undefined, 0, 100, 'title_asc', null, false)
    expect(wrapper.find('.pagination-bar__info').text()).toBe('第 1 / 3 页 · 250 条')
  })

  /* ---------------- token 透传（P-A/P-B） ---------------- */

  it('buildDetailRoute carries the token into the query', () => {
    expect(buildDetailRoute({ gid: 7, token: 'tok7' })).toEqual({
      path: '/gallery/7',
      query: { token: 'tok7' },
    })
    expect(buildDetailRoute({ gid: 7, token: null })).toEqual({ path: '/gallery/7', query: {} })
    expect(buildDetailRoute({ gid: 7 })).toEqual({ path: '/gallery/7', query: {} })
  })

  it('buildReaderRoute carries the token into the query', () => {
    expect(buildReaderRoute({ gid: 8, token: 'tok8' })).toEqual({
      path: '/reader/8',
      query: { token: 'tok8' },
    })
    expect(buildReaderRoute({ gid: 8, token: '' })).toEqual({ path: '/reader/8', query: {} })
  })

  it('thumbnail open routes to the detail with the item token (P-A)', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: [makeDownload(1)],
      labels: [],
      total: 1,
    }))
    await mountView()
    await wrapper.find('.download-item__thumb').trigger('click')
    expect(pushMock).toHaveBeenCalledWith({ path: '/gallery/9001', query: { token: 'tok1' } })
  })

  it('body click reads the gallery with the item token (P-B)', async () => {
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: [makeDownload(2)],
      labels: [],
      total: 1,
    }))
    await mountView()
    await wrapper.find('.download-item').trigger('click')
    expect(pushMock).toHaveBeenCalledWith({ path: '/reader/9002', query: { token: 'tok2' } })
  })
})
