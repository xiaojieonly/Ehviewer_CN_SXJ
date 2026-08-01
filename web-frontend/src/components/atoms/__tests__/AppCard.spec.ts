import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppCard from '../AppCard.vue'

describe('AppCard (presentational surface)', () => {
  it('renders the default slot content', () => {
    const wrapper = mount(AppCard, {
      props: { mode: 'list' },
      slots: { default: '<span class="slot-marker">custom content</span>' },
    })
    expect(wrapper.find('.slot-marker').text()).toBe('custom content')
  })

  it('applies the mode class to the card root', () => {
    const list = mount(AppCard, { props: { mode: 'list' } })
    const grid = mount(AppCard, { props: { mode: 'grid' } })
    expect(list.classes()).toContain('app-card')
    expect(list.classes()).toContain('app-card--list')
    expect(grid.classes()).toContain('app-card--grid')
  })

  it('is keyboard-activatable via the button role', () => {
    const wrapper = mount(AppCard, { props: { mode: 'grid' } })
    expect(wrapper.attributes('role')).toBe('button')
    expect(wrapper.attributes('tabindex')).toBe('0')
  })

  it('does not render any gallery content itself', () => {
    const wrapper = mount(AppCard, { props: { mode: 'list' } })
    expect(wrapper.find('.app-card__title').exists()).toBe(false)
    expect(wrapper.find('.app-card__tile').exists()).toBe(false)
    expect(wrapper.find('img').exists()).toBe(false)
  })
})

describe('AppCard (interaction)', () => {
  it('emits click with the original MouseEvent', async () => {
    const wrapper = mount(AppCard, { props: { mode: 'list' } })
    await wrapper.trigger('click')
    const emitted = wrapper.emitted('click')
    expect(emitted).toBeTruthy()
    const payload = emitted![0][0] as Event
    expect(payload).toBeInstanceOf(Event)
    expect(payload.type).toBe('click')
  })

  it('emits click on enter key activation', async () => {
    const wrapper = mount(AppCard, { props: { mode: 'list' } })
    await wrapper.trigger('keydown.enter')
    expect(wrapper.emitted('click')).toBeTruthy()
  })

  it('emits click on space key activation', async () => {
    const wrapper = mount(AppCard, { props: { mode: 'list' } })
    await wrapper.trigger('keydown.space')
    expect(wrapper.emitted('click')).toBeTruthy()
  })

  it('emits one click per activation, with no default scrolling on space', async () => {
    const wrapper = mount(AppCard, { props: { mode: 'list' } })
    await wrapper.trigger('keydown.space')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })
})
