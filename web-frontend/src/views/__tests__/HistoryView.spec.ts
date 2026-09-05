import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import HistoryView from '../HistoryView.vue'
import { historyApi } from '@/api/history'
import type { HistoryItem } from '@/api/history'
import { filterSlotsApi } from '@/api/filterSlots'
import { preferencesApi } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import type { Preferences } from '@/api/preferences'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('@/api/history', () => ({
  historyApi: { listHistory: vi.fn(), clearHistory: vi.fn() },
}))

vi.mock('@/api/filterSlots', () => ({
  filterSlotsApi: { get: vi.fn(), put: vi.fn() },
}))

vi.mock('@/api/preferences', () => ({
  preferencesApi: { get: vi.fn(), update: vi.fn() },
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

/** Fixed "now" would need fake timers; the tests only assert structure. */
const NOW = Date.now()

function makeHistoryItem(overrides: Partial<HistoryItem> = {}): HistoryItem {
  return {
    gid: 1,
    token: 'abc123',
    title: 'Sample Gallery',
    titleJpn: '',
    thumb: '',
    category: 2,
    rating: 4,
    mode: 0,
    time: NOW - 60_000, // one minute ago → "Today …"
    ...overrides,
  }
}

/** Seed the preferences store directly (avoids the async preferences load). */
function seedPrefs(general: Record<string, unknown>): void {
  const store = usePreferencesStore()
  store.prefs = { general } as unknown as Preferences
}

describe('HistoryView (F-UX1 grid meta — title + last-viewed sub line)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    pushMock.mockClear()
    vi.mocked(historyApi.clearHistory).mockResolvedValue({ success: true })
    vi.mocked(filterSlotsApi.get).mockResolvedValue([])
    vi.mocked(preferencesApi.get).mockReturnValue(new Promise(() => {})) // never settles
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  async function mountHistory(items: HistoryItem[]) {
    vi.mocked(historyApi.listHistory).mockResolvedValue({ history: items, total: items.length })
    wrapper = mount(HistoryView)
    await flushPromises()
    await flushPromises()
    return wrapper
  }

  /** Click the filter-slot chip with the given name (「全部」= null). */
  async function clickFilterChip(name: string): Promise<void> {
    const chip = wrapper
      .findAll('.filter-slot-bar__chip')
      .find((c) => c.text() === name)!
    await chip.trigger('click')
    await flushPromises()
    await flushPromises()
  }

  it('renders every grid card with the last-viewed stamp flowing in the card meta', async () => {
    seedPrefs({ listMode: 'grid' })
    const items = Array.from({ length: 5 }, (_, i) =>
      makeHistoryItem({ gid: i + 1, title: `Gallery ${i + 1}` }),
    )
    await mountHistory(items)

    const cells = wrapper.findAll('.gallery-grid__cell')
    expect(cells).toHaveLength(5)

    // All five cards carry the flowing sub line inside the card meta region.
    for (const cell of cells) {
      const meta = cell.find('.gallery-card__grid-meta')
      expect(meta.exists()).toBe(true)
      expect(meta.find('.gallery-card__grid-title').exists()).toBe(true)
      const sub = meta.find('.gallery-card__grid-sub')
      expect(sub.exists()).toBe(true)
      expect(sub.find('.time-row').exists()).toBe(true)
    }
  })

  it('makes the title and the sub line normal-flow siblings (no absolute overlap)', async () => {
    seedPrefs({ listMode: 'grid' })
    await mountHistory([makeHistoryItem({ gid: 7, title: 'Overlap Probe' })])

    const meta = wrapper.find('.gallery-card__grid-meta')
    const title = meta.find('.gallery-card__grid-title')
    const sub = meta.find('.gallery-card__grid-sub')
    expect(title.exists()).toBe(true)
    expect(sub.exists()).toBe(true)
    // DOM structure: the two lines are siblings stacked in normal flow —
    // the sub line stretches the card instead of absolutely overlapping the
    // title. (happy-dom performs no layout, so structure is the assertion.)
    expect(title.element.nextElementSibling).toBe(sub.element)
    expect(title.element.parentElement).toBe(meta.element)
    expect(sub.element.parentElement).toBe(meta.element)
    // The flowing line is NOT the absolutely positioned corner badge.
    expect(sub.find('.time-badge').exists()).toBe(false)
  })

  it('shows the formatted last-viewed stamp in the sub line', async () => {
    seedPrefs({ listMode: 'grid' })
    await mountHistory([makeHistoryItem({ gid: 9, time: NOW - 60_000 })])
    const sub = wrapper.find('.gallery-card__grid-sub')
    // One minute ago formats as "Today HH:MM".
    expect(sub.text()).toMatch(/^Today \d/)
  })

  it('suppresses the corner badge in grid cells (it is list-form only)', async () => {
    seedPrefs({ listMode: 'grid' })
    await mountHistory([makeHistoryItem({ gid: 3 })])
    // The badge still renders (single slot content both forms) but lives
    // under the grid-cell suppression rule; the grid form's visible stamp is
    // the flowing sub line.
    const badge = wrapper.find('.gallery-grid__cell .time-badge')
    expect(badge.exists()).toBe(true)
    expect(badge.classes()).not.toContain('time-row')
  })

  it('keeps the list form on the absolute corner badge without a meta region', async () => {
    seedPrefs({ listMode: 'list' })
    await mountHistory([makeHistoryItem({ gid: 5 }), makeHistoryItem({ gid: 6 })])

    const rows = wrapper.findAll('.gallery-list__row')
    expect(rows).toHaveLength(2)
    expect(wrapper.find('.gallery-grid__cell').exists()).toBe(false)
    // List cards have no grid meta region and no flowing sub line.
    expect(wrapper.find('.gallery-card__grid-meta').exists()).toBe(false)
    expect(wrapper.find('.gallery-card__grid-sub').exists()).toBe(false)
    // The last-viewed badge rides along in its classic corner form.
    expect(wrapper.findAll('.gallery-list__row .time-badge')).toHaveLength(2)
  })

  it('opens the gallery detail on card selection', async () => {
    seedPrefs({ listMode: 'grid' })
    await mountHistory([makeHistoryItem({ gid: 42 })])
    await wrapper.find('.app-card').trigger('click')
    expect(pushMock).toHaveBeenCalledWith({ path: '/gallery/42', query: { token: 'abc123' } })
  })

  it('passes through the local token in the detail route query (P-A)', async () => {
    seedPrefs({ listMode: 'grid' })
    await mountHistory([makeHistoryItem({ gid: 7, token: 'tok7' })])
    await wrapper.find('.app-card').trigger('click')
    expect(pushMock).toHaveBeenCalledWith({ path: '/gallery/7', query: { token: 'tok7' } })
  })

  it('omits the token query when the history row carries none', async () => {
    seedPrefs({ listMode: 'grid' })
    await mountHistory([makeHistoryItem({ gid: 9, token: '' })])
    await wrapper.find('.app-card').trigger('click')
    expect(pushMock).toHaveBeenCalledWith({ path: '/gallery/9', query: {} })
  })

  /* ---------------- W6 阅读进度透传（plan-2026-09-02） ---------------- */

  it('passes the history row page through as readProgress (badge shows N+1P)', async () => {
    // 历史行无页数（pages=0）→ 角标退化为 NP 格式。
    seedPrefs({ listMode: 'list', showReadProgress: true })
    await mountHistory([makeHistoryItem({ gid: 11, page: 5 })])
    const badge = wrapper.find('[data-testid="read-progress-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('6P')
  })

  it('shows the badge on grid cells too', async () => {
    seedPrefs({ listMode: 'grid', showReadProgress: true })
    await mountHistory([makeHistoryItem({ gid: 14, page: 2 })])
    const badge = wrapper.find('[data-testid="read-progress-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('3P')
  })

  it('hides the read-progress badge when the row carries no page (legacy server)', async () => {
    seedPrefs({ listMode: 'list', showReadProgress: true })
    await mountHistory([makeHistoryItem({ gid: 12 })])
    expect(wrapper.find('[data-testid="read-progress-badge"]').exists()).toBe(false)
  })

  it('hides the read-progress badge when showReadProgress is off', async () => {
    seedPrefs({ listMode: 'list', showReadProgress: false })
    await mountHistory([makeHistoryItem({ gid: 13, page: 5 })])
    expect(wrapper.find('[data-testid="read-progress-badge"]').exists()).toBe(false)
  })

  /* ---------------- search + filter slots (A5d, 互斥) ---------------- */

  it('loads the unfiltered history without q/regex params', async () => {
    await mountHistory([makeHistoryItem({ gid: 1 })])
    expect(historyApi.listHistory).toHaveBeenCalledWith(null, undefined, 0, 50)
  })

  it('activates a filter slot and requests q=pattern&regex=true', async () => {
    vi.mocked(filterSlotsApi.get).mockResolvedValue(FILTER_SLOTS)
    await mountHistory([makeHistoryItem({ gid: 1 })])
    expect(wrapper.findAll('.filter-slot-bar__chip')).toHaveLength(3) // 全部 + 2

    await clickFilterChip('Artist')
    expect(historyApi.listHistory).toHaveBeenLastCalledWith('artist', true, 0, 50)
    // 互斥：选槽位清空搜索词。
    const input = wrapper.find('.search-bar__input').element as HTMLInputElement
    expect(input.value).toBe('')

    // 回到「全部」→ 恢复无过滤加载（分页重置回 page=0）。
    await clickFilterChip('全部')
    expect(historyApi.listHistory).toHaveBeenLastCalledWith(null, undefined, 0, 50)
  })

  it('debounces a typed search to q=word and cancels the active slot', async () => {
    vi.useFakeTimers()
    vi.mocked(filterSlotsApi.get).mockResolvedValue(FILTER_SLOTS)
    await mountHistory([makeHistoryItem({ gid: 1 })])

    await clickFilterChip('Artist')
    await wrapper.find('.search-bar__input').setValue('futa')
    // 防抖 400ms 内不触发请求。
    await vi.advanceTimersByTimeAsync(200)
    expect(historyApi.listHistory).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    // 输入搜索取消槽位 → LIKE 搜索（无 regex 参数）。
    expect(historyApi.listHistory).toHaveBeenLastCalledWith('futa', undefined, 0, 50)
    const artistChip = wrapper
      .findAll('.filter-slot-bar__chip')
      .find((c) => c.text() === 'Artist')!
    expect(artistChip.classes()).not.toContain('filter-slot-bar__chip--active')
    vi.useRealTimers()
  })

  it('holds the debounced search while the IME composition is active (C1)', async () => {
    vi.useFakeTimers()
    await mountHistory([makeHistoryItem({ gid: 1 })])
    const input = wrapper.find('.search-bar__input')

    // 拼音组合期间的中间态输入不触发请求（首屏 1 次）。
    await input.trigger('compositionstart')
    await input.setValue('futa')
    await vi.advanceTimersByTimeAsync(1000)
    expect(historyApi.listHistory).toHaveBeenCalledTimes(1)

    // compositionend 后的最终选词照常防抖提交。
    await input.trigger('compositionend')
    await input.setValue('漫画')
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(historyApi.listHistory).toHaveBeenLastCalledWith('漫画', undefined, 0, 50)
    vi.useRealTimers()
  })

  it('clear button restores the unfiltered history', async () => {
    vi.useFakeTimers()
    await mountHistory([makeHistoryItem({ gid: 1 })])
    await wrapper.find('.search-bar__input').setValue('futa')
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(historyApi.listHistory).toHaveBeenLastCalledWith('futa', undefined, 0, 50)

    await wrapper.find('.search-bar__clear').trigger('click')
    await flushPromises()
    expect(historyApi.listHistory).toHaveBeenLastCalledWith(null, undefined, 0, 50)
    vi.useRealTimers()
  })

  /* ---------------- 服务端分页（W2-DL F3） ---------------- */

  /** 按服务端分页语义切片的 mock：page 0 起始，pageSize 每页条数，返回 total 全集数。 */
  function pagedServer(totalRows: number) {
    return async (_q: unknown, _r: unknown, page = 0, pageSize = 50): Promise<{ history: HistoryItem[]; total: number }> => {
      const start = page * pageSize
      const count = Math.max(0, Math.min(pageSize, totalRows - start))
      return {
        history: Array.from({ length: count }, (_, i) =>
          makeHistoryItem({ gid: start + i + 1, title: `H ${start + i + 1}` }),
        ),
        total: totalRows,
      }
    }
  }

  it('first screen loads page=0 only (server-side pagination, no full fetch)', async () => {
    seedPrefs({ listMode: 'grid' })
    vi.mocked(historyApi.listHistory).mockImplementation(pagedServer(120))
    wrapper = mount(HistoryView)
    await flushPromises()
    await flushPromises()

    // 首屏 offset=0：page=0&pageSize=50，只渲染第一页 50 行。
    expect(historyApi.listHistory).toHaveBeenCalledTimes(1)
    expect(historyApi.listHistory).toHaveBeenLastCalledWith(null, undefined, 0, 50)
    expect(wrapper.findAll('.gallery-grid__cell')).toHaveLength(50)
    expect(wrapper.text()).toContain('H 1')
    // 标题计数显示 已加载/全集。
    expect(wrapper.find('.history-view__count').text()).toBe('50 / 120 galleries')
  })

  it('appends the next page on scroll-to-bottom until total is reached', async () => {
    seedPrefs({ listMode: 'grid' })
    vi.mocked(historyApi.listHistory).mockImplementation(pagedServer(120))
    wrapper = mount(HistoryView)
    await flushPromises()
    await flushPromises()
    expect(wrapper.findAll('.gallery-grid__cell')).toHaveLength(50)

    // 滚到底 → 追加 page=1（第 51–100 条）。
    const el = wrapper.find('.fast-scroller__container').element as HTMLElement
    Object.defineProperty(el, 'scrollTop', { value: 1000, configurable: true })
    Object.defineProperty(el, 'clientHeight', { value: 800, configurable: true })
    el.dispatchEvent(new Event('scroll'))
    await flushPromises()
    await flushPromises()

    expect(historyApi.listHistory).toHaveBeenLastCalledWith(null, undefined, 1, 50)
    expect(wrapper.findAll('.gallery-grid__cell')).toHaveLength(100)

    // 继续滚 → 追加最后一页（只剩 20 条）。
    el.dispatchEvent(new Event('scroll'))
    await flushPromises()
    await flushPromises()
    expect(historyApi.listHistory).toHaveBeenLastCalledWith(null, undefined, 2, 50)
    expect(wrapper.findAll('.gallery-grid__cell')).toHaveLength(120)
    expect(wrapper.text()).toContain('H 120')

    // 达到 total 后继续滚动不再请求。
    el.dispatchEvent(new Event('scroll'))
    await flushPromises()
    expect(historyApi.listHistory).toHaveBeenCalledTimes(3)
    // 计数回到纯总数形态。
    expect(wrapper.find('.history-view__count').text()).toBe('120 galleries')
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

  it('names REGEX_INVALID when the server rejects the filter pattern (F4)', async () => {
    vi.mocked(historyApi.listHistory).mockRejectedValue(apiError('REGEX_INVALID'))
    wrapper = mount(HistoryView)
    await flushPromises()
    await flushPromises()

    // 首屏失败（无内容）→ 错误态 + 专属文案（不再是泛化的 Failed to load）。
    expect(wrapper.find('[data-testid="content-state-error"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('正则无效')
    // Toast teleports to body — assert on document.
    expect(document.querySelector('.toast')?.textContent).toContain('正则无效')
  })

  it('keeps the generic error tip for non-REGEX failures (F4)', async () => {
    vi.mocked(historyApi.listHistory).mockRejectedValue(new Error('boom'))
    wrapper = mount(HistoryView)
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('[data-testid="content-state-error"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('正则无效')
    expect(document.querySelector('.toast')).toBeNull()
  })

  /* ---------------- F6 清历史失败不再静默 --------------- */

  it('closes the dialog and shows an error toast when clearing fails (F6)', async () => {
    vi.mocked(historyApi.clearHistory).mockRejectedValue(new Error('boom'))
    await mountHistory([makeHistoryItem({ gid: 1 })])

    // FAB「Clear history」→ 确认对话框打开。
    await wrapper
      .findAll('.fab--mini')
      .find((b) => b.attributes('aria-label') === 'Clear history')!
      .trigger('click')
    await flushPromises()
    const scrim = document.querySelector('.dialog-scrim')
    expect(scrim).not.toBeNull()

    // 点击 Clear → 失败：对话框关闭（对齐成功路径）+ 错误 toast。
    const clearBtn = new DOMWrapper(scrim!.querySelector<HTMLButtonElement>('.dialog__btn--danger')!)
    await clearBtn.trigger('click')
    await flushPromises()

    expect(document.querySelector('.dialog-scrim')).toBeNull()
    expect(document.querySelector('.toast')?.textContent).toContain('清除历史失败')
    // 列表数据保持原样（未误清）。
    expect(wrapper.text()).toContain('Sample Gallery')
  })

  it('clears the list and closes the dialog when clearing succeeds (F6 对照)', async () => {
    await mountHistory([makeHistoryItem({ gid: 1 })])
    await wrapper
      .findAll('.fab--mini')
      .find((b) => b.attributes('aria-label') === 'Clear history')!
      .trigger('click')
    await flushPromises()

    const clearBtn = new DOMWrapper(
      document.querySelector('.dialog-scrim')!.querySelector<HTMLButtonElement>('.dialog__btn--danger')!,
    )
    await clearBtn.trigger('click')
    await flushPromises()

    expect(document.querySelector('.dialog-scrim')).toBeNull()
    expect(wrapper.find('[data-testid="content-state-empty"]').exists()).toBe(true)
    expect(document.querySelector('.toast')).toBeNull()
  })
})
