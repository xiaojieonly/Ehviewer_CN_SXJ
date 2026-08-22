import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { useSwipeGesture, type SwipeCallbacks } from '../useSwipeGesture'

type Point = { clientX: number; clientY: number }

/** Mount a host div wired to the composable and return the element + a dispatcher. */
function mountSwipe(callbacks: SwipeCallbacks) {
  const elRef = ref<HTMLElement | null>(null)
  const Host = defineComponent({
    setup() {
      useSwipeGesture(elRef, callbacks)
      return () => h('div', { ref: elRef, 'data-testid': 'surface' })
    },
  })
  const wrapper = mount(Host)
  const el = wrapper.find('[data-testid="surface"]').element as HTMLElement

  function fire(
    type: 'touchstart' | 'touchmove' | 'touchend',
    opts: { touches?: Point[]; changedTouches?: Point[] } = {},
  ) {
    const event = new Event(type) as Event & {
      touches?: unknown[]
      changedTouches?: unknown[]
    }
    Object.defineProperty(event, 'touches', { value: opts.touches ?? [] })
    Object.defineProperty(event, 'changedTouches', { value: opts.changedTouches ?? [] })
    el.dispatchEvent(event)
  }

  return { wrapper, fire }
}

const at = (x: number, y: number): Point => ({ clientX: x, clientY: y })

describe('useSwipeGesture (T-F2)', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('fires onSwipeLeft / onSwipeRight past the horizontal threshold', () => {
    const cbs: SwipeCallbacks = { onSwipeLeft: vi.fn(), onSwipeRight: vi.fn() }
    const { fire } = mountSwipe(cbs)

    fire('touchstart', { touches: [at(100, 100)] })
    fire('touchend', { changedTouches: [at(30, 105)] }) // dx=-70 dominant
    expect(cbs.onSwipeLeft).toHaveBeenCalledTimes(1)

    fire('touchstart', { touches: [at(100, 100)] })
    fire('touchend', { changedTouches: [at(180, 95)] }) // dx=+80 dominant
    expect(cbs.onSwipeRight).toHaveBeenCalledTimes(1)
  })

  it('fires onSwipeUp / onSwipeDown when the vertical delta dominates', () => {
    const cbs: SwipeCallbacks = { onSwipeUp: vi.fn(), onSwipeDown: vi.fn() }
    const { fire } = mountSwipe(cbs)

    fire('touchstart', { touches: [at(100, 200)] })
    fire('touchend', { changedTouches: [at(98, 120)] }) // dy=-80
    expect(cbs.onSwipeUp).toHaveBeenCalledTimes(1)

    fire('touchstart', { touches: [at(100, 200)] })
    fire('touchend', { changedTouches: [at(102, 290)] }) // dy=+90
    expect(cbs.onSwipeDown).toHaveBeenCalledTimes(1)
  })

  it('ignores gestures below the 50px threshold and ambiguous diagonals', () => {
    const cbs: SwipeCallbacks = {
      onSwipeLeft: vi.fn(),
      onSwipeRight: vi.fn(),
      onSwipeUp: vi.fn(),
      onSwipeDown: vi.fn(),
    }
    const { fire } = mountSwipe(cbs)

    // Small horizontal move.
    fire('touchstart', { touches: [at(100, 100)] })
    fire('touchend', { changedTouches: [at(70, 101)] })
    // |dx| == |dy| — neither axis dominates.
    fire('touchstart', { touches: [at(100, 100)] })
    fire('touchend', { changedTouches: [at(160, 160)] })

    for (const fn of Object.values(cbs)) expect(fn).not.toHaveBeenCalled()
  })

  it('ignores slow gestures (>500ms) — a long press is not a swipe', () => {
    vi.useFakeTimers()
    const cbs: SwipeCallbacks = { onSwipeLeft: vi.fn(), onSwipeRight: vi.fn() }
    const { fire } = mountSwipe(cbs)

    fire('touchstart', { touches: [at(200, 200)] })
    vi.advanceTimersByTime(501)
    fire('touchend', { changedTouches: [at(20, 200)] })

    expect(cbs.onSwipeLeft).not.toHaveBeenCalled()
  })

  it('reports pinch scale from two-finger touch moves after a two-finger start', () => {
    const onPinch = vi.fn()
    const { fire } = mountSwipe({ onPinch })

    // Two fingers 100px apart…
    fire('touchstart', { touches: [at(0, 0), at(100, 0)] })
    // …spread to 250px apart → scale 2.5.
    fire('touchmove', { touches: [at(0, 0), at(250, 0)] })
    expect(onPinch).toHaveBeenCalledWith(2.5)

    // A single-finger drag never pinches.
    onPinch.mockClear()
    fire('touchstart', { touches: [at(10, 10)] })
    fire('touchmove', { touches: [at(40, 40)] })
    expect(onPinch).not.toHaveBeenCalled()

    // A fresh two-finger start re-baselines the distance (no stale scaling).
    fire('touchstart', { touches: [at(0, 0), at(100, 0)] })
    fire('touchmove', { touches: [at(0, 0), at(200, 0)] })
    expect(onPinch).toHaveBeenLastCalledWith(2)
  })

  it('bails out cleanly when touchend carries no changed touches', () => {
    const cbs: SwipeCallbacks = { onSwipeLeft: vi.fn() }
    const { fire } = mountSwipe(cbs)

    fire('touchstart', { touches: [at(100, 100)] })
    expect(() => fire('touchend')).not.toThrow()
    expect(cbs.onSwipeLeft).not.toHaveBeenCalled()
  })
})
