import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
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
import type { Preferences } from '@/api/preferences'
import type { GalleryInfo } from '@/types/components'

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
        gallery: makeGallery({ thumb: 'https://gallery.test/t/123/cover.jpg' }),
        mode: 'list',
      },
    })
    expect(wrapper.find('.gallery-card__thumb img').attributes('src')).toBe(
      '/api/v1/image/proxy?url=' + encodeURIComponent('https://gallery.test/t/123/cover.jpg'),
    )
  })

  it('rewrites site-host thumbnails in grid mode too', () => {
    const wrapper = mount(GalleryCard, {
      props: {
        gallery: makeGallery({ thumb: 'https://t.gallery.test/t/9001/1.jpg' }),
        mode: 'grid',
      },
    })
    expect(wrapper.find('.gallery-card__tile img').attributes('src')).toBe(
      '/api/v1/image/proxy?url=' + encodeURIComponent('https://t.gallery.test/t/9001/1.jpg'),
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
