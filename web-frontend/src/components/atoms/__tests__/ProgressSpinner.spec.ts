import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ProgressSpinner from '../ProgressSpinner.vue'

describe('ProgressSpinner', () => {
  it('applies the small size preset (16px token)', () => {
    const wrapper = mount(ProgressSpinner, { props: { size: 'small' } })
    expect(wrapper.classes()).toContain('progress-spinner--small')
    expect(wrapper.classes()).not.toContain('progress-spinner--large')
  })

  it('applies the large size preset (76px token)', () => {
    const wrapper = mount(ProgressSpinner, { props: { size: 'large' } })
    expect(wrapper.classes()).toContain('progress-spinner--large')
  })

  it('defaults to indeterminate mode', () => {
    const wrapper = mount(ProgressSpinner, { props: { size: 'small' } })
    expect(wrapper.classes()).toContain('progress-spinner--indeterminate')
    // Indeterminate progressbars expose no current value.
    expect(wrapper.attributes('aria-valuenow')).toBeUndefined()
  })

  it('exposes progressbar semantics', () => {
    const wrapper = mount(ProgressSpinner, { props: { size: 'large' } })
    expect(wrapper.attributes('role')).toBe('progressbar')
    expect(wrapper.attributes('aria-valuemin')).toBe('0')
    expect(wrapper.attributes('aria-valuemax')).toBe('100')
  })

  it('renders an SVG ring with a circle', () => {
    const wrapper = mount(ProgressSpinner, { props: { size: 'large' } })
    expect(wrapper.find('svg').exists()).toBe(true)
    expect(wrapper.find('circle').exists()).toBe(true)
  })

  it('uses the primary color token by default', () => {
    const wrapper = mount(ProgressSpinner, { props: { size: 'small' } })
    expect(wrapper.find('circle').attributes('style')).toContain('var(--color-primary)')
  })

  it('honors a custom color prop', () => {
    const wrapper = mount(ProgressSpinner, {
      props: { size: 'small', color: '#ff0000' },
    })
    expect(wrapper.find('circle').attributes('style')).toContain('#ff0000')
  })

  it('renders a determinate arc from the progress value', () => {
    const wrapper = mount(ProgressSpinner, {
      props: { size: 'large', indeterminate: false, progress: 0.5 },
    })
    expect(wrapper.classes()).toContain('progress-spinner--determinate')
    expect(wrapper.attributes('aria-valuenow')).toBe('50')
    // A stroke-dasharray is set to encode the half arc.
    expect(wrapper.find('circle').attributes('style')).toContain('stroke-dasharray')
  })

  it('clamps determinate progress into [0, 1]', () => {
    const over = mount(ProgressSpinner, {
      props: { size: 'small', indeterminate: false, progress: 5 },
    })
    expect(over.attributes('aria-valuenow')).toBe('100')

    const under = mount(ProgressSpinner, {
      props: { size: 'small', indeterminate: false, progress: -1 },
    })
    expect(under.attributes('aria-valuenow')).toBe('0')
  })
})
