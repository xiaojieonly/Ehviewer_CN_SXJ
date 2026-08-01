import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SectionHeader from '../SectionHeader.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

describe('SectionHeader', () => {
  it('renders the title inside an h2', () => {
    const wrapper = mount(SectionHeader, { props: { title: '通用' } })
    expect(wrapper.find('h2.section-header').exists()).toBe(true)
    expect(wrapper.find('.section-header').text()).toBe('通用')
  })

  it('renders the icon inline with the title (UX-11)', () => {
    const wrapper = mount(SectionHeader, { props: { title: '通用', icon: 'devices' } })
    const icon = wrapper.findComponent(AppIcon)
    expect(icon.exists()).toBe(true)
    expect(icon.props('name')).toBe('devices')
    expect(icon.props('size')).toBe('18px')
    expect(icon.classes()).toContain('section-header__icon')
  })

  it('omits the icon when not provided', () => {
    const wrapper = mount(SectionHeader, { props: { title: '通用' } })
    expect(wrapper.findComponent(AppIcon).exists()).toBe(false)
  })
})
