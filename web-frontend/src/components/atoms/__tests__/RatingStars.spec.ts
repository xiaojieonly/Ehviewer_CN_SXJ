import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RatingStars from '../RatingStars.vue'

/** Count rendered stars per visual state. */
function starCounts(wrapper: ReturnType<typeof mount>) {
  return {
    full: wrapper.findAll('.rating-star--full').length,
    half: wrapper.findAll('.rating-star--half').length,
    empty: wrapper.findAll('.rating-star--empty').length,
  }
}

describe('RatingStars', () => {
  it('always renders exactly 5 stars', () => {
    for (const rating of [0, 1, 2.5, 4.5, 5]) {
      const wrapper = mount(RatingStars, { props: { rating } })
      expect(wrapper.findAll('.rating-star')).toHaveLength(5)
    }
  })

  it('renders all empty stars for rating 0', () => {
    const wrapper = mount(RatingStars, { props: { rating: 0 } })
    expect(starCounts(wrapper)).toEqual({ full: 0, half: 0, empty: 5 })
  })

  it('renders all full stars for the maximum 0–5 rating', () => {
    // gallery.rating is a 0–5 float; 5 → ceil(10)=10 half-stars → 5 full.
    const wrapper = mount(RatingStars, { props: { rating: 5 } })
    expect(starCounts(wrapper)).toEqual({ full: 5, half: 0, empty: 0 })
  })

  it('renders a half star for .5 fractions', () => {
    const wrapper = mount(RatingStars, { props: { rating: 4.5 } })
    expect(starCounts(wrapper)).toEqual({ full: 4, half: 1, empty: 0 })
  })

  it('mixes full, half and empty stars (3.5)', () => {
    const wrapper = mount(RatingStars, { props: { rating: 3.5 } })
    expect(starCounts(wrapper)).toEqual({ full: 3, half: 1, empty: 1 })
  })

  it('rounds a small rating up to a single half star', () => {
    // ceil(0.1 * 2) = ceil(0.2) = 1 half-star.
    const wrapper = mount(RatingStars, { props: { rating: 0.1 } })
    expect(starCounts(wrapper)).toEqual({ full: 0, half: 1, empty: 4 })
  })

  it('clamps out-of-range ratings', () => {
    expect(starCounts(mount(RatingStars, { props: { rating: 99 } }))).toEqual({
      full: 5,
      half: 0,
      empty: 0,
    })
    expect(starCounts(mount(RatingStars, { props: { rating: -3 } }))).toEqual({
      full: 0,
      half: 0,
      empty: 5,
    })
  })

  it('applies the size prop to each star', () => {
    const wrapper = mount(RatingStars, { props: { rating: 3, size: 24 } })
    const star = wrapper.find('.rating-star')
    expect(star.attributes('width')).toBe('24')
    expect(star.attributes('height')).toBe('24')
  })

  it('defaults stars to 16px', () => {
    const wrapper = mount(RatingStars, { props: { rating: 3 } })
    const star = wrapper.find('.rating-star')
    expect(star.attributes('width')).toBe('16')
    expect(star.attributes('height')).toBe('16')
  })

  it('applies the gap prop to the container', () => {
    const wrapper = mount(RatingStars, { props: { rating: 3, gap: 4 } })
    expect(wrapper.find('.rating-stars').attributes('style')).toContain('gap: 4px')
  })

  it('exposes an accessible label', () => {
    const wrapper = mount(RatingStars, { props: { rating: 4.5 } })
    expect(wrapper.attributes('role')).toBe('img')
    expect(wrapper.attributes('aria-label')).toContain('4.5')
  })

  it('renders inline SVG paths for each star', () => {
    const wrapper = mount(RatingStars, { props: { rating: 2.5 } })
    expect(wrapper.findAll('.rating-star path')).toHaveLength(5)
  })
})
