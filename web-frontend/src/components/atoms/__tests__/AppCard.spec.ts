import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppCard from '../AppCard.vue'
import RatingStars from '../RatingStars.vue'
import CategoryChip from '../CategoryChip.vue'
import CategoryTriangle from '../CategoryTriangle.vue'
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

describe('AppCard (list mode)', () => {
  it('renders title, uploader, rating and category chip', () => {
    const gallery = makeGallery()
    const wrapper = mount(AppCard, { props: { gallery, mode: 'list' } })

    expect(wrapper.find('.app-card__title').text()).toBe(gallery.title)
    expect(wrapper.find('.app-card__uploader').text()).toBe('uploader123')
    expect(wrapper.findComponent(RatingStars).exists()).toBe(true)
    expect(wrapper.findComponent(CategoryChip).exists()).toBe(true)
    expect(wrapper.find('.category-chip').text()).toBe('Doujinshi')
  })

  it('renders the list thumbnail at the fixed 80×120 size', () => {
    const wrapper = mount(AppCard, { props: { gallery: makeGallery(), mode: 'list' } })
    const img = wrapper.find('.app-card__thumb img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://example.com/thumb.jpg')
  })

  it('does not render grid-only elements in list mode', () => {
    const wrapper = mount(AppCard, { props: { gallery: makeGallery(), mode: 'list' } })
    expect(wrapper.findComponent(CategoryTriangle).exists()).toBe(false)
    expect(wrapper.find('.app-card__language').exists()).toBe(false)
  })

  it('passes the gallery rating down to RatingStars', () => {
    const wrapper = mount(AppCard, {
      props: { gallery: makeGallery({ rating: 3.5 }), mode: 'list' },
    })
    expect(wrapper.findComponent(RatingStars).props('rating')).toBe(3.5)
  })
})

describe('AppCard (grid mode)', () => {
  it('renders the image tile, corner triangle and language badge', () => {
    const wrapper = mount(AppCard, { props: { gallery: makeGallery(), mode: 'grid' } })
    expect(wrapper.find('.app-card__tile img').attributes('src')).toBe(
      'https://example.com/thumb.jpg',
    )
    expect(wrapper.findComponent(CategoryTriangle).exists()).toBe(true)
    expect(wrapper.find('.app-card__language').text()).toBe('EN')
  })

  it('does not render the list info column in grid mode', () => {
    const wrapper = mount(AppCard, { props: { gallery: makeGallery(), mode: 'grid' } })
    expect(wrapper.find('.app-card__title').exists()).toBe(false)
    expect(wrapper.find('.app-card__uploader').exists()).toBe(false)
  })

  it('derives the tile aspect ratio from thumb dimensions', () => {
    const wrapper = mount(AppCard, {
      props: { gallery: makeGallery({ thumbWidth: 300, thumbHeight: 400 }), mode: 'grid' },
    })
    // 300/400 = 0.75, inside the [0.33, 1.5] clamp.
    expect(wrapper.find('.app-card__tile').attributes('style')).toContain('aspect-ratio')
    expect(wrapper.find('.app-card__tile').attributes('style')).toContain('0.75')
  })

  it('falls back to a 0.67 aspect when thumb dimensions are unknown', () => {
    const wrapper = mount(AppCard, {
      props: { gallery: makeGallery({ thumbWidth: 0, thumbHeight: 0 }), mode: 'grid' },
    })
    expect(wrapper.find('.app-card__tile').attributes('style')).toContain('0.67')
  })

  it('hides the language badge when simpleLanguage is empty', () => {
    const wrapper = mount(AppCard, {
      props: { gallery: makeGallery({ simpleLanguage: '' }), mode: 'grid' },
    })
    expect(wrapper.find('.app-card__language').exists()).toBe(false)
  })
})

describe('AppCard (category + events)', () => {
  it('converts the numeric category bit to a chip/triangle', () => {
    // 0x8 = artist_cg
    const wrapper = mount(AppCard, {
      props: { gallery: makeGallery({ category: 0x8 }), mode: 'list' },
    })
    expect(wrapper.find('.category-chip').text()).toBe('Artist CG')
  })

  it('omits chip and triangle for an unknown category bit', () => {
    const wrapper = mount(AppCard, {
      props: { gallery: makeGallery({ category: 0x400 }), mode: 'grid' },
    })
    expect(wrapper.findComponent(CategoryChip).exists()).toBe(false)
    expect(wrapper.findComponent(CategoryTriangle).exists()).toBe(false)
  })

  it('emits click with the gallery', async () => {
    const gallery = makeGallery()
    const wrapper = mount(AppCard, { props: { gallery, mode: 'list' } })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toBeTruthy()
    expect(wrapper.emitted('click')![0][0]).toEqual(gallery)
  })

  it('emits click on keyboard activation', async () => {
    const gallery = makeGallery()
    const wrapper = mount(AppCard, { props: { gallery, mode: 'grid' } })
    await wrapper.trigger('keydown.enter')
    expect(wrapper.emitted('click')).toBeTruthy()
  })

  it('applies the mode class to the card root', () => {
    const list = mount(AppCard, { props: { gallery: makeGallery(), mode: 'list' } })
    const grid = mount(AppCard, { props: { gallery: makeGallery(), mode: 'grid' } })
    expect(list.classes()).toContain('app-card--list')
    expect(grid.classes()).toContain('app-card--grid')
  })
})
