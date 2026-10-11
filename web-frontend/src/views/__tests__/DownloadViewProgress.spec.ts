/**
 * DownloadView — P-F2（进度合帧）专项：
 * ① gid/id 索引（buildItemIndex）与原 Array#find 的等价断言；
 * ② WS 进度消息 rAF 合帧（一帧只批应用一次）、后台 tab 100ms 兜底 flush、
 *    卸载清理（rAF/定时器/缓冲）、未知 gid 与跨行定位的边界。
 *
 * 帧时钟全部走 fake（手动队列），不依赖 happy-dom 的 rAF 调度时机。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import DownloadView, { buildItemIndex } from '../DownloadView.vue'
import DownloadProgressRow from '@/components/download/DownloadProgressRow.vue'
import AppListRow from '@/components/gallery/AppListRow.vue'
import { downloadApi } from '@/api/download'
import { filterSlotsApi } from '@/api/filterSlots'
import type { DownloadItem } from '@/api/download'
import type { DownloadProgress } from '@/composables/useWebSocket'

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
    restartAll: vi.fn(),
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

vi.mock('@/api/preferences', () => ({
  preferencesApi: {
    get: vi.fn(async () => {
      throw new Error('preferences offline in tests')
    }),
    update: vi.fn(),
  },
}))

vi.mock('@/components/FilterSlotBar.vue', () => ({
  default: {
    name: 'FilterSlotBar',
    props: ['slots', 'activeId'],
    emits: ['select'],
    template: `<nav class="filter-slot-bar"><button type="button" class="filter-slot-bar__chip" @click="$emit('select', null)">全部</button></nav>`,
  },
}))

/** WebSocket stub（与 DownloadView.spec 同构：connected 真实 ref + 可探测的 subscribeAll）。 */
const ws = vi.hoisted(() => {
  const connected: { value: boolean } = { value: false }
  return {
    connected,
    subscribeAll: vi.fn(),
    connect: vi.fn(),
    disconnect: vi.fn(),
  }
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

/** ResizeObserver stub（虚拟器在 happy-dom 零尺寸下不算窗口，同 DownloadView.spec）。 */
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

/** 手动帧时钟：rAF 排队不自动执行，runFrames ≡ 一次渲染帧。 */
interface FrameClock {
  raf: ReturnType<typeof vi.fn>
  cancelRaf: ReturnType<typeof vi.fn>
  runFrames: () => void
}

let frames: FrameClock

function stubFrameClock(): void {
  const queue = new Map<number, FrameRequestCallback>()
  let seq = 0
  const raf = vi.fn((cb: FrameRequestCallback) => {
    seq += 1
    queue.set(seq, cb)
    return seq
  })
  const cancelRaf = vi.fn((id: number) => queue.delete(id))
  frames = {
    raf,
    cancelRaf,
    runFrames: () => {
      const pending = Array.from(queue.values())
      queue.clear()
      for (const cb of pending) cb(performance.now())
    },
  }
  vi.stubGlobal('requestAnimationFrame', raf)
  vi.stubGlobal('cancelAnimationFrame', cancelRaf)
}

/** 渲染计数 spy：包一层 instance.render（renderComponentRoot 每次重渲
    都从 instance 重新读取 render，挂载后替换即可拦截每一次重渲染）。
    render 不在 ComponentInternalInstance 的公开类型上——局部形状断言。 */
interface RenderCarrier {
  render: ((...args: unknown[]) => unknown) | null
}

function spyRenders(where: VueWrapper): () => number {
  const instance = (where.vm as unknown as { $: RenderCarrier }).$
  const original = instance.render
  if (!original) throw new Error('component has no render function')
  let count = 0
  instance.render = (...args: unknown[]) => {
    count += 1
    return original(...args)
  }
  return () => count
}

/**
 * 行渲染单元 spy：meta/stats/track/footer 都是 DownloadProgressRow 传给
 * AppListRow 的 **slot 内容**——slot 函数在 AppListRow 的渲染 effect 里执行，
 * done/速率等依赖记在 AppListRow 名下。所以「一行字段变化只重渲染该行」
 * 的最小渲染单元是行内 AppListRow 实例，spy 打在它身上。
 */
function spyRowUnitRenders(row: VueWrapper): () => number {
  return spyRenders(row.findComponent(AppListRow))
}

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

/** 只假化墙钟，不假化 rAF——帧时钟归 stubFrameClock 手动管（vitest 默认
 *  toFake 含 requestAnimationFrame，会把手动帧队列替换成 sinon 的假帧）。 */
function useFakeWallClock(): void {
  vi.useFakeTimers({
    toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'],
  })
}

describe('DownloadView — WS 进度合帧与索引 (P-F2 ①②)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    pushMock.mockClear()
    vi.mocked(downloadApi.list).mockResolvedValue({ downloads: [], labels: [], total: 0 })
    // 槽位加载必须 resolve 数组——undefined 会让 useFilterSlots 的 activeSlot
    // computed 在后续 flush 中炸出 unhandled rejection。
    vi.mocked(filterSlotsApi.get).mockResolvedValue([])
    ws.subscribeAll.mockClear()
    ws.connected.value = false
    stubResizeObserver()
    stubFrameClock()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  async function mountWithRows(rows: DownloadItem[]): Promise<void> {
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: rows,
      labels: [],
      total: rows.length,
    }))
    ws.connected.value = true
    wrapper = mount(DownloadView)
    await flushPromises()
    await flushPromises()
    await nextTick()
    await flushPromises()
  }

  function progressHandler(): (p: DownloadProgress) => void {
    expect(ws.subscribeAll).toHaveBeenCalled()
    return ws.subscribeAll.mock.calls[0]![0] as (p: DownloadProgress) => void
  }

  it('coalesces a 50-message burst into exactly one frame application (fake rAF)', async () => {
    await mountWithRows([
      makeDownload(1, { state: 2 }),
      makeDownload(2, { state: 2 }),
      makeDownload(3, { state: 2 }),
    ])
    const handleProgress = progressHandler()

    const rows = wrapper.findAllComponents(DownloadProgressRow)
    expect(rows.length).toBeGreaterThanOrEqual(2)
    const rendersOfRowA = spyRowUnitRenders(rows[0]!)
    const rendersOfRowB = spyRowUnitRenders(rows[1]!)
    const rowARendersBefore = rendersOfRowA()

    frames.raf.mockClear()
    // 一帧内 50 条同 gid 高频消息（done 递增）。
    for (let done = 1; done <= 50; done += 1) {
      handleProgress({ gid: 9001, state: 2, downloaded: done, total: 10, speed: 0, label: 0 })
    }
    // 只排了一帧：消息线程只写缓冲，不逐条触发渲染调度。
    expect(frames.raf).toHaveBeenCalledTimes(1)

    frames.runFrames()
    await nextTick()

    // 一帧只批应用一次 → 行 A 恰好 +1 次渲染，且呈现最后一条的 done。
    expect(rendersOfRowA() - rowARendersBefore).toBe(1)
    expect(wrapper.find('.download-item__pages').text()).toBe('50/10 pages')
    // 行 B（gid 9002 无消息）零重渲染。
    expect(rendersOfRowB()).toBe(0)
  })

  it('applies progress only to the addressed row and tolerates unknown gids (Map 定位)', async () => {
    await mountWithRows([
      makeDownload(1, { state: 2 }),
      makeDownload(2, { state: 2 }),
      makeDownload(3, { state: 2 }),
    ])
    const handleProgress = progressHandler()

    // gid 9002 → 行 2；其余行不动（与旧 find 定位等价）。
    handleProgress({ gid: 9002, state: 3, downloaded: 10, total: 10, speed: 0, label: 0 })
    frames.runFrames()
    await nextTick()

    const pages = wrapper.findAll('.download-item__pages')
    expect(pages[0]!.text()).toBe('0/10 pages')
    expect(pages[1]!.text()).toBe('10/10 pages')
    expect(pages[2]!.text()).toBe('0/10 pages')

    // 列表里不存在的 gid：不抛错、不落 DOM。
    expect(() =>
      handleProgress({ gid: 424242, state: 2, downloaded: 1, total: 10, speed: 0, label: 0 }),
    ).not.toThrow()
    frames.runFrames()
    await nextTick()
    expect(wrapper.findAll('.download-item__pages')[0]!.text()).toBe('0/10 pages')
  })

  it('flushes the buffer via the 100ms fallback when rAF stalls (后台 tab 兜底, fake timers)', async () => {
    useFakeWallClock()
    await mountWithRows([makeDownload(1, { state: 2 }), makeDownload(2, { state: 2 })])
    const handleProgress = progressHandler()

    handleProgress({ gid: 9001, state: 2, downloaded: 7, total: 10, speed: 0, label: 0 })
    // 帧不走（后台 tab rAF 停摆）→ 缓冲尚未落地。
    await nextTick()
    expect(wrapper.find('.download-item__pages').text()).toBe('0/10 pages')

    // 100ms 兜底定时器把缓冲刷进响应式状态。
    await vi.advanceTimersByTimeAsync(100)
    await nextTick()
    expect(wrapper.find('.download-item__pages').text()).toBe('7/10 pages')
  })

  it('cancels the pending flush on unmount (卸载清理)', async () => {
    useFakeWallClock()
    await mountWithRows([makeDownload(1, { state: 2 })])
    const handleProgress = progressHandler()

    // 挂载基线上可能已有别的组件排的无关定时器——只关心本视图兜底定时器
    // 的增减。
    const baselineTimers = vi.getTimerCount()
    handleProgress({ gid: 9001, state: 2, downloaded: 3, total: 10, speed: 0, label: 0 })
    expect(vi.getTimerCount()).toBe(baselineTimers + 1)

    wrapper.unmount()
    wrapper = undefined as unknown as VueWrapper

    // 兜底定时器被取消（回到基线）+ 排队的帧回调被摘除（runFrames 空转）。
    expect(vi.getTimerCount()).toBe(baselineTimers)
    expect(frames.cancelRaf).toHaveBeenCalledWith(expect.any(Number))
    expect(() => frames.runFrames()).not.toThrow()
    await nextTick()
  })

  it('restart-all 后从服务端重拉权威状态（磁盘校验通过的行不再被乐观翻转成 Waiting）', async () => {
    // 首次列表：一行进行中（state=2）。点「全部下载」后服务端会做磁盘校验：
    // 内容已在盘上的行被标记完成且没有任务——收不到任何进度帧。原乐观翻转会
    // 把它显示成 Waiting 且再无事件纠正；修复后必须以服务端重拉结果为准。
    await mountWithRows([makeDownload(1, { state: 2, done: 3, total: 10 })])
    vi.mocked(downloadApi.restartAll).mockResolvedValue(true)
    vi.mocked(downloadApi.list).mockImplementation(async () => ({
      downloads: [makeDownload(1, { state: 3, done: 10, total: 10 })],
      labels: [],
      total: 1,
    }))
    const listCallsBefore = vi.mocked(downloadApi.list).mock.calls.length

    const button = wrapper.findAll('button').find((b) => b.text().includes('全部下载'))
    expect(button).toBeDefined()
    await button!.trigger('click')
    await flushPromises()
    await flushPromises()
    await nextTick()

    expect(vi.mocked(downloadApi.restartAll)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(downloadApi.list).mock.calls.length).toBeGreaterThan(listCallsBefore)
    const row = wrapper.findAllComponents(DownloadProgressRow)[0]
    expect(row?.props('item').state).toBe(3)
    expect(row?.props('item').done).toBe(10)
  })

  it('单行「开始」被服务端拒绝（false）时不卡乐观 Waiting——还原并以服务端对账', async () => {
    // 线上症状：EH 熔断期间点「开始」→ 服务端即时把行标 FAILED 并返回 false
    // （HTTP 200）；旧 runItemAction 不看返回值 → 乐观 Waiting 永久停留（无任务
    // 无进度帧）。修复：false 还原乐观态 + 拉取单行信息覆盖（含错误文案）。
    await mountWithRows([makeDownload(1, { state: 4, error: 'EH_UNAVAILABLE: 旧错误' })])
    vi.mocked(downloadApi.start).mockResolvedValue(false)
    vi.mocked(downloadApi.getInfo).mockResolvedValue(
      makeDownload(1, { state: 4, error: 'EH_UNAVAILABLE: EH 平台当前不可达' }),
    )

    const row = wrapper.findAllComponents(DownloadProgressRow)[0]
    const startBtn = row!
      .findAll('button')
      .find((b) => b.attributes('aria-label') === 'Start download')
    expect(startBtn).toBeDefined()
    await startBtn!.trigger('click')
    await flushPromises()
    await nextTick()

    expect(vi.mocked(downloadApi.start)).toHaveBeenCalledWith(1)
    expect(vi.mocked(downloadApi.getInfo)).toHaveBeenCalledWith(1)
    expect(row!.props('item').state).toBe(4)
    expect(row!.props('item').error).toContain('EH 平台当前不可达')
  })

  it('buildItemIndex matches Array#find for every key, first hit wins on duplicates (等价断言)', () => {
    // 重复 gid（9001）：Map 首中语义必须与 find 一致（同键只留首条）。
    const items = [makeDownload(1), makeDownload(2), makeDownload(3, { gid: 9001 })]
    const byGid = buildItemIndex(items, (d) => d.gid)
    const byId = buildItemIndex(items, (d) => d.id)

    expect(byGid.size).toBe(2)
    for (const key of [9001, 9002, 9003, 9999]) {
      expect(byGid.get(key)).toBe(items.find((d) => d.gid === key))
    }
    for (const key of [1, 2, 3, 99]) {
      expect(byId.get(key)).toBe(items.find((d) => d.id === key))
    }
    // 空列表安全。
    expect(buildItemIndex([], (d) => d.gid).size).toBe(0)
  })
})
