import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppSwitch from '../AppSwitch.vue'

describe('AppSwitch', () => {
  it('renders a role=switch button with the off state by default', () => {
    const wrapper = mount(AppSwitch, { props: { modelValue: false } })
    const button = wrapper.find('button.app-switch')
    expect(button.exists()).toBe(true)
    expect(button.attributes('role')).toBe('switch')
    expect(button.attributes('aria-checked')).toBe('false')
    expect(button.classes()).not.toContain('app-switch--on')
  })

  it('reflects the on state via aria-checked and class', () => {
    const wrapper = mount(AppSwitch, { props: { modelValue: true } })
    const button = wrapper.find('button.app-switch')
    expect(button.attributes('aria-checked')).toBe('true')
    expect(button.classes()).toContain('app-switch--on')
  })

  it('emits the inverted value on click', async () => {
    const wrapper = mount(AppSwitch, { props: { modelValue: false } })
    await wrapper.find('button.app-switch').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0][0]).toBe(true)
  })

  it('emits false when clicked while on', async () => {
    const wrapper = mount(AppSwitch, { props: { modelValue: true } })
    await wrapper.find('button.app-switch').trigger('click')
    expect(wrapper.emitted('update:modelValue')![0][0]).toBe(false)
  })

  it('passes ariaLabel through to the switch', () => {
    const wrapper = mount(AppSwitch, {
      props: { modelValue: false, ariaLabel: '跟随系统主题' },
    })
    expect(wrapper.find('button.app-switch').attributes('aria-label')).toBe('跟随系统主题')
  })

  it('disables interaction and applies the disabled class', async () => {
    const wrapper = mount(AppSwitch, { props: { modelValue: false, disabled: true } })
    const button = wrapper.find('button.app-switch')
    expect(button.classes()).toContain('app-switch--disabled')
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
  })

  it('renders the track and thumb spans', () => {
    const wrapper = mount(AppSwitch, { props: { modelValue: true } })
    expect(wrapper.find('.app-switch__track').exists()).toBe(true)
    expect(wrapper.find('.app-switch__thumb').exists()).toBe(true)
  })
})
