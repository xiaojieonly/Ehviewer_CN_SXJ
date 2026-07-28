import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import SeekBarPanel from '../SeekBarPanel.vue'

type PanelProps = {
  currentPage: number
  totalPages: number
  reversed?: boolean
}

function factory(props: Partial<PanelProps> = {}): VueWrapper<InstanceType<typeof SeekBarPanel>> {
  return mount(SeekBarPanel, {
    props: { currentPage: 3, totalPages: 10, ...props } as PanelProps,
  })
}

const sliderOf = (wrapper: VueWrapper<any>) => wrapper.find<HTMLInputElement>('input[type="range"]')
const leftLabel = (wrapper: VueWrapper<any>) => wrapper.find('.seekbar-panel__label--left')
const rightLabel = (wrapper: VueWrapper<any>) => wrapper.find('.seekbar-panel__label--right')

describe('SeekBarPanel', () => {
  describe('page labels', () => {
    it('shows the current page on the left and the total on the right (LTR)', () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10 })
      expect(leftLabel(wrapper).text()).toBe('3')
      expect(rightLabel(wrapper).text()).toBe('10')
    })

    it('tracks prop updates', async () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10 })
      await wrapper.setProps({ currentPage: 7 })
      expect(leftLabel(wrapper).text()).toBe('7')
      expect(rightLabel(wrapper).text()).toBe('10')
    })

    it('swaps the labels in reversed (RTL) mode — total left, current right', () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10, reversed: true })
      expect(leftLabel(wrapper).text()).toBe('10')
      expect(rightLabel(wrapper).text()).toBe('3')
    })

    it('updates the start label live while scrubbing, before the prop round-trips', async () => {
      // GalleryActivity.onProgressChanged(fromUser): the start label follows
      // the seek bar, not the (still uncommitted) current page.
      const wrapper = factory({ currentPage: 3, totalPages: 10 })
      const slider = sliderOf(wrapper)

      await slider.trigger('pointerdown')
      slider.element.value = '4'
      await slider.trigger('input')

      expect(leftLabel(wrapper).text()).toBe('5')
      expect(rightLabel(wrapper).text()).toBe('10')
    })

    it('updates the RIGHT label live while scrubbing in reversed mode', async () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10, reversed: true })
      const slider = sliderOf(wrapper)

      await slider.trigger('pointerdown')
      slider.element.value = '8'
      await slider.trigger('input')

      expect(rightLabel(wrapper).text()).toBe('9')
      expect(leftLabel(wrapper).text()).toBe('10')
    })
  })

  describe('slider mapping (0-based internally, 1-based contract)', () => {
    it('maps max = totalPages - 1 and value = currentPage - 1', () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10 })
      const slider = sliderOf(wrapper)
      expect(slider.attributes('max')).toBe('9')
      expect(slider.element.value).toBe('2')
    })

    it('clamps an out-of-range currentPage into [1, totalPages]', () => {
      const over = factory({ currentPage: 99, totalPages: 10 })
      expect(sliderOf(over).element.value).toBe('9')
      expect(leftLabel(over).text()).toBe('10')

      const under = factory({ currentPage: -5, totalPages: 10 })
      expect(sliderOf(under).element.value).toBe('0')
      expect(leftLabel(under).text()).toBe('1')
    })

    it('handles a single-page gallery (max 0) without NaN', () => {
      const wrapper = factory({ currentPage: 1, totalPages: 1 })
      const slider = sliderOf(wrapper)
      expect(slider.attributes('max')).toBe('0')
      expect(slider.element.value).toBe('0')
      expect(leftLabel(wrapper).text()).toBe('1')
      expect(rightLabel(wrapper).text()).toBe('1')
    })

    it('exposes the fill percentage as a CSS custom property', () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10 })
      const style = sliderOf(wrapper).attributes('style') ?? ''
      // (3 - 1) / (10 - 1) = 22.22…%
      expect(style).toContain('--seekbar-fill: 22.22')
    })
  })

  describe('emits', () => {
    it('emits update:currentPage (1-based) on slider input', async () => {
      const wrapper = factory()
      const slider = sliderOf(wrapper)

      await slider.trigger('pointerdown')
      slider.element.value = '4'
      await slider.trigger('input')

      const emitted = wrapper.emitted('update:currentPage')
      expect(emitted).toBeTruthy()
      expect(emitted![emitted!.length - 1]).toEqual([5])
    })

    it('emits the full drag lifecycle: seek-start → update → seek-end + change', async () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10 })
      const slider = sliderOf(wrapper)

      await slider.trigger('pointerdown')
      slider.element.value = '6'
      await slider.trigger('input')
      await slider.trigger('change') // native commit on pointer release

      expect(wrapper.emitted('seek-start')).toHaveLength(1)
      expect(wrapper.emitted('update:currentPage')!.pop()).toEqual([7])
      expect(wrapper.emitted('seek-end')).toHaveLength(1)
      expect(wrapper.emitted('change')!.pop()).toEqual([7])
    })

    it('emits seek-end without change when pressed and released without moving', async () => {
      const wrapper = factory()
      const slider = sliderOf(wrapper)

      await slider.trigger('pointerdown')
      await slider.trigger('pointerup')

      expect(wrapper.emitted('seek-start')).toHaveLength(1)
      expect(wrapper.emitted('seek-end')).toHaveLength(1)
      expect(wrapper.emitted('change')).toBeUndefined()
    })

    it('treats keyboard adjustment as a commit without seek lifecycle events', async () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10 })
      const slider = sliderOf(wrapper)

      // Arrow keys fire input + change with no pointer involvement.
      slider.element.value = '3'
      await slider.trigger('input')
      await slider.trigger('change')

      expect(wrapper.emitted('update:currentPage')!.pop()).toEqual([4])
      expect(wrapper.emitted('change')!.pop()).toEqual([4])
      expect(wrapper.emitted('seek-start')).toBeUndefined()
      expect(wrapper.emitted('seek-end')).toBeUndefined()
    })

    it('ends the session on pointercancel without committing', async () => {
      const wrapper = factory()
      const slider = sliderOf(wrapper)

      await slider.trigger('pointerdown')
      slider.element.value = '5'
      await slider.trigger('input')
      await slider.trigger('pointercancel')

      expect(wrapper.emitted('seek-end')).toHaveLength(1)
      expect(wrapper.emitted('change')).toBeUndefined()
    })
  })

  describe('reversed (RTL) slider', () => {
    it('applies the mirroring class to the range input', () => {
      const ltr = factory({ reversed: false })
      expect(sliderOf(ltr).classes()).not.toContain('seekbar-panel__slider--reversed')

      const rtl = factory({ reversed: true })
      expect(sliderOf(rtl).classes()).toContain('seekbar-panel__slider--reversed')
      expect(rtl.find('.seekbar-panel--reversed').exists()).toBe(true)
    })

    it('keeps the same value mapping when reversed', () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10, reversed: true })
      const slider = sliderOf(wrapper)
      expect(slider.attributes('max')).toBe('9')
      expect(slider.element.value).toBe('2')
    })

    it('emits normally while reversed', async () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10, reversed: true })
      const slider = sliderOf(wrapper)

      await slider.trigger('pointerdown')
      slider.element.value = '0'
      await slider.trigger('input')
      await slider.trigger('change')

      expect(wrapper.emitted('update:currentPage')!.pop()).toEqual([1])
      expect(wrapper.emitted('change')!.pop()).toEqual([1])
    })
  })

  describe('accessibility', () => {
    it('exposes page context on the slider', () => {
      const wrapper = factory({ currentPage: 3, totalPages: 10 })
      const slider = sliderOf(wrapper)
      expect(slider.attributes('aria-valuetext')).toBe('3 / 10')
      expect(slider.attributes('aria-label')).toContain('3 of 10')
    })

    it('is a labelled group', () => {
      const wrapper = factory()
      expect(wrapper.attributes('role')).toBe('group')
      expect(wrapper.attributes('aria-label')).toBeTruthy()
    })
  })
})
