import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CategoryTriangle from '../CategoryTriangle.vue'
import { CATEGORY_LABELS, type GalleryCategory } from '@/types/components'

describe('CategoryTriangle', () => {
  it('renders a triangle element', () => {
    const wrapper = mount(CategoryTriangle, { props: { category: 'doujinshi' } })
    expect(wrapper.find('.category-triangle').exists()).toBe(true)
  })

  it('uses the category color token for the top border', () => {
    const wrapper = mount(CategoryTriangle, { props: { category: 'game_cg' } })
    expect(wrapper.attributes('style')).toContain('var(--color-cat-game-cg)')
  })

  it('maps underscore category keys to hyphenated tokens', () => {
    const wrapper = mount(CategoryTriangle, { props: { category: 'image_set' } })
    expect(wrapper.attributes('style')).toContain('var(--color-cat-image-set)')
  })

  it('covers every category with a token and accessible label', () => {
    for (const category of Object.keys(CATEGORY_LABELS) as GalleryCategory[]) {
      const wrapper = mount(CategoryTriangle, { props: { category } })
      expect(wrapper.attributes('style'), `token for ${category}`).toContain('--color-cat-')
      expect(wrapper.attributes('aria-label')).toBe(CATEGORY_LABELS[category])
    }
  })

  it('exposes an accessible label for the category', () => {
    const wrapper = mount(CategoryTriangle, { props: { category: 'misc' } })
    expect(wrapper.attributes('role')).toBe('img')
    expect(wrapper.attributes('aria-label')).toBe('Misc')
  })
})
