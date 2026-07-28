import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import FastScroller from '../FastScroller.vue'

/** happy-dom reports 0 for layout metrics — stub them per test. */
function mockScrollMetrics(el: HTMLElement, scrollHeight: number, clientHeight: number): void {
  Object.defineProperty(el, 'scrollHeight', { configurable: true, value: scrollHeight })
  Object.defineProperty(el, 'clientHeight', { configurable: true, value: clientHeight })
}

describe('FastScroller', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders slot content inside its scrollable container', () => {
    const wrapper = mount(FastScroller, {
      slots: { default: '<ul class="list"><li>a</li></ul>' },
    })
    expect(wrapper.find('.fast-scroller__container .list').exists()).toBe(true)
  })

  it('keeps the thumb hidden while idle', () => {
    const wrapper = mount(FastScroller, { slots: { default: '<p>x</p>' } })
    expect(wrapper.classes()).not.toContain('is-visible')
  })

  it('shows the thumb on scroll and sizes it proportionally', async () => {
    const wrapper = mount(FastScroller, { slots: { default: '<p>x</p>' } })
    const container = wrapper.find('.fast-scroller__container')
    const el = container.element as HTMLElement
    mockScrollMetrics(el, 2000, 500)
    el.scrollTop = 250
    await container.trigger('scroll')

    expect(wrapper.classes()).toContain('is-visible')

    const thumb = wrapper.find('.fast-scroller__thumb')
    // thumb height = max(500 * 500 / 2000, 32) = 125px
    expect(thumb.attributes('style')).toContain('height: 125px')
    // ratio = 250 / 1500 = 1/6 → top = 1/6 * (500 - 125) = 62.5px
    expect(thumb.attributes('style')).toContain('translateY(62.5px)')
  })

  it('fades the thumb out after the auto-hide delay', async () => {
    const wrapper = mount(FastScroller, {
      props: { autoHideDelay: 1500 },
      slots: { default: '<p>x</p>' },
    })
    const container = wrapper.find('.fast-scroller__container')
    const el = container.element as HTMLElement
    mockScrollMetrics(el, 2000, 500)
    el.scrollTop = 100
    await container.trigger('scroll')
    expect(wrapper.classes()).toContain('is-visible')

    vi.advanceTimersByTime(1600)
    await nextTick()
    expect(wrapper.classes()).not.toContain('is-visible')
  })

  it('re-arms the hide timer on consecutive scrolls', async () => {
    const wrapper = mount(FastScroller, {
      props: { autoHideDelay: 1500 },
      slots: { default: '<p>x</p>' },
    })
    const container = wrapper.find('.fast-scroller__container')
    const el = container.element as HTMLElement
    mockScrollMetrics(el, 2000, 500)

    el.scrollTop = 100
    await container.trigger('scroll')
    vi.advanceTimersByTime(1000)
    el.scrollTop = 200
    await container.trigger('scroll')
    // 1000ms after the FIRST scroll, but only 0ms after the second.
    vi.advanceTimersByTime(600)
    await nextTick()
    expect(wrapper.classes()).toContain('is-visible')

    vi.advanceTimersByTime(1000)
    await nextTick()
    expect(wrapper.classes()).not.toContain('is-visible')
  })

  it('collapses the thumb when content does not overflow', async () => {
    const wrapper = mount(FastScroller, { slots: { default: '<p>x</p>' } })
    const container = wrapper.find('.fast-scroller__container')
    const el = container.element as HTMLElement
    mockScrollMetrics(el, 400, 500)
    await container.trigger('scroll')

    const thumb = wrapper.find('.fast-scroller__thumb')
    expect(thumb.attributes('style')).toContain('height: 0px')
  })

  it('exposes the scroll container for composite layouts', () => {
    const wrapper = mount(FastScroller, { slots: { default: '<p>x</p>' } })
    const exposed = wrapper.vm as unknown as { containerRef: HTMLElement | null }
    expect(exposed.containerRef).toBeInstanceOf(HTMLElement)
    expect(exposed.containerRef?.classList.contains('fast-scroller__container')).toBe(true)
  })

  it('renders the bubble only when showBubble is enabled', async () => {
    const withoutBubble = mount(FastScroller, { slots: { default: '<p>x</p>' } })
    expect(withoutBubble.find('.fast-scroller__bubble').exists()).toBe(false)

    const withBubble = mount(FastScroller, {
      props: { showBubble: true },
      slots: { default: '<p>x</p>' },
    })
    expect(withBubble.find('.fast-scroller__bubble').exists()).toBe(true)
  })
})
