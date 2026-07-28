/**
 * Windowed virtual scrolling for large gallery lists (1000+ items) — the web
 * counterpart of the Android `EasyRecyclerView` used by `ContentLayout` in the
 * gallery list scenes.
 *
 * Only the visible slice of `items` (plus a configurable overscan buffer) is
 * exposed for rendering. Consumers absolutely position each row at its
 * `offsetTop` inside a spacer element sized to `totalHeight`, exactly like a
 * RecyclerView laying out only attached children.
 *
 * Supports fixed row heights (list mode) and variable row heights (masonry /
 * grid mode): pass an estimator function and refine real rendered heights via
 * `measureItem` — offsets recompute automatically.
 *
 * Native browser APIs only: `requestAnimationFrame`-coalesced scroll handling
 * with passive listeners, `ResizeObserver` for viewport tracking.
 */

import { computed, onMounted, onUnmounted, ref, watch, type Ref } from 'vue'

/** Options for {@link useVirtualScroll}. */
export interface VirtualScrollOptions {
  /**
   * Row height in px — a constant for fixed-height rows (list mode), or a
   * function of the item index for variable-height rows (masonry layout).
   * Function values are treated as estimates until {@link useVirtualScroll}'s
   * `measureItem` reports the actual rendered height for an index.
   */
  itemHeight: number | ((index: number) => number)
  /** Extra rows rendered above and below the viewport. @default 5 */
  overscan?: number
  /** Template ref of the scroll container (the element with `overflow-y`). */
  containerRef: Ref<HTMLElement | null>
}

/** One windowed row produced by {@link useVirtualScroll}. */
export interface VirtualItem<T> {
  /** The source item from the watched list. */
  item: T
  /** Index of the item within the full list (usable as `:key`). */
  index: number
  /** Top offset in px inside the `totalHeight` spacer. */
  offsetTop: number
}

/** rAF scheduler with a timeout fallback for non-browser environments. */
const scheduleFrame: (cb: () => void) => number =
  typeof requestAnimationFrame === 'function'
    ? (cb) => requestAnimationFrame(cb)
    : (cb) => setTimeout(cb, 16) as unknown as number

/** Matching cancellation for {@link scheduleFrame}. */
const cancelFrame: (id: number) => void =
  typeof cancelAnimationFrame === 'function'
    ? (id) => cancelAnimationFrame(id)
    : (id) => clearTimeout(id as unknown as ReturnType<typeof setTimeout>)

/** Idle time in ms after the last scroll event before `isScrolling` resets. */
const SCROLL_IDLE_DELAY_MS = 150

/**
 * Virtual (windowed) scrolling over a reactive list.
 *
 * Renders only `visibleItems` — the rows intersecting the viewport plus
 * `overscan` rows on each side — while `totalHeight` keeps the scrollbar
 * proportional to the full list. Scroll events are coalesced through
 * `requestAnimationFrame` and attached passively, so fast flings never block
 * the main thread or fight the compositor.
 *
 * @example
 * ```vue
 * <script setup lang="ts">
 * const containerRef = ref<HTMLElement | null>(null)
 * const galleries = ref<GalleryInfo[]>([])
 * const { visibleItems, totalHeight, scrollToIndex } = useVirtualScroll(
 *   galleries,
 *   { itemHeight: 132, overscan: 5, containerRef },
 * )
 * </script>
 *
 * <template>
 *   <div ref="containerRef" class="scroll-container">
 *     <div :style="{ height: `${totalHeight}px`, position: 'relative' }">
 *       <GalleryCard
 *         v-for="row in visibleItems"
 *         :key="row.index"
 *         :gallery="row.item"
 *         :style="{ position: 'absolute', top: `${row.offsetTop}px` }"
 *       />
 *     </div>
 *   </div>
 * </template>
 * ```
 *
 * @param items - Reactive list to window over.
 * @param options - Height model, overscan and scroll container ref.
 */
export function useVirtualScroll<T>(items: Ref<T[]>, options: VirtualScrollOptions) {
  const { itemHeight, containerRef } = options
  const overscan = Math.max(0, options.overscan ?? 5)

  /** Current vertical scroll offset of the container in px. */
  const scrollTop = ref(0)
  /** True while the user is scrolling; resets ~150ms after the last event. */
  const isScrolling = ref(false)

  /** Viewport (client) height of the scroll container in px. */
  const viewportHeight = ref(0)
  /** Bumped to invalidate the prefix-sum offset cache. */
  const layoutVersion = ref(0)
  /** Real rendered heights reported via `measureItem` (masonry support). */
  const measuredHeights = new Map<number, number>()

  let boundContainer: HTMLElement | null = null
  let resizeObserver: ResizeObserver | null = null
  let rafId: number | null = null
  let pendingScrollTop = 0
  let idleTimer: ReturnType<typeof setTimeout> | null = null

  /**
   * Resolved height of row `index`: a measured height wins over the
   * configured constant/estimator.
   */
  function heightAt(index: number): number {
    const measured = measuredHeights.get(index)
    if (measured !== undefined) return measured
    return typeof itemHeight === 'function' ? itemHeight(index) : itemHeight
  }

  /**
   * Prefix-sum layout of the whole list: `offsets[i]` is the top edge of row
   * `i`, `total` is the full content height. Recomputed only when `items`,
   * measured heights, or the height model change — never per scroll frame.
   */
  const layout = computed<{ offsets: number[]; total: number }>(() => {
    // Register the invalidation dependency (items watch + measureItem bump it).
    void layoutVersion.value
    const count = items.value.length
    const offsets = new Array<number>(count)
    let total = 0
    for (let i = 0; i < count; i++) {
      offsets[i] = total
      total += heightAt(i)
    }
    return { offsets, total }
  })

  /** Total scrollable content height in px (size the spacer to this). */
  const totalHeight = computed(() => layout.value.total)

  /**
   * First/last row intersecting the viewport (overscan NOT applied), located
   * via binary search over the monotonic offsets — O(log n) per scroll frame.
   */
  const visibleRange = computed<{ first: number; last: number }>(() => {
    const { offsets } = layout.value
    const count = offsets.length
    if (count === 0) return { first: 0, last: -1 }

    const top = scrollTop.value
    const bottom = top + Math.max(viewportHeight.value, 0)

    // First row whose bottom edge lies below the viewport top.
    let lo = 0
    let hi = count - 1
    let first = count
    while (lo <= hi) {
      const mid = (lo + hi) >> 1
      if (offsets[mid] + heightAt(mid) > top) {
        first = mid
        hi = mid - 1
      } else {
        lo = mid + 1
      }
    }

    // Last row whose top edge lies above the viewport bottom.
    lo = first
    hi = count - 1
    let last = first - 1
    while (lo <= hi) {
      const mid = (lo + hi) >> 1
      if (offsets[mid] < bottom) {
        last = mid
        lo = mid + 1
      } else {
        hi = mid - 1
      }
    }

    return { first, last }
  })

  /** First rendered index (visible range expanded by `overscan`). */
  const startIndex = computed(() => Math.max(0, visibleRange.value.first - overscan))

  /** Last rendered index (inclusive; -1 for an empty list). */
  const endIndex = computed(() => {
    const count = items.value.length
    if (count === 0) return -1
    return Math.min(count - 1, visibleRange.value.last + overscan)
  })

  /** The windowed slice to render: items plus their absolute offsets. */
  const visibleItems = computed<VirtualItem<T>[]>(() => {
    const list = items.value
    const { offsets } = layout.value
    const start = startIndex.value
    const end = endIndex.value
    const rows: VirtualItem<T>[] = []
    for (let i = start; i <= end && i < list.length; i++) {
      rows.push({ item: list[i], index: i, offsetTop: offsets[i] })
    }
    return rows
  })

  /** Coalesce scroll events into one state update per animation frame. */
  function scheduleScrollUpdate(top: number): void {
    pendingScrollTop = top
    if (rafId !== null) return
    rafId = scheduleFrame(() => {
      rafId = null
      scrollTop.value = pendingScrollTop
      isScrolling.value = true
      if (idleTimer !== null) clearTimeout(idleTimer)
      idleTimer = setTimeout(() => {
        isScrolling.value = false
      }, SCROLL_IDLE_DELAY_MS)
    })
  }

  /**
   * Scroll event handler. Attached automatically (passively) to
   * `containerRef`; also exposed for manual `@scroll` binding on custom
   * scroll proxies — double-binding is harmless (same coalescing pipeline).
   */
  function onScroll(event: Event): void {
    const target = event.target
    scheduleScrollUpdate(target instanceof HTMLElement ? target.scrollTop : 0)
  }

  /**
   * Programmatically scroll a row into view (FastScroller / keyboard-jump
   * equivalent of `LinearLayoutManager.scrollToPositionWithOffset`).
   *
   * @param index - Row index; clamped into `[0, items.length - 1]`.
   * @param align - Where to place the row in the viewport. @default 'start'
   */
  function scrollToIndex(index: number, align: 'start' | 'center' | 'end' = 'start'): void {
    const el = containerRef.value
    const count = items.value.length
    if (!el || count === 0) return

    const clamped = Math.max(0, Math.min(count - 1, index))
    const top = layout.value.offsets[clamped]
    const height = heightAt(clamped)

    let target = top
    if (align === 'center') {
      target = top - Math.max(0, (viewportHeight.value - height) / 2)
    } else if (align === 'end') {
      target = top - Math.max(0, viewportHeight.value - height)
    }

    el.scrollTop = Math.max(0, target)
    scrollTop.value = el.scrollTop
  }

  /**
   * Report the actual rendered height of a row (masonry / variable-height
   * layouts). Call from a child `ResizeObserver` or `onload` hook; offsets
   * and `totalHeight` recompute automatically. No-op when the height is
   * unchanged.
   *
   * @param index - Row index the measurement belongs to.
   * @param height - Rendered height in px.
   */
  function measureItem(index: number, height: number): void {
    if (measuredHeights.get(index) === height) return
    measuredHeights.set(index, height)
    layoutVersion.value++
  }

  /** Attach the passive scroll listener and viewport tracking to a container. */
  function bind(el: HTMLElement): void {
    unbind()
    boundContainer = el
    viewportHeight.value = el.clientHeight
    scrollTop.value = el.scrollTop
    el.addEventListener('scroll', onScroll, { passive: true })

    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver((entries) => {
        const entry = entries[0]
        if (entry) viewportHeight.value = entry.contentRect.height
      })
      resizeObserver.observe(el)
    }
  }

  /** Detach listeners/observers from the current container. */
  function unbind(): void {
    if (boundContainer) {
      boundContainer.removeEventListener('scroll', onScroll)
      boundContainer = null
    }
    if (resizeObserver) {
      resizeObserver.disconnect()
      resizeObserver = null
    }
  }

  // Invalidate the offset cache when the list is replaced or grows/shrinks
  // in place (e.g. infinite-scroll appends mutating the same array).
  watch([items, () => items.value.length], () => {
    layoutVersion.value++
  })

  // Re-bind when the container element appears or is swapped (v-if, routing).
  watch(containerRef, (el) => {
    if (el) bind(el)
    else unbind()
  }, { flush: 'post' })

  onMounted(() => {
    if (containerRef.value) bind(containerRef.value)
  })

  onUnmounted(() => {
    unbind()
    if (rafId !== null) {
      cancelFrame(rafId)
      rafId = null
    }
    if (idleTimer !== null) {
      clearTimeout(idleTimer)
      idleTimer = null
    }
  })

  return {
    visibleItems,
    totalHeight,
    scrollTop,
    isScrolling,
    startIndex,
    endIndex,
    scrollToIndex,
    onScroll,
    measureItem,
  }
}
