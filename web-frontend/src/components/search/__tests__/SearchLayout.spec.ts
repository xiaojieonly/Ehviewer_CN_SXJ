import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SearchLayout from '../SearchLayout.vue'
import CategoryTable from '../CategoryTable.vue'
import type { AdvanceSearchOptions, GalleryCategory } from '@/types/components'
import { ADVANCE_SEARCH_BITS, CATEGORY_ORDER } from '@/types/components'

describe('SearchLayout', () => {
  describe('composition', () => {
    it('embeds a CategoryTable selected with all 10 categories by default', () => {
      const wrapper = mount(SearchLayout)
      const table = wrapper.findComponent(CategoryTable)
      expect(table.exists()).toBe(true)
      expect(table.props('selected')).toEqual([...CATEGORY_ORDER])
    })

    it('forwards CategoryTable selections via update:selectedCategories', async () => {
      const wrapper = mount(SearchLayout)
      const next: GalleryCategory[] = ['manga']
      wrapper.findComponent(CategoryTable).vm.$emit('update:selected', next)
      expect(wrapper.emitted('update:selectedCategories')?.[0]).toEqual([next])
    })

    it('honors a custom selectedCategories prop', () => {
      const wrapper = mount(SearchLayout, {
        props: { selectedCategories: ['misc', 'western'] },
      })
      expect(wrapper.findComponent(CategoryTable).props('selected')).toEqual(['misc', 'western'])
    })
  })

  describe('expand / collapse', () => {
    it('is expanded by default', () => {
      const wrapper = mount(SearchLayout)
      expect(wrapper.classes()).not.toContain('is-collapsed')
      expect(wrapper.find('.search-layout__toggle').attributes('aria-expanded')).toBe('true')
    })

    it('collapses via the expanded prop', () => {
      const wrapper = mount(SearchLayout, { props: { expanded: false } })
      expect(wrapper.classes()).toContain('is-collapsed')
      expect(wrapper.find('.search-layout__toggle').attributes('aria-expanded')).toBe('false')
    })

    it('emits update:expanded when the header chevron is tapped', async () => {
      const wrapper = mount(SearchLayout)
      await wrapper.find('.search-layout__toggle').trigger('click')
      expect(wrapper.emitted('update:expanded')?.[0]).toEqual([false])
    })
  })

  describe('keyword search mode radio group', () => {
    it('renders the four Android modes', () => {
      const wrapper = mount(SearchLayout)
      const radios = wrapper.findAll('.search-layout__mode')
      expect(radios.map((r) => r.text())).toEqual(['Search', 'Subscription', 'Uploader', 'Tag'])
    })

    it('emits update:normalSearchMode on selection', async () => {
      const wrapper = mount(SearchLayout)
      await wrapper.findAll('.search-layout__mode')[1].trigger('click')
      expect(wrapper.emitted('update:normalSearchMode')?.[0]).toEqual(['subscription'])
    })
  })

  describe('advanced search', () => {
    it('toggles the advance section via the switch', async () => {
      const wrapper = mount(SearchLayout)
      expect(wrapper.find('.search-layout__advance-clip').classes()).not.toContain('is-open')
      await wrapper.find('.search-layout__switch').trigger('click')
      expect(wrapper.emitted('update:enableAdvance')?.[0]).toEqual([true])
    })

    it('opens the advance section when enableAdvance is set', () => {
      const wrapper = mount(SearchLayout, { props: { enableAdvance: true } })
      expect(wrapper.find('.search-layout__advance-clip').classes()).toContain('is-open')
      // 8 search checkboxes + 3 default-filter checkboxes.
      expect(wrapper.findAll('.search-layout__check')).toHaveLength(11)
    })

    it('emits update:advanceOptions with the toggled bitmask bit', async () => {
      const wrapper = mount(SearchLayout, { props: { enableAdvance: true } })
      const first = wrapper.find('.search-layout__check input')
      await first.setValue(true)
      const emitted = wrapper.emitted('update:advanceOptions')?.[0][0] as AdvanceSearchOptions
      expect(emitted.advanceSearch & ADVANCE_SEARCH_BITS.SNAME).toBe(ADVANCE_SEARCH_BITS.SNAME)
    })

    it('clears a bit that is already set', async () => {
      const wrapper = mount(SearchLayout, {
        props: {
          enableAdvance: true,
          advanceOptions: { advanceSearch: ADVANCE_SEARCH_BITS.STAGS, minRating: 0, pageFrom: 0, pageTo: 0 },
        },
      })
      // Second checkbox = STAGS.
      await wrapper.findAll('.search-layout__check input')[1].setValue(false)
      const emitted = wrapper.emitted('update:advanceOptions')?.[0][0] as AdvanceSearchOptions
      expect(emitted.advanceSearch & ADVANCE_SEARCH_BITS.STAGS).toBe(0)
    })

    it('sets the minimum rating from the stars', async () => {
      const wrapper = mount(SearchLayout, { props: { enableAdvance: true } })
      await wrapper.findAll('.search-layout__star')[2].trigger('click')
      const emitted = wrapper.emitted('update:advanceOptions')?.[0][0] as AdvanceSearchOptions
      expect(emitted.minRating).toBe(3)
    })

    it('steps the minimum rating down when tapping the current maximum', async () => {
      const wrapper = mount(SearchLayout, {
        props: {
          enableAdvance: true,
          advanceOptions: { advanceSearch: 0, minRating: 4, pageFrom: 0, pageTo: 0 },
        },
      })
      await wrapper.findAll('.search-layout__star')[3].trigger('click')
      const emitted = wrapper.emitted('update:advanceOptions')?.[0][0] as AdvanceSearchOptions
      expect(emitted.minRating).toBe(3)
    })

    it('emits page range updates from the from/to inputs', async () => {
      const wrapper = mount(SearchLayout, { props: { enableAdvance: true } })
      const inputs = wrapper.findAll('.search-layout__page input')
      await inputs[0].setValue('10')
      await inputs[1].setValue('200')
      const events = wrapper.emitted('update:advanceOptions') ?? []
      expect((events[0][0] as AdvanceSearchOptions).pageFrom).toBe(10)
      expect((events[1][0] as AdvanceSearchOptions).pageTo).toBe(200)
    })

    it('treats non-numeric page input as unset (0)', async () => {
      const wrapper = mount(SearchLayout, { props: { enableAdvance: true } })
      await wrapper.findAll('.search-layout__page input')[0].setValue('abc')
      const emitted = wrapper.emitted('update:advanceOptions')?.[0][0] as AdvanceSearchOptions
      expect(emitted.pageFrom).toBe(0)
    })
  })

  describe('image search mode', () => {
    it('shows the image section instead of keyword options', () => {
      const wrapper = mount(SearchLayout, { props: { mode: 'image' } })
      expect(wrapper.findComponent(CategoryTable).exists()).toBe(false)
      expect(wrapper.find('.search-layout__image-box').exists()).toBe(true)
    })

    it('emits select-image from the image section', async () => {
      const wrapper = mount(SearchLayout, { props: { mode: 'image' } })
      await wrapper.find('.search-layout__image-btn').trigger('click')
      expect(wrapper.emitted('select-image')).toHaveLength(1)
    })
  })

  describe('action row', () => {
    it('emits change-mode from the mode toggle', async () => {
      const wrapper = mount(SearchLayout)
      const toggle = wrapper.findAll('.search-layout__action')[0]
      expect(toggle.text()).toBe('Image search')
      await toggle.trigger('click')
      expect(wrapper.emitted('change-mode')).toHaveLength(1)
    })

    it('emits open-tag-selector from the tag selector entrance', async () => {
      const wrapper = mount(SearchLayout)
      await wrapper.findAll('.search-layout__action')[1].trigger('click')
      expect(wrapper.emitted('open-tag-selector')).toHaveLength(1)
    })

    it('emits search with the keyword and current advance options', async () => {
      const options: AdvanceSearchOptions = {
        advanceSearch: ADVANCE_SEARCH_BITS.SNAME | ADVANCE_SEARCH_BITS.STAGS,
        minRating: 4,
        pageFrom: 1,
        pageTo: 100,
      }
      const wrapper = mount(SearchLayout, { props: { keyword: 'naruto', advanceOptions: options } })
      await wrapper.find('.search-layout__search').trigger('click')
      expect(wrapper.emitted('search')?.[0]).toEqual(['naruto', options])
    })
  })
})
