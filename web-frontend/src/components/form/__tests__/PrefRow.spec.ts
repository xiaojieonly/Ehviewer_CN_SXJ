import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PrefRow from '../PrefRow.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

describe('PrefRow', () => {
  it('renders title and summary in the stacked text block', () => {
    const wrapper = mount(PrefRow, { props: { title: '外观', summary: '当前：亮色' } })
    expect(wrapper.find('.pref-row').exists()).toBe(true)
    expect(wrapper.find('.pref-row__title').text()).toBe('外观')
    expect(wrapper.find('.pref-row__summary').text()).toBe('当前：亮色')
  })

  it('renders the leading icon when `icon` is set', () => {
    const wrapper = mount(PrefRow, { props: { title: '外观', icon: 'settings-dark' } })
    const icon = wrapper.findComponent(AppIcon)
    expect(icon.exists()).toBe(true)
    expect(icon.props('name')).toBe('settings-dark')
    expect(icon.classes()).toContain('pref-row__icon')
  })

  it('omits the icon when `icon` is not set', () => {
    const wrapper = mount(PrefRow, { props: { title: '外观' } })
    expect(wrapper.findComponent(AppIcon).exists()).toBe(false)
  })

  it('omits the summary block when `summary` is not set', () => {
    const wrapper = mount(PrefRow, { props: { title: '外观' } })
    expect(wrapper.find('.pref-row__summary').exists()).toBe(false)
  })

  it('renders default-slot content into the control area', () => {
    const wrapper = mount(PrefRow, {
      props: { title: '外观' },
      slots: { default: '<button class="custom-control">x</button>' },
    })
    expect(wrapper.find('.pref-row__control .custom-control').exists()).toBe(true)
  })

  it('renders the below slot full-width when provided', () => {
    const wrapper = mount(PrefRow, {
      props: { title: '外观' },
      slots: { below: '<div class="custom-below">extra</div>' },
    })
    expect(wrapper.find('.pref-row__below .custom-below').exists()).toBe(true)
  })

  it('hides the below slot when empty', () => {
    const wrapper = mount(PrefRow, { props: { title: '外观' } })
    expect(wrapper.find('.pref-row__below').exists()).toBe(false)
  })

  it('applies the disabled class when `disabled` is true', async () => {
    const wrapper = mount(PrefRow, { props: { title: '外观' } })
    expect(wrapper.find('.pref-row').classes()).not.toContain('pref-row--disabled')
    await wrapper.setProps({ disabled: true })
    expect(wrapper.find('.pref-row').classes()).toContain('pref-row--disabled')
  })
})
