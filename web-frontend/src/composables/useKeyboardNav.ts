import { onMounted, onUnmounted } from 'vue'

export interface KeyNavCallbacks {
  onPrev?: () => void
  onNext?: () => void
  onFirst?: () => void
  onLast?: () => void
  onToggleToolbar?: () => void
  onZoomIn?: () => void
  onZoomOut?: () => void
}

export function useKeyboardNav(callbacks: KeyNavCallbacks) {
  function onKeyDown(e: KeyboardEvent) {
    switch (e.key) {
      case 'ArrowLeft':
      case 'a':
        e.preventDefault()
        callbacks.onPrev?.()
        break
      case 'ArrowRight':
      case 'd':
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
