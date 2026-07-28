import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CategoryChip from '../CategoryChip.vue'
import { CATEGORY_LABELS, type GalleryCategory } from '@/types/components'

describe('CategoryChip', () => {
  it('renders the default label for a category', () => {
    const wrapper = mount(CategoryChip, { props: { category: 'doujinshi' } })
    expect(wrapper.text()).toBe('Doujinshi')
  })

  it('renders a non-clickable span by default', () => {
    const wrapper = mount(CategoryChip, { props: { category: 'manga' } })
    expect(wrapper.element.tagName).toBe('SPAN')
    expect(wrapper.classes()).not.toContain('category-chip--clickable')
  })

  it('uses the category color token as background', () => {
    const wrapper = mount(CategoryChip, { props: { category: 'non_h' } })
    expect(wrapper.attributes('style')).toContain('var(--color-cat-non-h)')
  })

  it('maps underscore category keys to hyphenated tokens', () => {
    const cases: Array<[GalleryCategory, string]> = [
      ['artist_cg', '--color-cat-artist-cg'],
      ['game_cg', '--color-cat-game-cg'],
      ['image_set', '--color-cat-image-set'],
      ['asian_porn', '--color-cat-asian-porn'],
    ]
    for (const [category, token] of cases) {
      const wrapper = mount(CategoryChip, { props: { category } })
      expect(wrapper.attributes('style'), `token for ${category}`).toContain(`var(${token})`)
    }
  })

  it('covers every category with a label and token', () => {
    for (const category of Object.keys(CATEGORY_LABELS) as GalleryCategory[]) {
      const wrapper = mount(CategoryChip, { props: { category } })
      expect(wrapper.text(), `label for ${category}`).toBe(CATEGORY_LABELS[category])
      expect(wrapper.attributes('style')).toContain('--color-cat-')
    }
  })

  it('honors a custom label override', () => {
    const wrapper = mount(CategoryChip, {
      props: { category: 'misc', label: 'Custom' },
    })
    expect(wrapper.text()).toBe('Custom')
  })

  it('renders a button and emits click when clickable', async () => {
    const wrapper = mount(CategoryChip, {
      props: { category: 'cosplay', clickable: true },
    })
    expect(wrapper.element.tagName).toBe('BUTTON')
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toBeTruthy()
    expect(wrapper.emitted('click')![0]).toEqual(['cosplay'])
  })

  it('does not emit click when not clickable', async () => {
    const wrapper = mount(CategoryChip, { props: { category: 'western' } })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toBeUndefined()
  })
})
