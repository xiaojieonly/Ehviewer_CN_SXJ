import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import HistoryView from '../HistoryView.vue'
import { historyApi } from '@/api/history'
import type { HistoryItem } from '@/api/history'
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

vi.mock('@/api/preferences', () => ({
  preferencesApi: { get: vi.fn(), update: vi.fn() },
}))

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
    vi.mocked(preferencesApi.get).mockReturnValue(new Promise(() => {})) // never settles
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  async function mountHistory(items: HistoryItem[]) {
    vi.mocked(historyApi.listHistory).mockResolvedValue({ history: items })
    wrapper = mount(HistoryView)
    await flushPromises()
    return wrapper
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
})
