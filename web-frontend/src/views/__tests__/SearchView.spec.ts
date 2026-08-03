/**
 * SearchView (S5) — Wave-1 1a wiring (task A5):
 * FilterPanel ↔ filter state ↔ search call, chip row add/remove,
 * quick-search POST (QuickSearchDto schema + offline fallback),
 * prefs-capped recent searches, and the `/` / `f` PC shortcuts.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SearchView from '../SearchView.vue'
import SearchBar from '@/components/search/SearchBar.vue'
import FilterPanel from '@/components/search/FilterPanel.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import { galleryApi } from '@/api/gallery'
import { preferencesApi } from '@/api/preferences'
import type { SearchFilters } from '@/api/gallery'
import type { Preferences } from '@/api/preferences'
import { ADVANCE_SEARCH_BITS } from '@/types/components'

const HISTORY_KEY = 'anotherviewer-search-history'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('@/api/gallery', () => ({
  galleryApi: {
    search: vi.fn(),
    getQuickSearches: vi.fn(),
    createQuickSearch: vi.fn(),
    deleteQuickSearch: vi.fn(),
  },
}))

vi.mock('@/api/preferences', () => ({
  preferencesApi: { get: vi.fn(), update: vi.fn() },
}))

function prefsFixture(general: Record<string, unknown> = {}): Preferences {
  return {
    general: {
      theme: 'dark',
      themeAutoSwitch: false,
      launchPage: 'homepage',
      listMode: 'grid',
      showReadProgress: true,
      detailSize: 'medium',
      thumbSize: 'medium',
      historyInfoSize: 2,
      showJpnTitle: false,
      showGalleryPages: false,
      showTagTranslations: false,
      showGalleryComment: false,
      showGalleryRating: false,
      showSiteEvents: false,
      showSiteLimits: false,
      ...general,
    },
    reader: {
      readingDirection: 'ltr',
      pageMode: 'single',
      firstPageCover: false,
      pageScaling: 'fit-width',
      startPosition: 'home',
      autoPlayIntervalSec: 3,
      showProgress: true,
      showPageInterval: true,
      fullscreen: false,
      brightness: 100,
    },
    privacy: { enableAnalytics: false },
  } as unknown as Preferences
}

describe('SearchView — Wave-1 1a search filter wiring (A5)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    pushMock.mockClear()
    vi.mocked(galleryApi.search).mockResolvedValue({ success: true, data: [], total: 0 })
    vi.mocked(galleryApi.getQuickSearches).mockResolvedValue({ success: true, data: [] })
    vi.mocked(galleryApi.createQuickSearch).mockResolvedValue({
      id: 7,
      name: 'x',
      mode: 0,
      category: 0,
      keyword: '',
      advanceSearch: 0,
      minRating: 0,
      pageFrom: 0,
      pageTo: 0,
    })
    vi.mocked(preferencesApi.get).mockResolvedValue(prefsFixture())
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(SearchView)
    await flushPromises()
    return wrapper
  }

  /** Last `galleryApi.search` call's filters argument (5th). */
  function lastFilters(): SearchFilters | undefined {
    const calls = vi.mocked(galleryApi.search).mock.calls
    return calls[calls.length - 1][4]
  }

  function setFilters(filters: SearchFilters) {
    wrapper.findComponent(FilterPanel).vm.$emit('update:filters', filters)
  }

  describe('filter state → search call', () => {
    it('sends a (default) filters object as the 5th search argument on boot', async () => {
      await mountView()
      const call = vi.mocked(galleryApi.search).mock.calls[0]
      expect(call[3]).toBe(25)
      expect(call[4]).toBeDefined()
      expect(call[4]).toMatchObject({
        category: undefined,
        sort: undefined,
        pageMin: undefined,
        pageMax: undefined,
        minRating: undefined,
        searchName: false,
        searchTags: false,
        searchDesc: false,
        searchTorrents: false,
      })
    })

    it('applies FilterPanel edits and sends them with the next search', async () => {
      await mountView()
      setFilters({
        category: 0x4,
        sort: 2,
        pageMin: 5,
        pageMax: 50,
        minRating: 4,
        searchTags: true,
      })
      wrapper.findComponent(FilterPanel).vm.$emit('search')
      await flushPromises()
      expect(lastFilters()).toMatchObject({
        category: 0x4,
        sort: 2,
        pageMin: 5,
        pageMax: 50,
        minRating: 4,
        searchTags: true,
      })
    })

    it('shares state with the legacy SearchLayout panel (scope bits survive)', async () => {
      await mountView()
      // Scope flags map onto the AdvanceSearchTable low bits.
      setFilters({ searchName: true, searchTorrents: true })
      wrapper.findComponent(FilterPanel).vm.$emit('search')
      await flushPromises()
      expect(lastFilters()).toMatchObject({ searchName: true, searchTorrents: true })
    })
  })

  describe('chip row (add / remove / clear)', () => {
    it('derives chips from the active filters and feeds them to the SearchBar', async () => {
      await mountView()
      expect(wrapper.findComponent(SearchBar).props('filterChips')).toEqual([])
      expect(wrapper.findComponent(SearchBar).props('filterActive')).toBe(false)

      setFilters({ minRating: 4, sort: 1, searchTags: true })
      await flushPromises()
      const chips = wrapper.findComponent(SearchBar).props('filterChips') as Array<{ id: string }>
      expect(chips.map((chip) => chip.id).sort()).toEqual(['minRating', 'searchTags', 'sort'])
      expect(wrapper.findComponent(SearchBar).props('filterActive')).toBe(true)
    })

    it('chip × removes only that filter and re-runs the search', async () => {
      await mountView()
      setFilters({ minRating: 4, sort: 2, searchTags: true })
      await flushPromises()
      const before = vi.mocked(galleryApi.search).mock.calls.length

      wrapper.findComponent(SearchBar).vm.$emit('remove-filter-chip', 'minRating')
      await flushPromises()

      expect(vi.mocked(galleryApi.search).mock.calls.length).toBe(before + 1)
      expect(lastFilters()).toMatchObject({ minRating: undefined, sort: 2, searchTags: true })
    })

    it('Clear resets every filter and re-runs the search', async () => {
      await mountView()
      setFilters({ minRating: 4, sort: 2, category: 0x2 })
      await flushPromises()

      wrapper.findComponent(SearchBar).vm.$emit('clear-filter-chips')
      await flushPromises()

      expect(lastFilters()).toMatchObject({
        category: undefined,
        sort: undefined,
        minRating: undefined,
        searchTags: false,
      })
      expect(wrapper.findComponent(SearchBar).props('filterChips')).toEqual([])
    })
  })

  describe('filter panel open/close', () => {
    it('SearchBar filter button toggles the FilterPanel', async () => {
      await mountView()
      expect(wrapper.findComponent(FilterPanel).props('open')).toBe(false)
      wrapper.findComponent(SearchBar).vm.$emit('click-filter')
      await flushPromises()
      expect(wrapper.findComponent(FilterPanel).props('open')).toBe(true)
      wrapper.findComponent(SearchBar).vm.$emit('click-filter')
      await flushPromises()
      expect(wrapper.findComponent(FilterPanel).props('open')).toBe(false)
    })
  })

  describe('save as quick search (QuickSearchDto schema)', () => {
    async function openSaveDialog() {
      wrapper.findComponent(FilterPanel).vm.$emit('save-quick-search')
      await flushPromises()
    }

    it('POSTs the exact DTO payload for the current filter state', async () => {
      await mountView()
      setFilters({ minRating: 4, searchTags: true, pageMin: 5, pageMax: 50, sort: 3 })
      await openSaveDialog()

      await wrapper.find('.dialog input').setValue('Rated tag classics')
      await wrapper.find('.dialog .btn-primary').trigger('click')
      await flushPromises()

      expect(galleryApi.createQuickSearch).toHaveBeenCalledTimes(1)
      expect(galleryApi.createQuickSearch).toHaveBeenCalledWith({
        name: 'Rated tag classics',
        mode: 0,
        category: 0,
        keyword: '',
        advanceSearch: ADVANCE_SEARCH_BITS.STAGS,
        minRating: 4,
        pageFrom: 5,
        pageTo: 50,
        sort: 3, // W3 R4-11: sort persists with the preset
      })
    })

    it('loading a preset restores its persisted sort (W3 R4-11)', async () => {
      const preset = {
        id: 51,
        name: 'sorted preset',
        mode: 0,
        category: 0,
        keyword: 'sorted kw',
        advanceSearch: 0,
        minRating: 0,
        pageFrom: 0,
        pageTo: 0,
        sort: 2,
      }
      localStorage.setItem('anotherviewer-quick-searches', JSON.stringify([preset]))
      await mountView()

      // The preset surfaces as a suggestion; selecting it loads the preset.
      const bar = wrapper.findComponent(SearchBar)
      const suggestions = bar.props('suggestions') as Array<{ text: string }>
      const index = suggestions.findIndex((s) => s.text === 'sorted kw')
      expect(index).toBeGreaterThanOrEqual(0)
      bar.vm.$emit('select-suggestion', suggestions[index], index)
      await flushPromises()

      const calls = vi.mocked(galleryApi.search).mock.calls
      const filters = calls[calls.length - 1][4] as Record<string, unknown> | undefined
      expect(filters?.sort).toBe(2)
    })

    it('loading a legacy preset without sort keeps the default order', async () => {
      const legacy = {
        id: 52,
        name: 'legacy preset',
        mode: 0,
        category: 0,
        keyword: 'legacy kw',
        advanceSearch: 0,
        minRating: 3,
        pageFrom: 0,
        pageTo: 0,
      }
      localStorage.setItem('anotherviewer-quick-searches', JSON.stringify([legacy]))
      await mountView()

      const bar = wrapper.findComponent(SearchBar)
      const suggestions = bar.props('suggestions') as Array<{ text: string }>
      const index = suggestions.findIndex((s) => s.text === 'legacy kw')
      bar.vm.$emit('select-suggestion', suggestions[index], index)
      await flushPromises()

      const calls = vi.mocked(galleryApi.search).mock.calls
      const filters = calls[calls.length - 1][4] as Record<string, unknown> | undefined
      expect(filters?.sort).toBeUndefined()
      expect(filters?.minRating).toBe(3)
    })

    it('falls back to a device-local preset when the POST fails', async () => {
      await mountView()
      vi.mocked(galleryApi.createQuickSearch).mockRejectedValue(new Error('offline'))
      await openSaveDialog()

      await wrapper.find('.dialog input').setValue('Offline preset')
      await wrapper.find('.dialog .btn-primary').trigger('click')
      await flushPromises()

      expect(wrapper.find('.snackbar').text()).toContain('on this device')
      const stored = JSON.parse(localStorage.getItem('anotherviewer-quick-searches') ?? '[]')
      expect(stored).toHaveLength(1)
      expect(stored[0].name).toBe('Offline preset')
      expect(typeof stored[0].id).toBe('number')
    })
  })

  describe('delete quick search — server call (W3 R4-12)', () => {
    const PRESET = {
      id: 61,
      name: 'to delete',
      mode: 0,
      category: 0,
      keyword: 'del kw',
      advanceSearch: 0,
      minRating: 0,
      pageFrom: 0,
      pageTo: 0,
    }

    async function openManageDialog() {
      wrapper.findComponent(FabLayout).vm.$emit(
        'click-secondary',
        { id: 'manage-quick', icon: 'book-open', label: 'Quick searches' },
        0,
      )
      await flushPromises()
    }

    it('sends DELETE to the server and removes the preset locally', async () => {
      vi.mocked(galleryApi.deleteQuickSearch).mockResolvedValue({ success: true })
      localStorage.setItem('anotherviewer-quick-searches', JSON.stringify([PRESET]))
      await mountView()
      await openManageDialog()

      await wrapper.find('.qs-item__delete').trigger('click')
      await flushPromises()

      expect(galleryApi.deleteQuickSearch).toHaveBeenCalledWith(61)
      expect(JSON.parse(localStorage.getItem('anotherviewer-quick-searches') ?? '[]')).toEqual([])
      expect(wrapper.findAll('.qs-item')).toHaveLength(0)
    })

    it('keeps the local removal and warns when the server is unreachable', async () => {
      vi.mocked(galleryApi.deleteQuickSearch).mockRejectedValue(new Error('offline'))
      localStorage.setItem('anotherviewer-quick-searches', JSON.stringify([PRESET]))
      await mountView()
      await openManageDialog()

      await wrapper.find('.qs-item__delete').trigger('click')
      await flushPromises()

      expect(galleryApi.deleteQuickSearch).toHaveBeenCalledWith(61)
      expect(JSON.parse(localStorage.getItem('anotherviewer-quick-searches') ?? '[]')).toEqual([])
      expect(wrapper.find('.snackbar').text()).toContain('on this device only')
    })
  })

  describe('recent searches — device-local, prefs.general.recentSearchMax', () => {
    async function commitSearches(terms: string[]) {
      const bar = wrapper.findComponent(SearchBar)
      for (const term of terms) {
        bar.vm.$emit('search', term)
        await flushPromises()
      }
    }

    function storedHistory(): string[] {
      return JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]')
    }

    it('caps history at prefs.general.recentSearchMax (3)', async () => {
      vi.mocked(preferencesApi.get).mockResolvedValue(prefsFixture({ recentSearchMax: 3 }))
      await mountView()
      await commitSearches(['a', 'b', 'c', 'd', 'e'])
      expect(storedHistory()).toEqual(['e', 'd', 'c'])
    })

    it('records nothing when recentSearchMax is 0 (feature off)', async () => {
      vi.mocked(preferencesApi.get).mockResolvedValue(prefsFixture({ recentSearchMax: 0 }))
      await mountView()
      await commitSearches(['a', 'b'])
      expect(storedHistory()).toEqual([])
    })

    it('defaults to 10 when the preference key is missing', async () => {
      vi.mocked(preferencesApi.get).mockResolvedValue(prefsFixture()) // no recentSearchMax
      await mountView()
      const terms = Array.from({ length: 12 }, (_, i) => `term-${i + 1}`)
      await commitSearches(terms)
      const stored = storedHistory()
      expect(stored).toHaveLength(10)
      expect(stored[0]).toBe('term-12')
      expect(stored[9]).toBe('term-3')
    })
  })

  describe('PC keyboard shortcuts (no misfires)', () => {
    it('"/" enters the search state', async () => {
      await mountView()
      expect(wrapper.findComponent(SearchBar).props('state')).toBe('normal')
      window.dispatchEvent(new KeyboardEvent('keydown', { key: '/' }))
      await flushPromises()
      expect(wrapper.findComponent(SearchBar).props('state')).toBe('search-list')
    })

    it('"f" toggles the filter panel', async () => {
      await mountView()
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'f' }))
      await flushPromises()
      expect(wrapper.findComponent(FilterPanel).props('open')).toBe(true)
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F' }))
      await flushPromises()
      expect(wrapper.findComponent(FilterPanel).props('open')).toBe(false)
    })

    it('does not fire while typing in an input', async () => {
      await mountView()
      const input = document.createElement('input')
      document.body.appendChild(input)
      input.focus()

      input.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true }))
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'f', bubbles: true }))
      await flushPromises()

      expect(wrapper.findComponent(SearchBar).props('state')).toBe('normal')
      expect(wrapper.findComponent(FilterPanel).props('open')).toBe(false)
      input.remove()
    })

    it('does not fire while typing in a textarea or with modifier keys', async () => {
      await mountView()
      const textarea = document.createElement('textarea')
      document.body.appendChild(textarea)
      textarea.focus()
      textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'f', bubbles: true }))
      await flushPromises()
      expect(wrapper.findComponent(FilterPanel).props('open')).toBe(false)

      // Modifier combos are browser/app-level actions, never our shortcuts.
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'f', ctrlKey: true }))
      window.dispatchEvent(new KeyboardEvent('keydown', { key: '/', metaKey: true }))
      await flushPromises()
      expect(wrapper.findComponent(FilterPanel).props('open')).toBe(false)
      expect(wrapper.findComponent(SearchBar).props('state')).toBe('normal')
      textarea.remove()
    })
  })
})
