import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { useEdgeBackGesture } from '../useEdgeBackGesture'

/** Currently mounted host — torn down afterEach so listeners never stack. */
let active: VueWrapper | undefined

/**
 * Mount a host div (300×100 at a fixed position) that installs the
 * composable; return the host element for touch dispatching.
 */
function mountGesture(onBack: () => void): HTMLElement {
  let hostEl: HTMLElement | null = null
  const Host = defineComponent({
    setup() {
      const el = ref<HTMLElement | null>(null)
      useEdgeBackGesture(el, { onBack })
      return () =>
        h('div', {
          ref: el,
          style: 'position:fixed;left:0;top:0;width:300px;height:100px;',
        })
    },
  })
  active = mount(Host, { attachTo: document.body })
  hostEl = active.element as HTMLElement
  return hostEl
}

function touch(target: EventTarget, type: string, x: number, y: number): boolean {
  const t = new Touch({ identifier: 1, target: target as Element, clientX: x, clientY: y })
  const event = new TouchEvent(type, {
    bubbles: true,
    cancelable: true,
    touches: type === 'touchend' ? [] : [t],
    targetTouches: type === 'touchend' ? [] : [t],
    changedTouches: [t],
  })
  const spy = vi.spyOn(event, 'preventDefault')
  target.dispatchEvent(event)
  return spy.mock.calls.length > 0
}

describe('useEdgeBackGesture（Android 系统返回手势等价）', () => {
  afterEach(() => {
    active?.unmount()
    active = undefined
    vi.restoreAllMocks()
  })

  it('fires onBack after dragging inward past the exit distance (left edge)', () => {
    const onBack = vi.fn()
    const el = mountGesture(onBack)

    touch(el, 'touchstart', 10, 50)
    touch(el, 'touchmove', 60, 50) // 50px 进度
    touch(el, 'touchmove', 120, 50) // 110px ≥ 阈值
    touch(el, 'touchend', 120, 50)

    expect(onBack).toHaveBeenCalledTimes(1)
  })

  it('fires from the right edge as well（两侧皆可，与方向无关）', () => {
    const onBack = vi.fn()
    const el = mountGesture(onBack)
    const originalWidth = window.innerWidth
    Object.defineProperty(window, 'innerWidth', { value: 300, configurable: true })

    touch(el, 'touchstart', 290, 50) // 右缘 28px 内
    touch(el, 'touchmove', 180, 50) // 向左 110px
    touch(el, 'touchend', 180, 50)

    Object.defineProperty(window, 'innerWidth', { value: originalWidth, configurable: true })
    expect(onBack).toHaveBeenCalledTimes(1)
  })

  it('does not fire when released before the threshold', () => {
    const onBack = vi.fn()
    const el = mountGesture(onBack)

    touch(el, 'touchstart', 10, 50)
    touch(el, 'touchmove', 30, 50) // 20px：进度 ~0.21，低于快甩下限 0.25
    touch(el, 'touchend', 30, 50)

    expect(onBack).not.toHaveBeenCalled()
  })

  it('releases the gesture when vertical intent wins（scroll 模式滚屏不受影响）', () => {
    const onBack = vi.fn()
    const el = mountGesture(onBack)

    touch(el, 'touchstart', 10, 50)
    touch(el, 'touchmove', 12, 90) // 纵向位移先超斜率阈值
    touch(el, 'touchmove', 140, 90) // 之后再水平拖也不算
    touch(el, 'touchend', 140, 90)

    expect(onBack).not.toHaveBeenCalled()
  })

  it('ignores touches starting outside the edge zone', () => {
    const onBack = vi.fn()
    const el = mountGesture(onBack)

    touch(el, 'touchstart', 150, 50) // 屏幕中部
    touch(el, 'touchmove', 20, 50) // 即使向左划过阈值
    touch(el, 'touchend', 20, 50)

    expect(onBack).not.toHaveBeenCalled()
  })

  it('claims the touch in the capture phase so page-turn swipe never sees it', () => {
    const onBack = vi.fn()
    const el = mountGesture(onBack)
    const child = document.createElement('div')
    el.appendChild(child)
    const childTouchStart = vi.fn()
    child.addEventListener('touchstart', childTouchStart)

    touch(el, 'touchstart', 8, 50)

    // 捕获阶段 stopPropagation：子元素（PageMode 翻页滑动）收不到 touchstart。
    expect(childTouchStart).not.toHaveBeenCalled()
  })
})
