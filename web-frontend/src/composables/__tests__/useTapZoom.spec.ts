import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { useTapZoom, hasFineHoverPointer } from '../useTapZoom'

/** Currently mounted host — torn down afterEach so listeners never stack. */
let active: VueWrapper | undefined

interface HostOptions {
  desktop: boolean
  withZoom?: boolean
  suppressed?: () => boolean
}

/** Mount a throwaway host with a tappable stage and the given environment. */
function mountTap(options: HostOptions, callbacks: { single: () => void; zoom: () => void }) {
  const el = ref<HTMLElement | null>(null)
  const Host = defineComponent({
    setup() {
      useTapZoom(el, {
        onSingleTap: callbacks.single,
        ...(options.withZoom ? { onDoubleTap: callbacks.zoom } : {}),
        ...(options.suppressed ? { suppressed: options.suppressed } : {}),
      })
      return () => h('div', { ref: el, 'data-testid': 'stage' })
    },
  })
  active = mount(Host, { attachTo: document.body })
  return active.get('[data-testid="stage"]')
}

/**
 * Dispatch a click on the stage. `pointerType` is undefined on plain
 * MouseEvents (old engines) — that path falls back to the matchMedia query.
 */
function click(stage: ReturnType<typeof mountTap>, pointerType?: string): void {
  const event = new MouseEvent('click', { bubbles: true, clientX: 10, clientY: 10 })
  if (pointerType) {
    Object.defineProperty(event, 'pointerType', { value: pointerType })
  }
  stage.element.dispatchEvent(event)
}

function dblClick(stage: ReturnType<typeof mountTap>, pointerType?: string): void {
  const event = new MouseEvent('dblclick', { bubbles: true })
  if (pointerType) {
    Object.defineProperty(event, 'pointerType', { value: pointerType })
  }
  stage.element.dispatchEvent(event)
}

describe('useTapZoom (plan-2026-09-05 A5)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    active?.unmount()
    active = undefined
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  describe('desktop (mouse pointer)', () => {
    beforeEach(() => {
      vi.spyOn(window, 'matchMedia').mockImplementation(
        ((query: string) => ({
          matches: query.includes('pointer: fine'),
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })) as unknown as typeof window.matchMedia,
      )
    })

    it('acts on every click immediately — no 280ms judgment window', () => {
      const single = vi.fn()
      const stage = mountTap({ desktop: true, withZoom: true }, { single, zoom: vi.fn() })

      click(stage, 'mouse')
      expect(single).toHaveBeenCalledTimes(1)

      click(stage, 'mouse')
      expect(single).toHaveBeenCalledTimes(2)

      // No deferred timer side effects either.
      vi.advanceTimersByTime(1000)
      expect(single).toHaveBeenCalledTimes(2)
    })

    it('cycles zoom on the native dblclick', () => {
      const zoom = vi.fn()
      const stage = mountTap({ desktop: true, withZoom: true }, { single: vi.fn(), zoom })

      dblClick(stage, 'mouse')
      expect(zoom).toHaveBeenCalledTimes(1)
    })

    it('ignores dblclick synthesized from touch double-taps', () => {
      const zoom = vi.fn()
      const stage = mountTap({ desktop: true, withZoom: true }, { single: vi.fn(), zoom })

      dblClick(stage, 'touch')
      expect(zoom).not.toHaveBeenCalled()
    })

    it('single taps yield to the panning state (dblclick zoom-out still works)', () => {
      const single = vi.fn()
      const zoom = vi.fn()
      const stage = mountTap(
        { desktop: true, withZoom: true, suppressed: () => true },
        { single, zoom },
      )

      click(stage, 'mouse')
      expect(single).not.toHaveBeenCalled()

      dblClick(stage, 'mouse')
      expect(zoom).toHaveBeenCalledTimes(1)
    })

    it('swallows clicks right after suppressTaps (post-gesture)', () => {
      const single = vi.fn()
      let handle!: ReturnType<typeof useTapZoom>
      const Host = defineComponent({
        setup() {
          const el = ref<HTMLElement | null>(null)
          handle = useTapZoom(el, { onSingleTap: single })
          return () => h('div', { ref: el, 'data-testid': 'stage' })
        },
      })
      active = mount(Host, { attachTo: document.body })
      const stage = active.get('[data-testid="stage"]')

      handle.suppressTaps()
      click(stage, 'mouse')
      expect(single).not.toHaveBeenCalled()

      vi.advanceTimersByTime(500)
      click(stage, 'mouse')
      expect(single).toHaveBeenCalledTimes(1)
    })
  })

  describe('touch', () => {
    beforeEach(() => {
      vi.spyOn(window, 'matchMedia').mockImplementation(
        ((query: string) => ({
          matches: false,
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })) as unknown as typeof window.matchMedia,
      )
    })

    it('resolves a single tap after the (240ms) window', () => {
      const single = vi.fn()
      const stage = mountTap({ desktop: false, withZoom: true }, { single, zoom: vi.fn() })

      click(stage)
      expect(single).not.toHaveBeenCalled()

      vi.advanceTimersByTime(239)
      expect(single).not.toHaveBeenCalled()

      vi.advanceTimersByTime(1)
      expect(single).toHaveBeenCalledTimes(1)
    })

    it('fires the zoom cycle once for a touch double-tap', () => {
      const zoom = vi.fn()
      const single = vi.fn()
      const stage = mountTap({ desktop: false, withZoom: true }, { single, zoom })

      click(stage)
      vi.advanceTimersByTime(100)
      click(stage)

      expect(zoom).toHaveBeenCalledTimes(1)
      vi.advanceTimersByTime(1000)
      expect(single).not.toHaveBeenCalled()
    })

    it('never arms two timers for rapid taps without zoom (double-turn bug)', () => {
      const single = vi.fn()
      const stage = mountTap({ desktop: false }, { single, zoom: vi.fn() })

      click(stage)
      vi.advanceTimersByTime(100)
      click(stage)
      vi.advanceTimersByTime(240)
      expect(single).toHaveBeenCalledTimes(1)

      // A third tap after the dust settled acts on its own.
      click(stage)
      vi.advanceTimersByTime(240)
      expect(single).toHaveBeenCalledTimes(2)
    })

    it('still zooms on double-tap while the panning state is active', () => {
      const zoom = vi.fn()
      const single = vi.fn()
      const stage = mountTap(
        { desktop: false, withZoom: true, suppressed: () => true },
        { single, zoom },
      )

      click(stage)
      vi.advanceTimersByTime(100)
      click(stage)

      expect(zoom).toHaveBeenCalledTimes(1)
      vi.advanceTimersByTime(1000)
      expect(single).not.toHaveBeenCalled()
    })

    it('discards the pending tap on unmount', () => {
      const single = vi.fn()
      const stage = mountTap({ desktop: false }, { single, zoom: vi.fn() })

      click(stage)
      active?.unmount()
      active = undefined

      vi.advanceTimersByTime(1000)
      expect(single).not.toHaveBeenCalled()
    })
  })

  describe('hasFineHoverPointer', () => {
    it('is false when the environment lacks a fine pointer', () => {
      vi.spyOn(window, 'matchMedia').mockImplementation(
        (() => ({
          matches: false,
          media: '',
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })) as unknown as typeof window.matchMedia,
      )
      expect(hasFineHoverPointer()).toBe(false)
    })

    it('is true for (hover: hover) and (pointer: fine)', () => {
      vi.spyOn(window, 'matchMedia').mockImplementation(
        ((query: string) => ({
          matches: query === '(hover: hover) and (pointer: fine)',
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })) as unknown as typeof window.matchMedia,
      )
      expect(hasFineHoverPointer()).toBe(true)
    })
  })
})
