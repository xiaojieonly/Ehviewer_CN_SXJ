import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { useKeyboardNav, type KeyNavCallbacks } from '../useKeyboardNav'

/** Mount a throwaway host that installs the composable with the given callbacks. */
function mountNav(callbacks: KeyNavCallbacks) {
  const Host = defineComponent({
    setup() {
      useKeyboardNav(callbacks)
      return () => h('div')
    },
  })
  return mount(Host)
}

/** Dispatch a real keydown on window and return the preventDefault spy. */
function press(key: string): ReturnType<typeof vi.fn> {
  const event = new KeyboardEvent('keydown', { key })
  const spy = vi.spyOn(event, 'preventDefault')
  window.dispatchEvent(event)
  return spy
}

describe('useKeyboardNav (T-F2)', () => {
  afterEach(() => {
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

    expect(press('ArrowLeft')).toHaveBeenCalled()
    expect(press('a')).toHaveBeenCalled()
    expect(press('ArrowRight')).toHaveBeenCalled()
    expect(press('d')).toHaveBeenCalled()

    expect(cbs.onPrev).toHaveBeenCalledTimes(2)
    expect(cbs.onNext).toHaveBeenCalledTimes(2)
  })

  it('maps Home/End to onFirst/onLast and Escape to onToggleToolbar', () => {
    const cbs = makeCbs()
    mountNav(cbs)

    press('Home')
    press('End')
    press('Escape')

    expect(cbs.onFirst).toHaveBeenCalledTimes(1)
    expect(cbs.onLast).toHaveBeenCalledTimes(1)
    expect(cbs.onToggleToolbar).toHaveBeenCalledTimes(1)
  })

  it('pages with Space by default (paging implied)', () => {
    const cbs = makeCbs()
    mountNav(cbs)

    press(' ')
    expect(cbs.onNext).toHaveBeenCalledTimes(1)
    expect(cbs.onToggleToolbar).not.toHaveBeenCalled()
  })

  it('routes +/-/= to zoom callbacks', () => {
    const cbs = makeCbs()
    mountNav(cbs)

    press('+')
    press('=')
    press('-')

    expect(cbs.onZoomIn).toHaveBeenCalledTimes(2)
    expect(cbs.onZoomOut).toHaveBeenCalledTimes(1)
  })

  it('gates arrow/space paging behind pagingEnabled=false and Space degrades to toolbar toggle', () => {
    const cbs = makeCbs()
    mountNav({ ...cbs, pagingEnabled: () => false })

    press('ArrowLeft')
    press('ArrowRight')
    press('a')
    press('d')
    expect(cbs.onPrev).not.toHaveBeenCalled()
    expect(cbs.onNext).not.toHaveBeenCalled()

    // With paging off, Space toggles the chrome instead of flipping pages.
    press(' ')
    expect(cbs.onNext).not.toHaveBeenCalled()
    expect(cbs.onToggleToolbar).toHaveBeenCalledTimes(1)

    // Jump keys stay live regardless of the paging preference.
    press('Home')
    press('End')
    expect(cbs.onFirst).toHaveBeenCalledTimes(1)
    expect(cbs.onLast).toHaveBeenCalledTimes(1)
  })

  it('removes the listener on unmount', () => {
    const cbs = makeCbs()
    const wrapper = mountNav(cbs)

    press('ArrowRight')
    expect(cbs.onNext).toHaveBeenCalledTimes(1)

    wrapper.unmount()
    press('ArrowRight')
    expect(cbs.onNext).toHaveBeenCalledTimes(1)
  })
})
