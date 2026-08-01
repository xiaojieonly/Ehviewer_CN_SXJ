import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppSegmented from '../AppSegmented.vue'

const OPTIONS = [
  { value: 'list', label: '列表' },
  { value: 'grid', label: '网格' },
]

describe('AppSegmented', () => {
  it('renders one radio button per option', () => {
    const wrapper = mount(AppSegmented, { props: { modelValue: 'list', options: OPTIONS } })
    const buttons = wrapper.findAll('button.app-segmented__btn')
    expect(buttons).toHaveLength(2)
    expect(buttons[0].text()).toBe('列表')
    expect(buttons[1].text()).toBe('网格')
  })

  it('marks the active option with aria-checked and the active class', () => {
    const wrapper = mount(AppSegmented, { props: { modelValue: 'grid', options: OPTIONS } })
    const buttons = wrapper.findAll('button.app-segmented__btn')
    expect(buttons[0].attributes('aria-checked')).toBe('false')
    expect(buttons[0].classes()).not.toContain('app-segmented__btn--active')
    expect(buttons[1].attributes('aria-checked')).toBe('true')
    expect(buttons[1].classes()).toContain('app-segmented__btn--active')
  })

  it('uses the radiogroup role with the aria label', () => {
    const wrapper = mount(AppSegmented, {
      props: { modelValue: 'list', options: OPTIONS, ariaLabel: '外观' },
    })
    expect(wrapper.find('.app-segmented').attributes('role')).toBe('radiogroup')
    expect(wrapper.find('.app-segmented').attributes('aria-label')).toBe('外观')
  })

  it('emits the clicked option value', async () => {
    const wrapper = mount(AppSegmented, { props: { modelValue: 'list', options: OPTIONS } })
    await wrapper.findAll('button.app-segmented__btn')[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0][0]).toBe('grid')
  })

  it('renders an icon inside the button when the option has one', () => {
    const wrapper = mount(AppSegmented, {
      props: {
        modelValue: 'a',
        options: [{ value: 'a', label: 'A', icon: 'grid' }],
      },
    })
    expect(wrapper.find('button .app-icon').exists()).toBe(true)
  })

  it('handles an empty options array', () => {
    const wrapper = mount(AppSegmented, { props: { modelValue: '', options: [] } })
    expect(wrapper.findAll('button.app-segmented__btn')).toHaveLength(0)
  })
})
