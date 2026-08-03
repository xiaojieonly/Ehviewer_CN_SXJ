import { describe, it, expect } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import FilterPanel from '../FilterPanel.vue'
import AppSelect from '@/components/form/AppSelect.vue'
import AppSwitch from '@/components/form/AppSwitch.vue'
import type { SearchFilters } from '@/api/gallery'
import { CATEGORY_BIT_VALUES, CATEGORY_ORDER } from '@/types/components'

function mountPanel(props: Partial<{ filters: SearchFilters; open: boolean }> = {}) {
  return mount(FilterPanel, {
    props: { filters: {}, open: true, ...props },
  })
}

describe('FilterPanel', () => {
  describe('visibility', () => {
    it('renders nothing while closed', () => {
      const wrapper = mountPanel({ open: false })
      expect(wrapper.find('.filter-panel').exists()).toBe(false)
    })

    it('renders the anchored popover when open', () => {
      const wrapper = mountPanel({ open: true })
      expect(wrapper.find('.filter-panel').exists()).toBe(true)
      expect(wrapper.find('.filter-panel').attributes('aria-label')).toBe('Search filters')
    })

    it('closes via the header ✕', async () => {
      const wrapper = mountPanel()
      await wrapper.find('.filter-panel__close').trigger('click')
      expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
    })

    it('closes on a mousedown outside the panel', async () => {
      const wrapper = mountPanel()
      await flushPromises()
      // Let the deferred (requestAnimationFrame) listener registration run.
      await new Promise((resolve) => setTimeout(resolve, 30))
      document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
      await flushPromises()
      expect(wrapper.emitted('update:open')?.some((args) => args[0] === false)).toBe(true)
      wrapper.unmount()
    })
  })

  describe('category chips (exclusion bitmask)', () => {
    it('renders 10 chips; all included when no mask is set', () => {
      const wrapper = mountPanel()
      const chips = wrapper.findAll('.filter-panel__chip')
      expect(chips).toHaveLength(CATEGORY_ORDER.length)
      expect(chips.every((chip) => chip.classes('is-included'))).toBe(true)
    })

    it('marks excluded categories from filters.category', () => {
      const wrapper = mountPanel({
        filters: { category: CATEGORY_BIT_VALUES.manga | CATEGORY_BIT_VALUES.misc },
      })
      const chips = wrapper.findAll('.filter-panel__chip')
      const byLabel = new Map(chips.map((chip) => [chip.text(), chip]))
      expect(byLabel.get('Manga')?.classes('is-included')).toBe(false)
      expect(byLabel.get('Misc')?.classes('is-included')).toBe(false)
      expect(byLabel.get('Doujinshi')?.classes('is-included')).toBe(true)
    })

    it('toggling an included category sets its exclusion bit', async () => {
      const wrapper = mountPanel()
      const chips = wrapper.findAll('.filter-panel__chip')
      const index = CATEGORY_ORDER.indexOf('western')
      await chips[index].trigger('click')
      const emitted = wrapper.emitted('update:filters')?.[0][0] as SearchFilters
      expect(emitted.category).toBe(CATEGORY_BIT_VALUES.western)
    })

    it('toggling an excluded category clears its bit again', async () => {
      const wrapper = mountPanel({
        filters: { category: CATEGORY_BIT_VALUES.manga | CATEGORY_BIT_VALUES.misc },
      })
      const chips = wrapper.findAll('.filter-panel__chip')
      const index = CATEGORY_ORDER.indexOf('manga')
      await chips[index].trigger('click')
      const emitted = wrapper.emitted('update:filters')?.[0][0] as SearchFilters
      expect(emitted.category).toBe(CATEGORY_BIT_VALUES.misc)
    })

    it('drops the category field when the last exclusion is undone', async () => {
      const wrapper = mountPanel({ filters: { category: CATEGORY_BIT_VALUES.misc } })
      const chips = wrapper.findAll('.filter-panel__chip')
      const index = CATEGORY_ORDER.indexOf('misc')
      await chips[index].trigger('click')
      const emitted = wrapper.emitted('update:filters')?.[0][0] as SearchFilters
      expect(emitted.category).toBeUndefined()
    })
  })

  describe('sort select', () => {
    it('shows the four sort orders and applies the selection', async () => {
      const wrapper = mountPanel()
      const sortSelect = wrapper.findAllComponents(AppSelect)[0]
      expect(sortSelect.props('options').map((o: { label: string }) => o.label)).toEqual([
        'Default order',
        'Posted time — newest first',
        'Rating — highest first',
        'Title — ascending',
      ])
      sortSelect.vm.$emit('update:modelValue', 2)
      const emitted = wrapper.emitted('update:filters')?.[0][0] as SearchFilters
      expect(emitted.sort).toBe(2)
    })

    it('maps the default order back to an absent sort', async () => {
      const wrapper = mountPanel({ filters: { sort: 3 } })
      wrapper.findAllComponents(AppSelect)[0].vm.$emit('update:modelValue', 0)
      const emitted = wrapper.emitted('update:filters')?.[0][0] as SearchFilters
      expect(emitted.sort).toBeUndefined()
    })
  })

  describe('page bounds', () => {
    it('emits pageMin / pageMax from the numeric inputs', async () => {
      const wrapper = mountPanel()
      const min = wrapper.find('input[aria-label="Minimum page count"]')
      const max = wrapper.find('input[aria-label="Maximum page count"]')
      await min.setValue('5')
      await max.setValue('50')
      const events = wrapper.emitted('update:filters') ?? []
      expect((events[0][0] as SearchFilters).pageMin).toBe(5)
      expect((events[1][0] as SearchFilters).pageMax).toBe(50)
    })

    it('treats zero / non-numeric input as unset', async () => {
      const wrapper = mountPanel({ filters: { pageMin: 5, pageMax: 50 } })
      await wrapper.find('input[aria-label="Minimum page count"]').setValue('0')
      await wrapper.find('input[aria-label="Maximum page count"]').setValue('abc')
      const events = wrapper.emitted('update:filters') ?? []
      expect((events[0][0] as SearchFilters).pageMin).toBeUndefined()
      expect((events[1][0] as SearchFilters).pageMax).toBeUndefined()
    })
  })

  describe('minimum rating', () => {
    it('offers Off + 2..5 and applies the threshold', async () => {
      const wrapper = mountPanel()
      const ratingSelect = wrapper.findAllComponents(AppSelect)[1]
      expect(
        ratingSelect.props('options').map((o: { value: string | number }) => o.value),
      ).toEqual([0, 2, 3, 4, 5])
      ratingSelect.vm.$emit('update:modelValue', 4)
      const emitted = wrapper.emitted('update:filters')?.[0][0] as SearchFilters
      expect(emitted.minRating).toBe(4)
    })

    it('maps Off (0) back to an absent minRating', async () => {
      const wrapper = mountPanel({ filters: { minRating: 3 } })
      wrapper.findAllComponents(AppSelect)[1].vm.$emit('update:modelValue', 0)
      const emitted = wrapper.emitted('update:filters')?.[0][0] as SearchFilters
      expect(emitted.minRating).toBeUndefined()
    })
  })

  describe('search scope switches', () => {
    it('renders the four scope switches bound to the filter state', () => {
      const wrapper = mountPanel({ filters: { searchTags: true } })
      const switches = wrapper.findAllComponents(AppSwitch)
      expect(switches).toHaveLength(4)
      expect(switches.map((s) => s.props('modelValue'))).toEqual([false, true, false, false])
    })

    it('emits the toggled scope flag (true keeps, false clears)', async () => {
      const wrapper = mountPanel()
      const switches = wrapper.findAllComponents(AppSwitch)
      switches[0].vm.$emit('update:modelValue', true)
      switches[3].vm.$emit('update:modelValue', false)
      const events = wrapper.emitted('update:filters') ?? []
      expect((events[0][0] as SearchFilters).searchName).toBe(true)
      expect((events[1][0] as SearchFilters).searchTorrents).toBeUndefined()
    })
  })

  describe('footer actions', () => {
    it('Reset emits an empty filter state', async () => {
      const wrapper = mountPanel({ filters: { sort: 2, minRating: 4 } })
      const buttons = wrapper.findAll('.filter-panel__btn-text')
      expect(buttons[0].text()).toBe('Reset')
      await buttons[0].trigger('click')
      expect(wrapper.emitted('update:filters')?.[0]).toEqual([{}])
    })

    it('Save as quick search emits save-quick-search', async () => {
      const wrapper = mountPanel()
      const buttons = wrapper.findAll('.filter-panel__btn-text')
      expect(buttons[1].text()).toBe('Save as quick search')
      await buttons[1].trigger('click')
      expect(wrapper.emitted('save-quick-search')).toHaveLength(1)
    })

    it('Search emits the primary search action', async () => {
      const wrapper = mountPanel()
      await wrapper.find('.filter-panel__btn-primary').trigger('click')
      expect(wrapper.emitted('search')).toHaveLength(1)
    })
  })

  it('never mutates the prop object (immutable updates)', async () => {
    const filters: SearchFilters = { sort: 1 }
    const wrapper = mountPanel({ filters })
    wrapper.findAllComponents(AppSelect)[1].vm.$emit('update:modelValue', 5)
    expect(filters).toEqual({ sort: 1 })
    expect(wrapper.emitted('update:filters')).toHaveLength(1)
  })
})
