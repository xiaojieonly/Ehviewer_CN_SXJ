import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FilterSlotBar from '../FilterSlotBar.vue'
import type { FilterSlot } from '@/api/filterSlots'

const SLOTS: FilterSlot[] = [
  { id: 's1', name: '画风A', pattern: '^[Aa]rt' },
  { id: 's2', name: '汉化', pattern: '汉化' },
]

describe('FilterSlotBar', () => {
  it('renders the 全部 chip plus one chip per slot', () => {
    const wrapper = mount(FilterSlotBar, { props: { slots: SLOTS, activeId: null } })
    const chips = wrapper.findAll('button.filter-slot-bar__chip')
    expect(chips).toHaveLength(3)
    expect(chips[0].text()).toBe('全部')
    expect(chips[1].text()).toBe('画风A')
    expect(chips[2].text()).toBe('汉化')
  })

  it('marks the active chip with the active class and aria-pressed', () => {
    const wrapper = mount(FilterSlotBar, { props: { slots: SLOTS, activeId: 's1' } })
    const chips = wrapper.findAll('button.filter-slot-bar__chip')
    expect(chips[0].attributes('aria-pressed')).toBe('false')
    expect(chips[1].attributes('aria-pressed')).toBe('true')
    expect(chips[1].classes()).toContain('filter-slot-bar__chip--active')
    expect(chips[2].classes()).not.toContain('filter-slot-bar__chip--active')
  })

  it('emits null for 全部 and the slot id for a slot chip', async () => {
    const wrapper = mount(FilterSlotBar, { props: { slots: SLOTS, activeId: null } })
    const chips = wrapper.findAll('button.filter-slot-bar__chip')
    await chips[0].trigger('click')
    await chips[2].trigger('click')
    expect(wrapper.emitted('select')).toEqual([[null], ['s2']])
  })
})
