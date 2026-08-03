import { onMounted, onUnmounted } from 'vue'

export interface KeyNavCallbacks {
  onPrev?: () => void
  onNext?: () => void
  onFirst?: () => void
  onLast?: () => void
  onToggleToolbar?: () => void
  onZoomIn?: () => void
  onZoomOut?: () => void
  /** Wave-1 1c: when false, arrow/space paging is disabled (keyboardPaging off). */
  pagingEnabled?: () => boolean
}

export function useKeyboardNav(callbacks: KeyNavCallbacks) {
  function onKeyDown(e: KeyboardEvent) {
    const paging = callbacks.pagingEnabled ? callbacks.pagingEnabled() : true
    switch (e.key) {
      case 'ArrowLeft':
      case 'a':
        if (!paging) break
        e.preventDefault()
        callbacks.onPrev?.()
        break
      case 'ArrowRight':
      case 'd':
        if (!paging) break
        e.preventDefault()
        callbacks.onNext?.()
        break
      case 'Home':
        e.preventDefault()
        callbacks.onFirst?.()
        break
      case 'End':
        e.preventDefault()
        callbacks.onLast?.()
        break
      case ' ':
        if (paging) {
          e.preventDefault()
          callbacks.onNext?.()
          break
        }
        e.preventDefault()
        callbacks.onToggleToolbar?.()
        break
      case 'Escape':
        e.preventDefault()
        callbacks.onToggleToolbar?.()
        break
      case '+':
      case '=':
        e.preventDefault()
        callbacks.onZoomIn?.()
        break
      case '-':
        e.preventDefault()
        callbacks.onZoomOut?.()
        break
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', onKeyDown)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', onKeyDown)
  })
}
