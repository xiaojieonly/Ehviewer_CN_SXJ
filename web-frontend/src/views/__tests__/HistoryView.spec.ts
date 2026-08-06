import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import HistoryView from '../HistoryView.vue'
import client from '@/api/client'
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

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
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
    vi.mocked(client.get).mockResolvedValue({ data: { history: items } })
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
    expect(pushMock).toHaveBeenCalledWith('/gallery/42')
  })

  /* ---------------- search + filter slots (A5d, 互斥) ---------------- */

  it('loads the unfiltered history without q/regex params', async () => {
    await mountHistory([makeHistoryItem({ gid: 1 })])
    expect(client.get).toHaveBeenCalledWith('/history/list', { params: {} })
  })

  it('activates a filter slot and requests q=pattern&regex=true', async () => {
    vi.mocked(filterSlotsApi.get).mockResolvedValue(FILTER_SLOTS)
    await mountHistory([makeHistoryItem({ gid: 1 })])
    expect(wrapper.findAll('.filter-slot-bar__chip')).toHaveLength(3) // 全部 + 2

    await clickFilterChip('Artist')
    expect(client.get).toHaveBeenLastCalledWith('/history/list', {
      params: { q: 'artist', regex: 'true' },
    })
    // 互斥：选槽位清空搜索词。
    const input = wrapper.find('.search-bar__input').element as HTMLInputElement
    expect(input.value).toBe('')

    // 回到「全部」→ 恢复无过滤加载。
    await clickFilterChip('全部')
    expect(client.get).toHaveBeenLastCalledWith('/history/list', { params: {} })
  })

  it('debounces a typed search to q=word and cancels the active slot', async () => {
    vi.useFakeTimers()
    vi.mocked(filterSlotsApi.get).mockResolvedValue(FILTER_SLOTS)
    await mountHistory([makeHistoryItem({ gid: 1 })])

    await clickFilterChip('Artist')
    await wrapper.find('.search-bar__input').setValue('futa')
    // 防抖 400ms 内不触发请求。
    await vi.advanceTimersByTimeAsync(200)
    expect(client.get).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    // 输入搜索取消槽位 → LIKE 搜索（无 regex 参数）。
    expect(client.get).toHaveBeenLastCalledWith('/history/list', {
      params: { q: 'futa' },
    })
    const artistChip = wrapper
      .findAll('.filter-slot-bar__chip')
      .find((c) => c.text() === 'Artist')!
    expect(artistChip.classes()).not.toContain('filter-slot-bar__chip--active')
    vi.useRealTimers()
  })

  it('clear button restores the unfiltered history', async () => {
    vi.useFakeTimers()
    await mountHistory([makeHistoryItem({ gid: 1 })])
    await wrapper.find('.search-bar__input').setValue('futa')
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(client.get).toHaveBeenLastCalledWith('/history/list', {
      params: { q: 'futa' },
    })

    await wrapper.find('.search-bar__clear').trigger('click')
    await flushPromises()
    expect(client.get).toHaveBeenLastCalledWith('/history/list', { params: {} })
    vi.useRealTimers()
  })
})
