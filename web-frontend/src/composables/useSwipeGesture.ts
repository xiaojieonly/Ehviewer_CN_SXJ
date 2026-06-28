import { ref, onMounted, onUnmounted, type Ref } from 'vue'

export interface SwipeCallbacks {
  onSwipeLeft?: () => void
  onSwipeRight?: () => void
  onSwipeUp?: () => void
  onSwipeDown?: () => void
  onPinch?: (scale: number) => void
}

export function useSwipeGesture(element: Ref<HTMLElement | null>, callbacks: SwipeCallbacks) {
  const threshold = 50
  const startTime = ref(0)
  const startX = ref(0)
  const startY = ref(0)
  const initialPinchDistance = ref(0)

  function onTouchStart(e: TouchEvent) {
    if (e.touches.length === 2) {
      const dx = e.touches[0].clientX - e.touches[1].clientX
      const dy = e.touches[0].clientY - e.touches[1].clientY
      initialPinchDistance.value = Math.hypot(dx, dy)
      return
    }
    startTime.value = Date.now()
    startX.value = e.touches[0].clientX
    startY.value = e.touches[0].clientY
  }

  function onTouchEnd(e: TouchEvent) {
    if (e.changedTouches.length === 0) return

    const dx = e.changedTouches[0].clientX - startX.value
    const dy = e.changedTouches[0].clientY - startY.value
    const elapsed = Date.now() - startTime.value

    if (elapsed > 500) return

    const absDx = Math.abs(dx)
    const absDy = Math.abs(dy)

    if (absDx > absDy && absDx > threshold) {
      if (dx > 0) callbacks.onSwipeRight?.()
      else callbacks.onSwipeLeft?.()
    } else if (absDy > absDx && absDy > threshold) {
      if (dy > 0) callbacks.onSwipeDown?.()
      else callbacks.onSwipeUp?.()
    }
  }

  function onTouchMove(e: TouchEvent) {
    if (e.touches.length === 2) {
      const dx = e.touches[0].clientX - e.touches[1].clientX
      const dy = e.touches[0].clientY - e.touches[1].clientY
      const distance = Math.hypot(dx, dy)
      if (initialPinchDistance.value > 0) {
        const scale = distance / initialPinchDistance.value
        callbacks.onPinch?.(scale)
      }
    }
  }

  onMounted(() => {
    const el = element.value
    if (!el) return
    el.addEventListener('touchstart', onTouchStart, { passive: true })
    el.addEventListener('touchend', onTouchEnd, { passive: true })
    el.addEventListener('touchmove', onTouchMove, { passive: true })
  })

  onUnmounted(() => {
    const el = element.value
    if (!el) return
    el.removeEventListener('touchstart', onTouchStart)
    el.removeEventListener('touchend', onTouchEnd)
    el.removeEventListener('touchmove', onTouchMove)
  })
}
