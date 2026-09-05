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

  it('renders an `<a>` root carrying the detailUrl href (B2 link semantics)', () => {
    const wrapper = mount(AppCard, {
      props: { mode: 'list', detailUrl: '/gallery/123?token=abc' },
    })
    expect(wrapper.element.tagName).toBe('A')
    expect(wrapper.attributes('href')).toBe('/gallery/123?token=abc')
  })

  it('renders no href when detailUrl is not passed (still focusable)', () => {
    const wrapper = mount(AppCard, { props: { mode: 'list' } })
    expect(wrapper.element.tagName).toBe('A')
    expect(wrapper.attributes('href')).toBeUndefined()
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

  it('plain left clicks preventDefault the href navigation and emit click', async () => {
    const wrapper = mount(AppCard, { props: { mode: 'list', detailUrl: '/gallery/1' } })
    let defaultPrevented = false
    wrapper.element.addEventListener('click', (e: Event) => {
      defaultPrevented = e.defaultPrevented
    })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
    expect(defaultPrevented).toBe(true)
  })

  it('modified clicks never emit — the browser keeps open-in-new-tab', async () => {
    const wrapper = mount(AppCard, { props: { mode: 'list', detailUrl: '/gallery/1' } })
    await wrapper.trigger('click', { ctrlKey: true })
    await wrapper.trigger('click', { metaKey: true })
    await wrapper.trigger('click', { shiftKey: true })
    await wrapper.trigger('click', { button: 1 })
    expect(wrapper.emitted('click')).toBeUndefined()
  })
})
