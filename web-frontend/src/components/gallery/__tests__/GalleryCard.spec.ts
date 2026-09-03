import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import GalleryCard, {
  DEFAULT_FAVORITE_SLOT_NAMES,
  parseFavoriteSlotNames,
} from '../GalleryCard.vue'
import AppCard from '@/components/atoms/AppCard.vue'
import RatingStars from '@/components/atoms/RatingStars.vue'
import CategoryChip from '@/components/atoms/CategoryChip.vue'
import CategoryTriangle from '@/components/atoms/CategoryTriangle.vue'
import { usePreferencesStore } from '@/stores/preferences'
import { favoriteApi } from '@/api/favorite'
import { downloadApi } from '@/api/download'
import { PRIVACY_PLACEHOLDER_SRC, setPrivacyMaskEnabled } from '@/utils/privacyMask'
import type { Preferences } from '@/api/preferences'
import type { GalleryInfo } from '@/types/components'

/* F-UX6 action wiring — the card calls the same APIs the detail/download
   views use; the router is stubbed to a push spy. */
const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('@/api/favorite', () => ({
  favoriteApi: { addFavorite: vi.fn(), removeFavorite: vi.fn() },
}))

vi.mock('@/api/download', () => ({
  downloadApi: { add: vi.fn() },
}))

/** Build a GalleryInfo fixture; `overrides` patches individual fields. */
function makeGallery(overrides: Partial<GalleryInfo> = {}): GalleryInfo {
  return {
    gid: 12345,
    token: 'abc123',
    title: '(C99) Test Gallery Title [English]',
    titleJpn: 'テストギャラリー',
    thumb: 'https://example.com/thumb.jpg',
    category: 0x2, // doujinshi bit
    posted: '2024-01-01 12:00',
    uploader: 'uploader123',
    rating: 4.5,
    rated: false,
    simpleLanguage: 'EN',
    simpleTags: ['tag1', 'tag2'],
    thumbWidth: 300,
    thumbHeight: 400,
    pages: 24,
    favoriteSlot: -1,
    favoriteName: '',
    ...overrides,
  }
}

/**
 * Seeds the preferences store with a partial `general` section (the card must
 * tolerate a store without the parallel-work schema keys, so everything here
 * is optional).
 */
function seedPrefs(general: Record<string, unknown>): void {
  const store = usePreferencesStore()
  store.prefs = { general } as unknown as Preferences
}

beforeEach(() => {
  setActivePinia(createPinia())
  // 打码是模块级状态，会跨用例泄漏——统一复位。
  setPrivacyMaskEnabled(false)
})

describe('GalleryCard (list mode)', () => {
  it('renders title, japanese title, rating, chip, pages and tags', () => {
    const gallery = makeGallery()
    const wrapper = mount(GalleryCard, { props: { gallery, mode: 'list' } })

    expect(wrapper.find('.gallery-card__title').text()).toBe(gallery.title)
    expect(wrapper.find('.gallery-card__title-jpn').text()).toBe('テストギャラリー')
    expect(wrapper.findComponent(RatingStars).exists()).toBe(true)
    expect(wrapper.findComponent(CategoryChip).exists()).toBe(true)
    expect(wrapper.find('.category-chip').text()).toBe('Doujinshi')
    expect(wrapper.find('.gallery-card__pages').text()).toBe('24P')
    expect(wrapper.find('.gallery-card__tag').text()).toBe('tag1')
  })

  it('renders the thumbnail at the fixed 80×120 size', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    const img = wrapper.find('.gallery-card__thumb img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://example.com/thumb.jpg')
  })

  it('omits japanese title and posted when absent', () => {
    const wrapper = mount(GalleryCard, {
      props: {
        gallery: makeGallery({ titleJpn: '', posted: '' }),
        mode: 'list',
      },
    })
    expect(wrapper.find('.gallery-card__title-jpn').exists()).toBe(false)
    expect(wrapper.find('.gallery-card__posted').exists()).toBe(false)
  })

  it('does not render grid-only elements in list mode', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    expect(wrapper.findComponent(CategoryTriangle).exists()).toBe(false)
    expect(wrapper.find('.gallery-card__lang').exists()).toBe(false)
    expect(wrapper.find('.gallery-card__grid-title').exists()).toBe(false)
  })

  it('隐私打码：标题与日文标题隐藏、序列号替代，缩略图换占位图', () => {
    setPrivacyMaskEnabled(true)
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__title').text()).toBe('#12345')
    expect(wrapper.find('.gallery-card__title-jpn').exists()).toBe(false)
    expect(wrapper.find('.gallery-card__thumb img').attributes('src')).toBe(
      PRIVACY_PLACEHOLDER_SRC,
    )
  })
})

describe('GalleryCard (grid mode)', () => {
  it('renders the image tile, corner triangle, language badge and grid title', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'grid' },
    })
    expect(wrapper.find('.gallery-card__tile img').attributes('src')).toBe(
      'https://example.com/thumb.jpg',
    )
    expect(wrapper.findComponent(CategoryTriangle).exists()).toBe(true)
    expect(wrapper.find('.gallery-card__lang').text()).toBe('EN')
    expect(wrapper.find('.gallery-card__grid-title').text()).toBe('(C99) Test Gallery Title [English]')
  })

  it('hides the language badge when simpleLanguage is empty', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ simpleLanguage: '' }), mode: 'grid' },
    })
    expect(wrapper.find('.gallery-card__lang').exists()).toBe(false)
  })

  it('derives the tile aspect ratio from thumb dimensions with clamping', () => {
    // 300/400 = 0.75, inside the [0.333, 1.333] clamp.
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ thumbWidth: 300, thumbHeight: 400 }), mode: 'grid' },
    })
    expect(wrapper.find('.gallery-card__tile').attributes('style')).toContain('aspect-ratio')
    expect(wrapper.find('.gallery-card__tile').attributes('style')).toContain('0.75')
  })

  it('clamps extreme thumb aspects into [0.333, 1.333]', () => {
    const wide = mount(GalleryCard, {
      props: { gallery: makeGallery({ thumbWidth: 4000, thumbHeight: 100 }), mode: 'grid' },
    })
    // 40 → clamped to 1.333.
    expect(wide.find('.gallery-card__tile').attributes('style')).toContain('1.333')

    const tall = mount(GalleryCard, {
      props: { gallery: makeGallery({ thumbWidth: 100, thumbHeight: 4000 }), mode: 'grid' },
    })
    // 0.025 → clamped to 0.333.
    expect(tall.find('.gallery-card__tile').attributes('style')).toContain('0.333')
  })

  it('falls back to a 2:3 aspect when thumb dimensions are unknown', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ thumbWidth: 0, thumbHeight: 0 }), mode: 'grid' },
    })
    expect(wrapper.find('.gallery-card__tile').attributes('style')).toContain('aspect-ratio: 2 / 3')
  })

  describe('grid meta region (F-UX1 flowing title + sub line)', () => {
    it('wraps the title in the flowing meta region', () => {
      const wrapper = mount(GalleryCard, {
        props: { gallery: makeGallery(), mode: 'grid' },
      })
      const meta = wrapper.find('.gallery-card__grid-meta')
      expect(meta.exists()).toBe(true)
      expect(meta.find('.gallery-card__grid-title').exists()).toBe(true)
    })

    it('renders no sub line when the grid-sub slot is absent (home-style cards)', () => {
      const wrapper = mount(GalleryCard, {
        props: { gallery: makeGallery(), mode: 'grid' },
      })
      expect(wrapper.find('.gallery-card__grid-sub').exists()).toBe(false)
    })

    it('renders the grid-sub slot as a flowing sibling of the title', () => {
      const wrapper = mount(GalleryCard, {
        props: { gallery: makeGallery(), mode: 'grid' },
        slots: { 'grid-sub': '<span class="sub-marker">Today 14:32</span>' },
      })
      const meta = wrapper.find('.gallery-card__grid-meta')
      const title = meta.find('.gallery-card__grid-title')
      const sub = meta.find('.gallery-card__grid-sub')
      expect(sub.exists()).toBe(true)
      expect(sub.text()).toBe('Today 14:32')
      // Two lines are normal-flow siblings inside the meta region — no
      // absolute positioning can overlap them.
      expect(title.element.nextElementSibling).toBe(sub.element)
      expect(sub.element.parentElement).toBe(meta.element)
    })

    it('does not render the grid meta region in list mode', () => {
      const wrapper = mount(GalleryCard, {
        props: { gallery: makeGallery(), mode: 'list' },
      })
      expect(wrapper.find('.gallery-card__grid-meta').exists()).toBe(false)
    })
  })
})

describe('GalleryCard (surface + category + events)', () => {
  it('builds on the AppCard surface', () => {
    const wrapper = mount(GalleryCard, { props: { gallery: makeGallery(), mode: 'list' } })
    expect(wrapper.findComponent(AppCard).exists()).toBe(true)
    expect(wrapper.find('.app-card').exists()).toBe(true)
    expect(wrapper.find('.app-card--list').exists()).toBe(true)
  })

  it('converts the numeric category bit to a chip/triangle', () => {
    // 0x8 = artist_cg
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ category: 0x8 }), mode: 'list' },
    })
    expect(wrapper.find('.category-chip').text()).toBe('Artist CG')
  })

  it('omits chip and triangle for an unknown category bit', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ category: 0x400 }), mode: 'grid' },
    })
    expect(wrapper.findComponent(CategoryChip).exists()).toBe(false)
    expect(wrapper.findComponent(CategoryTriangle).exists()).toBe(false)
  })

  it('emits click with the gallery on mouse click', async () => {
    const gallery = makeGallery()
    const wrapper = mount(GalleryCard, { props: { gallery, mode: 'list' } })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toBeTruthy()
    expect(wrapper.emitted('click')![0][0]).toEqual(gallery)
  })

  it('emits click with the gallery on keyboard activation (enter + space)', async () => {
    const gallery = makeGallery()
    const wrapper = mount(GalleryCard, { props: { gallery, mode: 'grid' } })
    await wrapper.trigger('keydown.enter')
    await wrapper.trigger('keydown.space')
    const emitted = wrapper.emitted('click')
    expect(emitted).toHaveLength(2)
    expect(emitted![0][0]).toEqual(gallery)
    expect(emitted![1][0]).toEqual(gallery)
  })

  it('renders the overlay slot inside the grid tile', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'grid' },
      slots: { overlay: '<span class="overlay-marker">progress</span>' },
    })
    const overlay = wrapper.find('.overlay-marker')
    expect(overlay.exists()).toBe(true)
    expect(overlay.element.parentElement?.classList.contains('gallery-card__tile')).toBe(true)
  })

  it('keeps the thumbnail hidden until the image loads', async () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    const img = wrapper.find('.gallery-card__img')
    expect(img.classes()).not.toContain('is-loaded')
    await img.trigger('load')
    expect(wrapper.find('.gallery-card__img').classes()).toContain('is-loaded')
  })
})

describe('GalleryCard (missing / failed thumbnail, E2E-9 + E2E-3)', () => {
  it('renders title, pages, rating and chip with thumb null (list mode)', () => {
    const gallery = makeGallery({ thumb: '' })
    const wrapper = mount(GalleryCard, { props: { gallery, mode: 'list' } })
    expect(wrapper.find('.gallery-card__title').text()).toBe(gallery.title)
    expect(wrapper.find('.gallery-card__pages').text()).toBe('24P')
    expect(wrapper.findComponent(RatingStars).exists()).toBe(true)
    expect(wrapper.findComponent(CategoryChip).exists()).toBe(true)
  })

  it('renders an icon placeholder without alt text when thumb is null', () => {
    for (const thumb of ['', null as unknown as string]) {
      const wrapper = mount(GalleryCard, {
        props: { gallery: makeGallery({ thumb }), mode: 'list' },
      })
      expect(wrapper.find('.gallery-card__thumb img').exists()).toBe(false)
      const placeholder = wrapper.find('.gallery-card__thumb-placeholder')
      expect(placeholder.exists()).toBe(true)
      expect(placeholder.text()).toBe('')
      expect(placeholder.find('svg').exists()).toBe(true)
    }
  })

  it('keeps the card visible after the thumbnail fails to load', async () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    const img = wrapper.find('.gallery-card__img')
    await img.trigger('error')
    expect(wrapper.find('.gallery-card__img').exists()).toBe(false)
    expect(wrapper.find('.gallery-card__thumb-placeholder').exists()).toBe(true)
    expect(wrapper.find('.gallery-card__title').text()).toBe(
      '(C99) Test Gallery Title [English]',
    )
    expect(wrapper.find('.gallery-card__pages').text()).toBe('24P')
  })

  it('renders the grid tile placeholder when thumb is null', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ thumb: '' }), mode: 'grid' },
    })
    expect(wrapper.find('.gallery-card__tile img').exists()).toBe(false)
    const placeholder = wrapper.find('.gallery-card__tile-placeholder')
    expect(placeholder.exists()).toBe(true)
    expect(placeholder.text()).toBe('')
    expect(wrapper.find('.gallery-card__grid-title').text()).toBe(
      '(C99) Test Gallery Title [English]',
    )
  })
})

describe('GalleryCard (B-2 info switches: uploader / posted)', () => {
  it('hides uploader and posted by default (prefs unloaded)', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__uploader').exists()).toBe(false)
    expect(wrapper.find('.gallery-card__posted').exists()).toBe(false)
  })

  it('hides uploader and posted when the prefs keys are missing', () => {
    seedPrefs({ listMode: 'list' }) // general section without the show* keys
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__uploader').exists()).toBe(false)
    expect(wrapper.find('.gallery-card__posted').exists()).toBe(false)
  })

  it('shows the uploader row only when general.showUploader is true', () => {
    seedPrefs({ showUploader: true })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ uploader: 'uploader123' }), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__uploader').text()).toBe('uploader123')
  })

  it('omits the uploader row when the gallery has no uploader', () => {
    seedPrefs({ showUploader: true })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ uploader: '' }), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__uploader').exists()).toBe(false)
  })

  it('shows the posted stamp only when general.showPostedTime is true', () => {
    seedPrefs({ showPostedTime: true })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ posted: '2024-01-01 12:00' }), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__posted').text()).toBe('2024-01-01 12:00')
  })

  it('keeps posted hidden while showing uploader when only showUploader is on', () => {
    seedPrefs({ showUploader: true, showPostedTime: false })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__uploader').exists()).toBe(true)
    expect(wrapper.find('.gallery-card__posted').exists()).toBe(false)
  })

  it('does not render uploader in grid mode', () => {
    seedPrefs({ showUploader: true, showPostedTime: true })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'grid' },
    })
    expect(wrapper.find('.gallery-card__uploader').exists()).toBe(false)
    expect(wrapper.find('.gallery-card__posted').exists()).toBe(false)
  })
})

describe('GalleryCard (R4-6 empty title → #gid fallback)', () => {
  it.each([[''], ['   ']])(
    'renders the #gid fallback in list mode when the title is %j',
    (title) => {
      const wrapper = mount(GalleryCard, {
        props: { gallery: makeGallery({ title, gid: 987654 }), mode: 'list' },
      })
      expect(wrapper.find('.gallery-card__title').text()).toBe('#987654')
    },
  )

  it('renders #gid in grid mode when the title is empty', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ title: '', gid: 4242 }), mode: 'grid' },
    })
    expect(wrapper.find('.gallery-card__grid-title').text()).toBe('#4242')
  })

  it('uses #gid for the thumbnail alt text when the title is empty', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ title: '', gid: 777 }), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__thumb img').attributes('alt')).toBe('#777')
  })

  it('keeps a non-empty title untouched', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ title: 'Real Title', gid: 1 }), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__title').text()).toBe('Real Title')
  })
})

describe('GalleryCard (B-4 favoriteSlotNames)', () => {
  it('exposes the 10 default names when prefs are unloaded', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    const names = (wrapper.vm as unknown as { favoriteSlotNames: string[] }).favoriteSlotNames
    expect(names).toEqual([...DEFAULT_FAVORITE_SLOT_NAMES])
    expect(names).toHaveLength(10)
  })

  it('splits general.favoriteSlotNames on | and falls back per empty slot', () => {
    seedPrefs({ favoriteSlotNames: 'Manga||Doujin|  ' })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    const names = (wrapper.vm as unknown as { favoriteSlotNames: string[] }).favoriteSlotNames
    expect(names).toEqual([
      'Manga',
      'Favorites 1', // empty entry → default
      'Doujin',
      'Favorites 3', // whitespace-only entry → default
      'Favorites 4',
      'Favorites 5',
      'Favorites 6',
      'Favorites 7',
      'Favorites 8',
      'Favorites 9',
    ])
  })

  it('takes at most 10 names', () => {
    seedPrefs({ favoriteSlotNames: Array.from({ length: 14 }, (_, i) => `S${i}`).join('|') })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery(), mode: 'list' },
    })
    const names = (wrapper.vm as unknown as { favoriteSlotNames: string[] }).favoriteSlotNames
    expect(names).toHaveLength(10)
    expect(names[9]).toBe('S9')
  })
})

describe('parseFavoriteSlotNames (pure helper)', () => {
  it('returns all defaults for non-string input', () => {
    expect(parseFavoriteSlotNames(undefined)).toEqual([...DEFAULT_FAVORITE_SLOT_NAMES])
    expect(parseFavoriteSlotNames(null)).toEqual([...DEFAULT_FAVORITE_SLOT_NAMES])
    expect(parseFavoriteSlotNames(42)).toEqual([...DEFAULT_FAVORITE_SLOT_NAMES])
  })

  it('returns all defaults for an empty string', () => {
    expect(parseFavoriteSlotNames('')).toEqual([...DEFAULT_FAVORITE_SLOT_NAMES])
  })

  it('keeps the FavoriteView default naming for unfilled slots', () => {
    // FavoriteView has always used "Favorites 0" … "Favorites 9".
    expect(DEFAULT_FAVORITE_SLOT_NAMES[0]).toBe('Favorites 0')
    expect(DEFAULT_FAVORITE_SLOT_NAMES[9]).toBe('Favorites 9')
  })
})

describe('GalleryCard (R4-9 site thumbnail proxy rewrite)', () => {
  it('rewrites site-host thumbnails through the image proxy (list mode)', () => {
    const wrapper = mount(GalleryCard, {
      props: {
        gallery: makeGallery({ thumb: 'https://ehgt.org/t/123/cover.jpg' }),
        mode: 'list',
      },
    })
    expect(wrapper.find('.gallery-card__thumb img').attributes('src')).toBe(
      '/api/v1/image/proxy?url=' + encodeURIComponent('https://ehgt.org/t/123/cover.jpg'),
    )
  })

  it('rewrites site-host thumbnails in grid mode too', () => {
    const wrapper = mount(GalleryCard, {
      props: {
        gallery: makeGallery({ thumb: 'https://lofi.e-hentai.org/t/9001/1.jpg' }),
        mode: 'grid',
      },
    })
    expect(wrapper.find('.gallery-card__tile img').attributes('src')).toBe(
      '/api/v1/image/proxy?url=' + encodeURIComponent('https://lofi.e-hentai.org/t/9001/1.jpg'),
    )
  })

  it('keeps non-site thumbnail URLs untouched', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ thumb: 'https://example.com/thumb.jpg' }), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__thumb img').attributes('src')).toBe(
      'https://example.com/thumb.jpg',
    )
  })

  it('keeps the placeholder for an empty site-host-less thumb', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ thumb: '' }), mode: 'list' },
    })
    expect(wrapper.find('.gallery-card__thumb img').exists()).toBe(false)
    expect(wrapper.find('.gallery-card__thumb-placeholder').exists()).toBe(true)
  })
})

describe('GalleryCard (F-UX6 PC quick actions + context menu)', () => {
  const writeTextMock = vi.fn()

  /** Stub the `(pointer: fine)` media query (happy-dom reports no match). */
  function stubPointerQuery(fine: boolean): void {
    vi.spyOn(window, 'matchMedia').mockImplementation(
      (query: string) =>
        ({
          matches: fine && query === '(pointer: fine)',
          media: query,
          onchange: null,
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          addListener: vi.fn(),
          removeListener: vi.fn(),
          dispatchEvent: () => false,
        }) as unknown as MediaQueryList,
    )
  }

  function setViewportWidth(width: number): void {
    const happyDOM = (
      window as unknown as { happyDOM?: { setInnerWidth?: (width: number) => void } }
    ).happyDOM
    if (happyDOM?.setInnerWidth) happyDOM.setInnerWidth(width)
    else Object.defineProperty(window, 'innerWidth', { configurable: true, value: width })
  }

  function mountPcCard(overrides: Partial<GalleryInfo> = {}, mode: 'grid' | 'list' = 'grid') {
    stubPointerQuery(true)
    setViewportWidth(1280)
    return mount(GalleryCard, { props: { gallery: makeGallery(overrides), mode } })
  }

  function contextMenuItems(): HTMLButtonElement[] {
    return Array.from(document.body.querySelectorAll('.card-context-menu__item'))
  }

  beforeEach(() => {
    pushMock.mockClear()
    writeTextMock.mockReset().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: writeTextMock },
    })
    vi.mocked(favoriteApi.addFavorite).mockReset().mockResolvedValue({ success: true })
    vi.mocked(favoriteApi.removeFavorite).mockReset().mockResolvedValue({ success: true })
    vi.mocked(downloadApi.add).mockReset().mockResolvedValue(true)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    setViewportWidth(1024) // happy-dom default
  })

  describe('hover quick-action bar (PC form only)', () => {
    it('renders the three quick actions on a fine-pointer ≥720px viewport (grid)', () => {
      // favoriteSlot -2 = not favorited, so the first action is "Favorite".
      const wrapper = mountPcCard({ favoriteSlot: -2 })
      const bar = wrapper.find('.card-quick-actions')
      expect(bar.exists()).toBe(true)
      expect(bar.classes()).toContain('card-quick-actions--grid')
      const buttons = bar.findAll('button')
      expect(buttons).toHaveLength(3)
      expect(buttons.map((b) => b.attributes('aria-label'))).toEqual([
        'Favorite',
        'Download',
        'Details',
      ])
    })

    it('renders the pill variant in list mode', () => {
      const wrapper = mountPcCard({}, 'list')
      const bar = wrapper.find('.card-quick-actions')
      expect(bar.exists()).toBe(true)
      expect(bar.classes()).toContain('card-quick-actions--list')
    })

    it('does NOT render under a 375px viewport (mobile red line)', () => {
      stubPointerQuery(true)
      setViewportWidth(375)
      const wrapper = mount(GalleryCard, {
        props: { gallery: makeGallery(), mode: 'grid' },
      })
      expect(wrapper.find('.card-quick-actions').exists()).toBe(false)
    })

    it('does NOT render with a coarse pointer on a wide viewport', () => {
      stubPointerQuery(false)
      setViewportWidth(1280)
      const wrapper = mount(GalleryCard, {
        props: { gallery: makeGallery(), mode: 'grid' },
      })
      expect(wrapper.find('.card-quick-actions').exists()).toBe(false)
    })

    it('favorite action calls favoriteApi.addFavorite with the defaultFavoriteSlot', async () => {
      seedPrefs({ defaultFavoriteSlot: 3 })
      const wrapper = mountPcCard({ favoriteSlot: -2 }) // not favorited → add
      await wrapper.findAll('.card-quick-actions__btn')[0].trigger('click')
      expect(favoriteApi.addFavorite).toHaveBeenCalledWith(12345, 'abc123', 0x2, 3)
      await flushPromises()
      expect(wrapper.find('.gallery-card__toast').text()).toBe('Favorited')
    })

    it('favorite action omits the slot while preferences are unloaded', async () => {
      const wrapper = mountPcCard({ favoriteSlot: -2 }) // not favorited → add
      await wrapper.findAll('.card-quick-actions__btn')[0].trigger('click')
      expect(favoriteApi.addFavorite).toHaveBeenCalledWith(12345, 'abc123', 0x2, undefined)
    })

    it('a favorited gallery offers removal (aria-pressed + removeFavorite)', async () => {
      const wrapper = mountPcCard({ favoriteSlot: 5 })
      const favBtn = wrapper.findAll('.card-quick-actions__btn')[0]
      expect(favBtn.attributes('aria-pressed')).toBe('true')
      expect(favBtn.attributes('aria-label')).toBe('Remove from favorites')
      await favBtn.trigger('click')
      expect(favoriteApi.removeFavorite).toHaveBeenCalledWith(12345, 'abc123')
      expect(favoriteApi.addFavorite).not.toHaveBeenCalled()
    })

    it('download action queues the gallery via downloadApi.add', async () => {
      const wrapper = mountPcCard()
      await wrapper.findAll('.card-quick-actions__btn')[1].trigger('click')
      expect(downloadApi.add).toHaveBeenCalledWith(
        12345,
        'abc123',
        '(C99) Test Gallery Title [English]',
        'https://example.com/thumb.jpg',
      )
      await flushPromises()
      expect(wrapper.find('.gallery-card__toast').text()).toBe('Added to downloads')
    })

    it('details action navigates to the gallery detail route', async () => {
      const wrapper = mountPcCard()
      await wrapper.findAll('.card-quick-actions__btn')[2].trigger('click')
      expect(pushMock).toHaveBeenCalledWith({ path: '/gallery/12345', query: { token: 'abc123' } })
    })

    it('quick-action clicks never trigger the card navigation', async () => {
      const wrapper = mountPcCard()
      await wrapper.findAll('.card-quick-actions__btn')[1].trigger('click')
      expect(wrapper.emitted('click')).toBeUndefined()
    })
  })

  describe('right-click context menu', () => {
    it('opens with details / favorite / download / copy-link on contextmenu', async () => {
      // favoriteSlot -2 = not favorited, so the menu offers "Favorite".
      const wrapper = mountPcCard({ favoriteSlot: -2 })
      await wrapper.trigger('contextmenu', { clientX: 120, clientY: 80 })
      expect(contextMenuItems().map((item) => item.textContent?.trim())).toEqual([
        'Details',
        'Favorite',
        'Download',
        'Copy link',
      ])
    })

    it('copy link writes the app-internal detail URL (neutralized, no site host)', async () => {
      const wrapper = mountPcCard()
      await wrapper.trigger('contextmenu', { clientX: 10, clientY: 10 })
      contextMenuItems()[3].click()
      await flushPromises()
      expect(writeTextMock).toHaveBeenCalledTimes(1)
      const url = writeTextMock.mock.calls[0][0] as string
      expect(url).toBe(`${window.location.origin}/gallery/12345`)
      expect(url).not.toContain('exhentai')
      expect(url).not.toContain('e-hentai')
    })

    it('menu favorite honors the favorited state', async () => {
      const wrapper = mountPcCard({ favoriteSlot: 0 })
      await wrapper.trigger('contextmenu', { clientX: 10, clientY: 10 })
      const labels = contextMenuItems().map((item) => item.textContent?.trim())
      expect(labels).toContain('Remove from favorites')
    })

    it('invoking a menu action closes the menu and runs the action', async () => {
      const wrapper = mountPcCard()
      await wrapper.trigger('contextmenu', { clientX: 10, clientY: 10 })
      contextMenuItems()[0].click() // Details
      await flushPromises()
      expect(pushMock).toHaveBeenCalledWith({ path: '/gallery/12345', query: { token: 'abc123' } })
      expect(document.body.querySelector('.card-context-menu')).toBeNull()
    })

    it('does NOT open on non-PC viewports (375px)', async () => {
      stubPointerQuery(true)
      setViewportWidth(375)
      const wrapper = mount(GalleryCard, {
        props: { gallery: makeGallery(), mode: 'grid' },
      })
      await wrapper.trigger('contextmenu', { clientX: 10, clientY: 10 })
      expect(document.body.querySelector('.card-context-menu')).toBeNull()
    })
  })
})

/* ═══════════════════════════════════════════════════════════════════════
 * W5 additions (plan-2026-09-02 §5.3 W5 / §8 前端) — 阅读进度角标：
 * showReadProgress 开关 + readProgress 值共同驱动显隐，格式 N/MP / NP，
 * 列表形态顶替页数文案（Android GalleryAdapterNew 同槽位替换语义），
 * 网格形态挂瓦片右下角。
 * ═══════════════════════════════════════════════════════════════════════ */

describe('GalleryCard W5 — 阅读进度角标', () => {
  it('shows N/MP in list mode when showReadProgress is on and readProgress > 0', () => {
    seedPrefs({ showReadProgress: true })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ readProgress: 3, pages: 24 }), mode: 'list' },
    })
    const badge = wrapper.find('[data-testid="read-progress-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('4/24P')
    // 顶替页数文案（Android 同一 TextView 替换语义），不与 24P 重复。
    expect(wrapper.findAll('.gallery-card__pages')).toHaveLength(1)
  })

  it('formats NP when the page count is unknown (pages ≤ 0)', () => {
    seedPrefs({ showReadProgress: true })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ readProgress: 3, pages: 0 }), mode: 'list' },
    })
    expect(wrapper.find('[data-testid="read-progress-badge"]').text()).toBe('4P')
  })

  it('hides the badge when showReadProgress is off (plain page count stays)', () => {
    seedPrefs({ showReadProgress: false })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ readProgress: 3, pages: 24 }), mode: 'list' },
    })
    expect(wrapper.find('[data-testid="read-progress-badge"]').exists()).toBe(false)
    expect(wrapper.find('.gallery-card__pages').text()).toBe('24P')
  })

  it('hides the badge when readProgress is 0 or missing (legacy server)', () => {
    seedPrefs({ showReadProgress: true })
    const zero = mount(GalleryCard, {
      props: { gallery: makeGallery({ readProgress: 0, pages: 24 }), mode: 'list' },
    })
    expect(zero.find('[data-testid="read-progress-badge"]').exists()).toBe(false)
    expect(zero.find('.gallery-card__pages').text()).toBe('24P')
    const legacy = mount(GalleryCard, {
      props: { gallery: makeGallery({ pages: 24 }), mode: 'list' },
    })
    expect(legacy.find('[data-testid="read-progress-badge"]').exists()).toBe(false)
  })

  it('hides the badge when prefs are unloaded', () => {
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ readProgress: 3, pages: 24 }), mode: 'list' },
    })
    expect(wrapper.find('[data-testid="read-progress-badge"]').exists()).toBe(false)
  })

  it('rides the grid tile bottom-right corner in grid mode', () => {
    seedPrefs({ showReadProgress: true })
    const wrapper = mount(GalleryCard, {
      props: { gallery: makeGallery({ readProgress: 9, pages: 24 }), mode: 'grid' },
    })
    const badge = wrapper.find('[data-testid="read-progress-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.classes()).toContain('gallery-card__progress')
    expect(badge.text()).toBe('10/24P')
  })
})
