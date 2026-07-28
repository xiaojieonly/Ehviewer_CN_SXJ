import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppIcon from '../AppIcon.vue'
import { iconNames, icons } from '@/assets/icons'

describe('AppIcon', () => {
  it('renders a known icon with SVG content', () => {
    const wrapper = mount(AppIcon, { props: { name: 'magnify-dark' } })
    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
    expect(svg.find('path').exists()).toBe(true)
  })

  it('renders multiple different icons', () => {
    for (const name of ['heart', 'download', 'settings-dark']) {
      const wrapper = mount(AppIcon, { props: { name } })
      expect(wrapper.find('svg').exists(), `icon "${name}" should render an <svg>`).toBe(true)
    }
  })

  it('applies the size prop to the wrapper span', () => {
    const wrapper = mount(AppIcon, { props: { name: 'heart', size: '48px' } })
    const span = wrapper.find('.app-icon')
    expect(span.attributes('style')).toContain('width: 48px')
    expect(span.attributes('style')).toContain('height: 48px')
  })

  it('defaults size to 24px', () => {
    const wrapper = mount(AppIcon, { props: { name: 'heart' } })
    const span = wrapper.find('.app-icon')
    expect(span.attributes('style')).toContain('width: 24px')
    expect(span.attributes('style')).toContain('height: 24px')
  })

  it('renders empty content for an unknown icon', () => {
    const wrapper = mount(AppIcon, { props: { name: 'nonexistent-icon-xyz' } })
    expect(wrapper.find('svg').exists()).toBe(false)
    // The wrapper span still exists for layout stability.
    expect(wrapper.find('.app-icon').exists()).toBe(true)
  })

  it('applies color override by replacing currentColor', () => {
    const wrapper = mount(AppIcon, {
      props: { name: 'magnify-dark', color: '#ff0000' },
    })
    const svgHtml = wrapper.find('.app-icon').html()
    expect(svgHtml).toContain('#ff0000')
    expect(svgHtml).not.toContain('currentColor')
  })

  it('preserves currentColor when no color prop is given', () => {
    const wrapper = mount(AppIcon, { props: { name: 'magnify-dark' } })
    const svgHtml = wrapper.find('.app-icon').html()
    expect(svgHtml).toContain('currentColor')
  })

  it('has a non-empty icon registry', () => {
    expect(iconNames.length).toBeGreaterThanOrEqual(70)
    // Every registered icon should have SVG content.
    for (const name of iconNames) {
      expect(icons[name], `icon "${name}" should have SVG content`).toBeTruthy()
    }
  })
})
