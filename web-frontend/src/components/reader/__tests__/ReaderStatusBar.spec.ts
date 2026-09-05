import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ReaderStatusBar from '../ReaderStatusBar.vue'

type BarProps = {
  currentPage: number
  totalPages: number
  visible: boolean
}

function factory(props: Partial<BarProps> = {}) {
  return mount(ReaderStatusBar, {
    props: { currentPage: 3, totalPages: 10, visible: true, ...props } as BarProps,
  })
}

const progressOf = (wrapper: ReturnType<typeof factory>) =>
  wrapper.find('.reader-status-bar__progress')

describe('ReaderStatusBar', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // Fixed wall clock: 2026-07-28 09:05 local
    vi.setSystemTime(new Date(2026, 6, 28, 9, 5, 0))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('page progress', () => {
    it('renders "N/M" like GalleryActivity.updateProgress()', () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10 })
      expect(progressOf(wrapper).text()).toBe('3/10')
    })

    it('tracks prop updates', async () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10 })
      await wrapper.setProps({ currentPage: 7 })
      expect(progressOf(wrapper).text()).toBe('7/10')
    })

    it('renders nothing for an empty gallery', () => {
      const wrapper = factory({ currentPage: 0, totalPages: 0 })
      expect(progressOf(wrapper).text()).toBe('')
    })
  })

  describe('visibility', () => {
    it('is shown without the hidden class when visible', () => {
      const wrapper = factory({ visible: true })
      expect(wrapper.classes()).not.toContain('reader-status-bar--hidden')
      expect(wrapper.attributes('aria-hidden')).toBe('false')
    })

    it('carries the hidden class when not visible', () => {
      const wrapper = factory({ visible: false })
      expect(wrapper.classes()).toContain('reader-status-bar--hidden')
      expect(wrapper.attributes('aria-hidden')).toBe('true')
    })

    it('toggles when the visible prop changes', async () => {
      const wrapper = factory({ visible: true })
      await wrapper.setProps({ visible: false })
      expect(wrapper.classes()).toContain('reader-status-bar--hidden')
      await wrapper.setProps({ visible: true })
      expect(wrapper.classes()).not.toContain('reader-status-bar--hidden')
    })
  })

  describe('auto-hide (HIDE_SLIDER_DELAY = 3000ms)', () => {
    // plan-2026-09-05 A6: the bar only NOTIFIES the idle timeout (`idle`);
    // the parent decides whether to hide or pause the countdown.
    it('emits idle after 3s of visibility', () => {
      const wrapper = factory({ visible: true })

      vi.advanceTimersByTime(2999)
      expect(wrapper.emitted('idle')).toBeUndefined()

      vi.advanceTimersByTime(1)
      expect(wrapper.emitted('idle')).toBeTruthy()
    })

    it('re-arms the countdown when the bar is re-shown', async () => {
      const wrapper = factory({ visible: true })

      vi.advanceTimersByTime(2000)
      await wrapper.setProps({ visible: false })
      await wrapper.setProps({ visible: true })

      // 2999ms after the re-show: still quiet.
      vi.advanceTimersByTime(2999)
      expect(wrapper.emitted('idle')).toBeUndefined()

      vi.advanceTimersByTime(1)
      expect(wrapper.emitted('idle')).toHaveLength(1)
    })

    it('re-arms the countdown via the exposed resetIdle() (mouse wake, A6)', async () => {
      const wrapper = factory({ visible: true })

      vi.advanceTimersByTime(2000)
      ;(wrapper.vm as unknown as { resetIdle: () => void }).resetIdle()

      // 2999ms after the re-arm: still quiet.
      vi.advanceTimersByTime(2999)
      expect(wrapper.emitted('idle')).toBeUndefined()

      vi.advanceTimersByTime(1)
      expect(wrapper.emitted('idle')).toHaveLength(1)
    })

    it('never auto-hides when mounted hidden', () => {
      const wrapper = factory({ visible: false })
      vi.advanceTimersByTime(60_000)
      expect(wrapper.emitted('idle')).toBeUndefined()
    })

    it('emits at most once per visible session', () => {
      const wrapper = factory({ visible: true })
      vi.advanceTimersByTime(10_000)
      expect(wrapper.emitted('idle')).toHaveLength(1)
    })
  })

  describe('clock', () => {
    it('renders the current time as HH:mm (24h)', () => {
      const wrapper = factory()
      expect(wrapper.find('.reader-status-bar__clock').text()).toBe('09:05')
    })

    it('ticks as minutes pass', async () => {
      const wrapper = factory()
      vi.setSystemTime(new Date(2026, 6, 28, 21, 37, 0))
      vi.advanceTimersByTime(1000)
      await nextTick() // let the interval's ref write re-render
      expect(wrapper.find('.reader-status-bar__clock').text()).toBe('21:37')
    })
  })

  describe('battery placeholder', () => {
    it('renders a battery glyph on the right', () => {
      const wrapper = factory()
      const battery = wrapper.find('.reader-status-bar__battery')
      expect(battery.exists()).toBe(true)
      expect(battery.find('svg').exists()).toBe(true)
      expect(battery.attributes('aria-label')).toBe('电量')
    })
  })
})
