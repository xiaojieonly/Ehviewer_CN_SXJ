import { describe, it, expect } from 'vitest'
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import PrefCard from '../PrefCard.vue'
import PrefRow from '../PrefRow.vue'

describe('PrefCard', () => {
  it('renders default slot content', () => {
    const wrapper = mount(PrefCard, { slots: { default: '<div class="row">a</div>' } })
    expect(wrapper.find('.pref-card').exists()).toBe(true)
    expect(wrapper.find('.pref-card .row').exists()).toBe(true)
  })

  it('renders empty when no slot content', () => {
    const wrapper = mount(PrefCard)
    expect(wrapper.find('.pref-card').exists()).toBe(true)
    expect(wrapper.find('.pref-card').text()).toBe('')
  })

  it('hosts PrefRow children', () => {
    const wrapper = mount(PrefCard, {
      slots: { default: () => h(PrefRow, { title: 'x' }) },
    })
    expect(wrapper.findComponent(PrefRow).exists()).toBe(true)
    expect(wrapper.find('.pref-row__title').text()).toBe('x')
  })
})
