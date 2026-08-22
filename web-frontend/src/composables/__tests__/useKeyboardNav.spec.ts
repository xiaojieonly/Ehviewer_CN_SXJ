import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { useKeyboardNav, type KeyNavCallbacks } from '../useKeyboardNav'

/** Currently mounted host — torn down afterEach so listeners never stack. */
let active: VueWrapper | undefined

/** Mount a throwaway host that installs the composable with the given callbacks. */
function mountNav(callbacks: KeyNavCallbacks) {
  const Host = defineComponent({
    setup() {
      useKeyboardNav(callbacks)
      return () => h('div')
    },
  })
  active = mount(Host)
  return active
}

/**
 * Dispatch a real keydown on window; report whether the composable
 * prevented the browser default (scroll / quick-find side effects).
 */
function press(key: string): boolean {
  const event = new KeyboardEvent('keydown', { key })
  const spy = vi.spyOn(event, 'preventDefault')
  window.dispatchEvent(event)
  return spy.mock.calls.length > 0
}

describe('useKeyboardNav (T-F2)', () => {
  afterEach(() => {
    active?.unmount()
    active = undefined
    vi.restoreAllMocks()
  })

  function makeCbs(): Required<Pick<KeyNavCallbacks, 'onPrev' | 'onNext' | 'onFirst' | 'onLast' | 'onToggleToolbar' | 'onZoomIn' | 'onZoomOut'>> {
    return {
      onPrev: vi.fn(),
      onNext: vi.fn(),
      onFirst: vi.fn(),
      onLast: vi.fn(),
      onToggleToolbar: vi.fn(),
      onZoomIn: vi.fn(),
      onZoomOut: vi.fn(),
    }
  }

  it('maps ArrowLeft/a to onPrev and ArrowRight/d to onNext, preventing the default', () => {
    const cbs = makeCbs()
    mountNav(cbs)

    expect(press('ArrowLeft')).toBe(true)
    expect(press('a')).toBe(true)
    expect(press('ArrowRight')).toBe(true)
    expect(press('d')).toBe(true)

    expect(cbs.onPrev).toHaveBeenCalledTimes(2)
    expect(cbs.onNext).toHaveBeenCalledTimes(2)
  })

  it('maps Home/End to onFirst/onLast and Escape to onToggleToolbar', () => {
    const cbs = makeCbs()
    mountNav(cbs)

    expect(press('Home')).toBe(true)
    expect(press('End')).toBe(true)
    expect(press('Escape')).toBe(true)

    expect(cbs.onFirst).toHaveBeenCalledTimes(1)
    expect(cbs.onLast).toHaveBeenCalledTimes(1)
    expect(cbs.onToggleToolbar).toHaveBeenCalledTimes(1)
  })

  it('pages with Space by default (paging implied)', () => {
    const cbs = makeCbs()
    mountNav(cbs)

    expect(press(' ')).toBe(true)
    expect(cbs.onNext).toHaveBeenCalledTimes(1)
    expect(cbs.onToggleToolbar).not.toHaveBeenCalled()
  })

  it('routes +/-/= to zoom callbacks', () => {
    const cbs = makeCbs()
    mountNav(cbs)

    expect(press('+')).toBe(true)
    expect(press('=')).toBe(true)
    expect(press('-')).toBe(true)

    expect(cbs.onZoomIn).toHaveBeenCalledTimes(2)
    expect(cbs.onZoomOut).toHaveBeenCalledTimes(1)
  })

  it('gates arrow/space paging behind pagingEnabled=false and Space degrades to toolbar toggle', () => {
    const cbs = makeCbs()
    mountNav({ ...cbs, pagingEnabled: () => false })

    // Gated keys are swallowed WITHOUT preventDefault — the page keeps its
    // native behavior (e.g. scrolling) when keyboard paging is off.
    expect(press('ArrowLeft')).toBe(false)
    expect(press('ArrowRight')).toBe(false)
    expect(press('a')).toBe(false)
    expect(press('d')).toBe(false)
    expect(cbs.onPrev).not.toHaveBeenCalled()
    expect(cbs.onNext).not.toHaveBeenCalled()

    // With paging off, Space toggles the chrome instead of flipping pages.
    expect(press(' ')).toBe(true)
    expect(cbs.onNext).not.toHaveBeenCalled()
    expect(cbs.onToggleToolbar).toHaveBeenCalledTimes(1)

    // Jump keys stay live regardless of the paging preference.
    expect(press('Home')).toBe(true)
    expect(press('End')).toBe(true)
    expect(cbs.onFirst).toHaveBeenCalledTimes(1)
    expect(cbs.onLast).toHaveBeenCalledTimes(1)
  })

  it('removes the listener on unmount', () => {
    const cbs = makeCbs()
    const wrapper = mountNav(cbs)

    expect(press('ArrowRight')).toBe(true)
    expect(cbs.onNext).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    expect(press('ArrowRight')).toBe(false)
    expect(cbs.onNext).toHaveBeenCalledTimes(1)
  })
})
