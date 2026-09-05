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
  // Attached so element.focus() really moves document.activeElement (the A1
  // form-target guard inspects the event target, i.e. the focused element).
  active = mount(Host, { attachTo: document.body })
  return active
}

/**
 * Dispatch a real keydown; report whether the composable prevented the
 * browser default (scroll / quick-find side effects). The event is dispatched
 * on the focused element (like a real keyboard) and bubbles up to the
 * composable's window listener — `e.target` is therefore the focused control,
 * which is exactly what the A1 form-target guard inspects.
 */
function press(key: string, init: KeyboardEventInit = {}): boolean {
  const event = new KeyboardEvent('keydown', { key, bubbles: true, ...init })
  const spy = vi.spyOn(event, 'preventDefault')
  const target: EventTarget = document.activeElement ?? window
  target.dispatchEvent(event)
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

  it("accepts SHIFT-chorded single-letter aliases (A/D still page)", async () => {
    // A1: 'a'/'d' match case-insensitively (via e.key.toLowerCase()).
    const cbs = makeCbs()
    mountNav(cbs)

    expect(press('A')).toBe(true)
    expect(press('D')).toBe(true)
    expect(cbs.onPrev).toHaveBeenCalledTimes(1)
    expect(cbs.onNext).toHaveBeenCalledTimes(1)
  })

  it('never intercepts Ctrl/Alt/Meta chords (A1)', () => {
    const cbs = makeCbs()
    mountNav(cbs)

    expect(press('a', { ctrlKey: true })).toBe(false)
    expect(press('d', { metaKey: true })).toBe(false)
    expect(press('ArrowLeft', { altKey: true })).toBe(false)
    expect(press('+', { ctrlKey: true })).toBe(false)
    expect(press('-', { metaKey: true })).toBe(false)
    expect(cbs.onPrev).not.toHaveBeenCalled()
    expect(cbs.onNext).not.toHaveBeenCalled()
    expect(cbs.onZoomIn).not.toHaveBeenCalled()
    expect(cbs.onZoomOut).not.toHaveBeenCalled()
  })

  it('ignores keys while an IME composition is in flight (A1)', () => {
    const cbs = makeCbs()
    mountNav(cbs)

    expect(press('a', { isComposing: true })).toBe(false)
    expect(press(' ', { isComposing: true })).toBe(false)
    expect(press('Escape', { isComposing: true })).toBe(false)
    expect(cbs.onPrev).not.toHaveBeenCalled()
    expect(cbs.onNext).not.toHaveBeenCalled()
    expect(cbs.onToggleToolbar).not.toHaveBeenCalled()
  })

  it('leaves focused form controls their native keys (A1)', () => {
    const cbs = makeCbs()
    const wrapper = mountNav(cbs)

    // Baseline: with nothing focused (activeElement = body) paging still works.
    expect(press('ArrowLeft')).toBe(true)
    expect(cbs.onPrev).toHaveBeenCalledTimes(1)

    for (const tag of ['input', 'textarea', 'select']) {
      const el = document.createElement(tag)
      wrapper.element.appendChild(el)
      el.focus()
      expect(press('ArrowLeft')).toBe(false)
      expect(press('ArrowRight')).toBe(false)
      expect(press(' ')).toBe(false)
      expect(press('a')).toBe(false)
      el.remove()
    }
    expect(cbs.onPrev).toHaveBeenCalledTimes(1)
    expect(cbs.onNext).not.toHaveBeenCalled()
  })

  it('keeps Space activating a focused button (A1)', () => {
    const cbs = makeCbs()
    const wrapper = mountNav(cbs)
    const button = document.createElement('button')
    wrapper.element.appendChild(button)
    button.focus()

    expect(press(' ')).toBe(false)
    expect(cbs.onNext).not.toHaveBeenCalled()
    expect(cbs.onToggleToolbar).not.toHaveBeenCalled()
    button.remove()
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
