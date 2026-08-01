import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppTextField from '../AppTextField.vue'

describe('AppTextField', () => {
  it('renders an input with the model value', () => {
    const wrapper = mount(AppTextField, { props: { modelValue: 'hello' } })
    const input = wrapper.find('input.app-text-field__input')
    expect(input.exists()).toBe(true)
    expect((input.element as HTMLInputElement).value).toBe('hello')
  })

  it('renders the floating label', () => {
    const wrapper = mount(AppTextField, { props: { modelValue: '', label: '代理地址' } })
    expect(wrapper.find('.app-text-field__label').text()).toBe('代理地址')
  })

  it('omits the label when not provided', () => {
    const wrapper = mount(AppTextField, { props: { modelValue: '' } })
    expect(wrapper.find('.app-text-field__label').exists()).toBe(false)
  })

  it('emits update:modelValue on input', async () => {
    const wrapper = mount(AppTextField, { props: { modelValue: '' } })
    await wrapper.find('input').setValue('new text')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0][0]).toBe('new text')
  })

  it('applies the requested input type', () => {
    const wrapper = mount(AppTextField, { props: { modelValue: '', type: 'password' } })
    expect(wrapper.find('input').attributes('type')).toBe('password')
  })

  it('shows helperText when present and errorText absent', () => {
    const wrapper = mount(AppTextField, { props: { modelValue: '', helperText: '必填' } })
    expect(wrapper.find('.app-text-field__helper').text()).toBe('必填')
    expect(wrapper.find('.app-text-field').classes()).not.toContain('app-text-field--error')
  })

  it('shows errorText and applies the error class, helper shows error first', () => {
    const wrapper = mount(AppTextField, {
      props: { modelValue: '', helperText: '必填', errorText: '格式错误' },
    })
    expect(wrapper.find('.app-text-field').classes()).toContain('app-text-field--error')
    expect(wrapper.find('.app-text-field__helper').text()).toBe('格式错误')
  })

  it('clears the error class when errorText empties', async () => {
    const wrapper = mount(AppTextField, { props: { modelValue: '', errorText: 'x' } })
    expect(wrapper.find('.app-text-field').classes()).toContain('app-text-field--error')
    await wrapper.setProps({ errorText: '' })
    expect(wrapper.find('.app-text-field').classes()).not.toContain('app-text-field--error')
  })

  it('disables the input and applies the disabled class', () => {
    const wrapper = mount(AppTextField, { props: { modelValue: '', disabled: true } })
    expect(wrapper.find('input').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.app-text-field').classes()).toContain('app-text-field--disabled')
  })

  it('forwards maxlength', () => {
    const wrapper = mount(AppTextField, { props: { modelValue: '', maxlength: 20 } })
    expect(wrapper.find('input').attributes('maxlength')).toBe('20')
  })

  it('sets an accessible name from ariaLabel', () => {
    const wrapper = mount(AppTextField, { props: { modelValue: '', ariaLabel: '用户名' } })
    expect(wrapper.find('input').attributes('aria-label')).toBe('用户名')
  })

  it('falls back to the label for the accessible name', () => {
    const wrapper = mount(AppTextField, { props: { modelValue: '', label: '密码' } })
    expect(wrapper.find('input').attributes('aria-label')).toBe('密码')
  })
})
