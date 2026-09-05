import { onMounted, onUnmounted } from 'vue'

export interface KeyNavCallbacks {
  onPrev?: () => void
  onNext?: () => void
  onFirst?: () => void
  onLast?: () => void
  onToggleToolbar?: () => void
  /**
   * Zoom one step; leave undefined in modes without zoom semantics
   * (scroll/dual) so +/- is not claimed at all — the key then keeps its
   * native (no-op) behavior instead of silently mutating an unused value.
   */
  onZoomIn?: () => void
  onZoomOut?: () => void
  /** Wave-1 1c: when false, arrow/space paging is disabled (keyboardPaging off). */
  pagingEnabled?: () => boolean
}

/**
 * Focused form controls keep their native keys: a focused seekbar range
 * scrubs with ←/→, a focused button activates on Space, selects react to
 * arrows, text fields need every character — the reader shortcuts never
 * hijack them.
 */
function isFormTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  if (target.isContentEditable) return true
  const tag = target.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT'
}

export function useKeyboardNav(callbacks: KeyNavCallbacks) {
  function onKeyDown(e: KeyboardEvent) {
    // 修饰键组合属于浏览器与操作系统（Ctrl+A 全选、Ctrl+D 书签、Cmd+/- 页面
    // 缩放）——一律放行，且不 preventDefault。
    if (e.ctrlKey || e.metaKey || e.altKey) return
    // IME 组合进行中（拼音选词的 Enter/Esc）不劫持。
    if (e.isComposing) return
    // 焦点在表单控件/可编辑元素上：原生按键优先（seekbar 方向键等）。
    if (isFormTarget(e.target)) return

    const paging = callbacks.pagingEnabled ? callbacks.pagingEnabled() : true
    // 单字符按键统一小写匹配（Shift/A 大写锁定下的 A、D 仍是翻页别名）。
    const key = e.key.length === 1 ? e.key.toLowerCase() : e.key
    switch (key) {
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
        // 空格保持按钮原生激活语义：焦点在按钮上时既不翻页也不开合 chrome。
        if (e.target instanceof HTMLElement && e.target.tagName === 'BUTTON') return
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
        // 未注册缩放回调的模式（scroll/dual）不认领按键、不 preventDefault。
        if (!callbacks.onZoomIn) break
        e.preventDefault()
        callbacks.onZoomIn()
        break
      case '-':
        if (!callbacks.onZoomOut) break
        e.preventDefault()
        callbacks.onZoomOut()
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
