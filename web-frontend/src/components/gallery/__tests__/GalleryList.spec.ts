import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import GalleryList, { resolveListMode, type ListMode } from '../GalleryList.vue'
import GalleryGrid from '../GalleryGrid.vue'
import GalleryCard from '../GalleryCard.vue'
import { preferencesApi } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import type { Preferences } from '@/api/preferences'
import type { GalleryInfo } from '@/types'

vi.mock('@/api/preferences', () => ({
  preferencesApi: { get: vi.fn(), update: vi.fn() },
}))

/** Build a GalleryInfo fixture; `overrides` patches individual fields. */
function makeGallery(overrides: Partial<GalleryInfo> = {}): GalleryInfo {
  return {
    gid: 1,
    token: 'abc123',
    title: 'Sample Gallery',
    titleJpn: '',
    thumb: '',
    category: 2,
    posted: '2026-01-01',
    uploader: 'someone',
    rating: 4,
    rated: false,
    simpleLanguage: '',
    simpleTags: [],
    thumbWidth: 0,
    thumbHeight: 0,
    pages: 10,
    favoriteSlot: -1,
    favoriteName: '',
    ...overrides,
  }
}

/** Seed the store with a partial `general` section (schema keys optional). */
function seedPrefs(general: Record<string, unknown>): void {
  const store = usePreferencesStore()
  store.prefs = { general } as unknown as Preferences
}

function mountList(
  items: GalleryInfo[],
  slots: Record<string, string> = {},
) {
  return mount(GalleryList, { props: { items }, slots })
}

describe('resolveListMode', () => {
  it('passes through valid modes', () => {
    expect(resolveListMode('list')).toBe('list')
    expect(resolveListMode('grid')).toBe('grid')
  })

  it('falls back to grid for missing/unknown/stale values', () => {
    expect(resolveListMode(undefined)).toBe('grid')
    expect(resolveListMode(null)).toBe('grid')
    expect(resolveListMode('')).toBe('grid')
    expect(resolveListMode('table')).toBe('grid')
    expect(resolveListMode(3)).toBe('grid')
  })
})

describe('GalleryList (B-1 listMode consumption)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(preferencesApi.get).mockReset()
    vi.mocked(preferencesApi.update).mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders the grid form via GalleryGrid when listMode is grid', () => {
    seedPrefs({ listMode: 'grid' })
    const wrapper = mountList([makeGallery(), makeGallery({ gid: 2 })])
    expect(wrapper.findComponent(GalleryGrid).exists()).toBe(true)
    expect(wrapper.find('.gallery-list__rows').exists()).toBe(false)
  })

  it('renders list-form rows when listMode is list', () => {
    seedPrefs({ listMode: 'list' })
    const wrapper = mountList([makeGallery(), makeGallery({ gid: 2 }), makeGallery({ gid: 3 })])
    expect(wrapper.findComponent(GalleryGrid).exists()).toBe(false)
    expect(wrapper.findAll('.gallery-list__row')).toHaveLength(3)
    const cards = wrapper.findAllComponents(GalleryCard)
    expect(cards).toHaveLength(3)
    expect(cards[0].props('mode')).toBe('list')
  })

  it('falls back to the grid form while prefs are unloaded', () => {
    // No store load triggered beforehand — simulates a missing listMode key.
    vi.mocked(preferencesApi.get).mockReturnValue(new Promise(() => {})) // never settles
    const wrapper = mountList([makeGallery()])
    expect(wrapper.findComponent(GalleryGrid).exists()).toBe(true)
    expect(wrapper.find('.gallery-list__rows').exists()).toBe(false)
  })

  it('loads preferences once on mount when unloaded', async () => {
    vi.mocked(preferencesApi.get).mockResolvedValue({
      general: { listMode: 'list' },
    } as unknown as Preferences)
    const wrapper = mountList([makeGallery()])
    expect(preferencesApi.get).toHaveBeenCalledTimes(1)
    await flushPromises()
    // The freshly loaded listMode takes effect.
    expect(wrapper.findAll('.gallery-list__row')).toHaveLength(1)
  })

  it('does not re-load preferences that are already present', () => {
    seedPrefs({ listMode: 'grid' })
    mountList([makeGallery()])
    expect(preferencesApi.get).not.toHaveBeenCalled()
  })

  it('treats an unknown stored listMode as grid', () => {
    seedPrefs({ listMode: 'compact' })
    const wrapper = mountList([makeGallery()])
    expect(wrapper.findComponent(GalleryGrid).exists()).toBe(true)
  })
})

describe('GalleryList (toggle control + persistence)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(preferencesApi.get).mockReset()
    vi.mocked(preferencesApi.update).mockReset().mockResolvedValue({} as Preferences)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function listButton(wrapper: ReturnType<typeof mountList>) {
    return wrapper.find('button[aria-label="列表视图"]')
  }
  function gridButton(wrapper: ReturnType<typeof mountList>) {
    return wrapper.find('button[aria-label="网格视图"]')
  }

  it('renders a toggle button per mode with the active one pressed', () => {
    seedPrefs({ listMode: 'grid' })
    const wrapper = mountList([makeGallery()])
    expect(gridButton(wrapper).attributes('aria-pressed')).toBe('true')
    expect(listButton(wrapper).attributes('aria-pressed')).toBe('false')
  })

  it('switching to list updates the store and re-renders rows', async () => {
    seedPrefs({ listMode: 'grid' })
    const wrapper = mountList([makeGallery(), makeGallery({ gid: 2 })])

    await listButton(wrapper).trigger('click')

    const store = usePreferencesStore()
    expect(store.prefs?.general.listMode).toBe('list')
    expect(wrapper.findAll('.gallery-list__row')).toHaveLength(2)
    expect(wrapper.findComponent(GalleryGrid).exists()).toBe(false)
    expect(gridButton(wrapper).attributes('aria-pressed')).toBe('false')
    expect(listButton(wrapper).attributes('aria-pressed')).toBe('true')
  })

  it('persists the switch through the preferences API (debounced PUT)', async () => {
    vi.useFakeTimers()
    seedPrefs({ listMode: 'grid' })
    const wrapper = mountList([makeGallery()])

    await listButton(wrapper).trigger('click')
    expect(preferencesApi.update).not.toHaveBeenCalled() // debounced

    await vi.advanceTimersByTimeAsync(600)
    expect(preferencesApi.update).toHaveBeenCalledTimes(1)
    expect(preferencesApi.update).toHaveBeenCalledWith({
      general: expect.objectContaining({ listMode: 'list' }),
    })
  })

  it('does not write when the active mode is clicked again', async () => {
    vi.useFakeTimers()
    seedPrefs({ listMode: 'grid' })
    const wrapper = mountList([makeGallery()])

    await gridButton(wrapper).trigger('click')
    await vi.advanceTimersByTimeAsync(600)

    expect(preferencesApi.update).not.toHaveBeenCalled()
    expect(usePreferencesStore().prefs?.general.listMode).toBe('grid')
  })

  it('round-trips grid → list → grid', async () => {
    seedPrefs({ listMode: 'grid' })
    const wrapper = mountList([makeGallery()])

    await listButton(wrapper).trigger('click')
    expect(wrapper.find('.gallery-list__rows').exists()).toBe(true)
    await gridButton(wrapper).trigger('click')
    expect(wrapper.findComponent(GalleryGrid).exists()).toBe(true)
    expect(usePreferencesStore().prefs?.general.listMode).toBe('grid')
  })
})

describe('GalleryList (item-extra slot + selection)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders item-extra inside list rows', () => {
    seedPrefs({ listMode: 'list' })
    const wrapper = mountList([makeGallery({ gid: 9 })], {
      'item-extra': '<span class="test-badge">badge</span>',
    })
    const badge = wrapper.find('.gallery-list__row .test-badge')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('badge')
  })

  it('renders item-extra in grid cells too', () => {
    seedPrefs({ listMode: 'grid' })
    const wrapper = mountList([makeGallery({ gid: 9 })], {
      'item-extra': '<span class="test-badge">badge</span>',
    })
    expect(wrapper.find('.gallery-grid__cell .test-badge').exists()).toBe(true)
  })

  it('emits select with the gallery when a row card is clicked', async () => {
    seedPrefs({ listMode: 'list' })
    const gallery = makeGallery({ gid: 5 })
    const wrapper = mountList([gallery])
    await wrapper.findComponent(GalleryCard).trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')![0][0]).toEqual(gallery)
  })

  it('emits select with the gallery when a grid card is clicked', async () => {
    seedPrefs({ listMode: 'grid' })
    const gallery = makeGallery({ gid: 6 })
    const wrapper = mountList([gallery])
    await wrapper.findComponent(GalleryCard).trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')![0][0]).toEqual(gallery)
  })

  it('staggers list-row entrances via inline animationDelay (T-1 style)', () => {
    seedPrefs({ listMode: 'list' })
    const items = Array.from({ length: 15 }, (_, i) => makeGallery({ gid: i + 1 }))
    const wrapper = mountList(items)
    const rows = wrapper.findAll('.gallery-list__row')
    expect(rows[0].attributes('style')).toContain('animation-delay: 0ms')
    expect(rows[1].attributes('style')).toContain('animation-delay: 24ms')
    // Capped at 240ms so late rows don't wait.
    expect(rows[14].attributes('style')).toContain('animation-delay: 240ms')
  })

  it('exposes mode/setMode/toggleMode for programmatic switching', async () => {
    seedPrefs({ listMode: 'grid' })
    const wrapper = mountList([makeGallery()])
    const vm = wrapper.vm as unknown as {
      mode: ListMode
      setMode: (m: ListMode) => void
      toggleMode: () => void
    }
    expect(vm.mode).toBe('grid')
    vm.toggleMode()
    await flushPromises()
    expect(usePreferencesStore().prefs?.general.listMode).toBe('list')
    expect(vm.mode).toBe('list')
    vm.setMode('grid')
    await flushPromises()
    expect(usePreferencesStore().prefs?.general.listMode).toBe('grid')
  })
})
