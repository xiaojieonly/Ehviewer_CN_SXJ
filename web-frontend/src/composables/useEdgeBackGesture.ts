import { onBeforeUnmount, onMounted, reactive, type Ref } from 'vue'

/**
 * useEdgeBackGesture.ts — Android 系统返回手势的 Web 等价（阅读器退出）：
 *
 * - 触点落在左/右边缘 EDGE_ZONE 内即认领该手势，并在捕获阶段吞掉
 *   touchstart——与 Android「边缘触点归系统返回」一致，PageMode 的翻页
 *   滑动不会再收到这个触点；
 * - 水平向内拖动推进进度（模板据此渲染边缘指示条），拖满 EXIT_DISTANCE
 *   或近距离快甩即触发 onBack；
 * - 纵向位移先到斜率阈值视为滚动意图，立即放手不 preventDefault；捏合
 *   （第二根手指落下）同样取消。
 * 与阅读方向无关：左右两条边都触发（Android 返回手势两侧皆可）。
 */
export interface EdgeBackCallbacks {
  onBack: () => void
}

export interface EdgeBackState {
  /** null = 手势未激活；否则为触点出发的边缘。 */
  side: 'left' | 'right' | null
  /** 0–1，拖动进度，驱动指示条宽度/透明度。 */
  progress: number
}

/** 边缘认领区宽度（px）——Android 系统 手势约 24dp 量级。 */
const EDGE_ZONE = 28
/** 触发返回的向内拖动距离（px）。 */
const EXIT_DISTANCE = 96
/** 水平/纵向意图判定斜率（px）——先超出者赢。 */
const SLOP = 12
/** 近距离快甩：进度超过该值且瞬时速度足够即视为返回。 */
const FLING_PROGRESS = 0.25
/** 快甩速度阈值（px/ms，平滑后）。 */
const FLING_VELOCITY = 0.5

export function useEdgeBackGesture(
  el: Ref<HTMLElement | null>,
  callbacks: EdgeBackCallbacks,
): { state: EdgeBackState } {
  const state = reactive<EdgeBackState>({ side: null, progress: 0 })

  let startX = 0
  let startY = 0
  let lastX = 0
  let lastT = 0
  let velocity = 0
  let horizontal = false
  let dead = false

  function reset() {
    state.side = null
    state.progress = 0
    horizontal = false
    velocity = 0
  }

  function onTouchStart(event: TouchEvent) {
    if (event.touches.length !== 1) return
    const touch = event.touches[0]
    const edge = window.innerWidth - EDGE_ZONE
    const side: 'left' | 'right' | null =
      touch.clientX <= EDGE_ZONE ? 'left' : touch.clientX >= edge ? 'right' : null
    if (!side) return
    // 边缘触点归返回手势：捕获阶段吞掉，翻页滑动收不到这个触点。
    event.stopPropagation()
    state.side = side
    state.progress = 0
    startX = touch.clientX
    startY = touch.clientY
    lastX = touch.clientX
    lastT = performance.now()
    velocity = 0
    horizontal = false
    dead = false
  }

  function onTouchMove(event: TouchEvent) {
    if (!state.side) return
    if (event.touches.length !== 1) {
      reset()
      return
    }
    if (dead) return
    const touch = event.touches[0]
    const dx = touch.clientX - startX
    const dy = touch.clientY - startY
    const absDx = Math.abs(dx)
    const absDy = Math.abs(dy)
    if (!horizontal) {
      if (absDx > SLOP && absDx > absDy) {
        horizontal = true
      } else if (absDy > SLOP && absDy > absDx) {
        // 纵向意图（scroll 模式滚屏等）——放手，本次触点不再参与。
        dead = true
        reset()
        return
      } else {
        return
      }
    }
    // 已确认水平向内拖动：浏览器别插手（别滚屏/别触发导航手势语义）。
    event.preventDefault()
    const now = performance.now()
    const dt = now - lastT
    if (dt > 0) {
      velocity = 0.8 * ((touch.clientX - lastX) / dt) + 0.2 * velocity
      lastX = touch.clientX
      lastT = now
    }
    const inward = state.side === 'left' ? Math.max(0, dx) : Math.max(0, -dx)
    state.progress = Math.min(1, inward / EXIT_DISTANCE)
  }

  function onTouchEnd() {
    if (!state.side) return
    const fired = state.progress >= 1 || (state.progress >= FLING_PROGRESS && Math.abs(velocity) > FLING_VELOCITY)
    reset()
    if (fired) callbacks.onBack()
  }

  const opts = { capture: true } as AddEventListenerOptions
  onMounted(() => {
    const node = el.value
    if (!node) return
    node.addEventListener('touchstart', onTouchStart, { ...opts, passive: true })
    node.addEventListener('touchmove', onTouchMove, { ...opts, passive: false })
    node.addEventListener('touchend', onTouchEnd, { ...opts, passive: true })
    node.addEventListener('touchcancel', reset, { ...opts, passive: true })
  })
  onBeforeUnmount(() => {
    const node = el.value
    if (!node) return
    node.removeEventListener('touchstart', onTouchStart, opts)
    node.removeEventListener('touchmove', onTouchMove, opts)
    node.removeEventListener('touchend', onTouchEnd, opts)
    node.removeEventListener('touchcancel', reset, opts)
  })

  return { state }
}
