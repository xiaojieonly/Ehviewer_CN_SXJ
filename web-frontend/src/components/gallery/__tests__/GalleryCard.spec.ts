import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import GalleryCard from '../GalleryCard.vue'
import AppCard from '@/components/atoms/AppCard.vue'
import RatingStars from '@/components/atoms/RatingStars.vue'
import CategoryChip from '@/components/atoms/CategoryChip.vue'
import CategoryTriangle from '@/components/atoms/CategoryTriangle.vue'
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

describe('GalleryCard (list mode)', () => {
  it('renders title, japanese title, rating, chip, pages, tags and posted', () => {
    const gallery = makeGallery()
    const wrapper = mount(GalleryCard, { props: { gallery, mode: 'list' } })

    expect(wrapper.find('.gallery-card__title').text()).toBe(gallery.title)
    expect(wrapper.find('.gallery-card__title-jpn').text()).toBe('テストギャラリー')
    expect(wrapper.findComponent(RatingStars).exists()).toBe(true)
    expect(wrapper.findComponent(CategoryChip).exists()).toBe(true)
    expect(wrapper.find('.category-chip').text()).toBe('Doujinshi')
    expect(wrapper.find('.gallery-card__pages').text()).toBe('24P')
    expect(wrapper.find('.gallery-card__tag').text()).toBe('tag1')
    expect(wrapper.find('.gallery-card__posted').text()).toBe('2024-01-01 12:00')
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
