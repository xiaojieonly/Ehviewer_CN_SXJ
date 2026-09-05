import { onBeforeUnmount, onMounted } from 'vue'
import type { Ref } from 'vue'

/**
 * useTapZoom.ts — single-tap vs double-tap disambiguation shared by the
 * paged reader modes (plan-2026-09-05 A5):
 *
 * - Desktop (`pointerType === 'mouse'`, falling back to a
 *   `hover: hover + pointer: fine` environment query for engines without
 *   PointerEvent clicks): no artificial delay — every click acts immediately
 *   and the zoom cycle rides on the NATIVE `dblclick` event.
 * - Touch/pen: a 240ms double-tap window (was 280ms) keeps double-tap zoom
 *   from straying into a page turn. A fresh click always clears the pending
 *   single-tap timer, and unmount clears it too — two rapid taps can never
 *   leave two armed timers (the old DualPageMode double-page-turn bug).
 */
export interface TapZoomCallbacks {
  /**
   * Single-activation action (tap-zone navigation / chrome toggle), given the
   * click position in client coordinates. Desktop: fires on every click.
   * Touch: fires once, after the double-tap window elapses.
   */
  onSingleTap: (x: number, y: number) => void
  /** Zoom cycle on double-tap / native double-click; omit when no zoom. */
  onDoubleTap?: () => void
  /**
   * Persistent "gestures are panning" state (zoomed in): single-tap actions
   * yield to dragging, but the double-tap channel stays live so the user can
   * still zoom back out.
   */
  suppressed?: () => boolean
}

export interface TapZoomHandle {
  /** Swallow the clicks that follow a custom gesture (pinch / pan / swipe). */
  suppressTaps: () => void
}

/** Touch double-tap disambiguation window. */
const DOUBLE_TAP_WINDOW_MS = 240
/** Transient suppression right after a custom gesture (pinch / pan / swipe). */
const TAP_SUPPRESS_WINDOW_MS = 400

/** Does this environment have a hover-capable fine pointer (a mouse)? */
export function hasFineHoverPointer(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(hover: hover) and (pointer: fine)').matches
  )
}

export function useTapZoom(
  el: Ref<HTMLElement | null>,
  callbacks: TapZoomCallbacks,
): TapZoomHandle {
  let lastTapAt = 0
  let tapTimer: ReturnType<typeof setTimeout> | null = null
  let suppressUntil = 0

  function suppressTaps() {
    suppressUntil = Date.now() + TAP_SUPPRESS_WINDOW_MS
  }

  function clearTapTimer() {
    if (tapTimer !== null) {
      clearTimeout(tapTimer)
      tapTimer = null
    }
  }

  /**
   * Mouse-like pointer? Prefer the event's own pointerType (correct per click
   * on hybrid touch+mouse devices); older engines that dispatch plain
   * MouseEvents fall back to the environment query.
   */
  function isMouseLike(event: MouseEvent): boolean {
    const pointerType = (event as PointerEvent).pointerType
    if (pointerType) return pointerType === 'mouse'
    return hasFineHoverPointer()
  }

  function onClick(event: MouseEvent) {
    // A fresh click always supersedes a pending single-tap action — at most
    // one armed timer, ever (core of the double-timer fix).
    clearTapTimer()
    // Clicks right after a custom gesture (pinch / pan / swipe) are its
    // synthetic aftermath — swallow them wholesale.
    if (Date.now() < suppressUntil) return

    if (isMouseLike(event)) {
      // Desktop: instant feedback, no disambiguation window — zoom lives on
      // the native dblclick listener below.
      if (!callbacks.suppressed?.()) callbacks.onSingleTap(event.clientX, event.clientY)
      return
    }

    const now = Date.now()
    if (callbacks.onDoubleTap && now - lastTapAt <= DOUBLE_TAP_WINDOW_MS) {
      lastTapAt = 0
      callbacks.onDoubleTap()
      return
    }
    lastTapAt = now
    // Zoomed in: taps are for panning — no zone navigation.
    if (callbacks.suppressed?.()) return
    const x = event.clientX
    const y = event.clientY
    tapTimer = setTimeout(() => {
      tapTimer = null
      callbacks.onSingleTap(x, y)
    }, DOUBLE_TAP_WINDOW_MS)
  }

  function onDblClick(event: MouseEvent) {
    if (Date.now() < suppressUntil) return
    // Touch double-taps resolve through the window in onClick; the browser's
    // synthetic dblclick would fire the zoom a second time.
    if (!isMouseLike(event)) return
    callbacks.onDoubleTap?.()
  }

  onMounted(() => {
    el.value?.addEventListener('click', onClick)
    el.value?.addEventListener('dblclick', onDblClick)
  })

  onBeforeUnmount(() => {
    el.value?.removeEventListener('click', onClick)
    el.value?.removeEventListener('dblclick', onDblClick)
    clearTapTimer()
  })

  return { suppressTaps }
}
