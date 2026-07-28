/**
 * Container-driven column counting — the web equivalent of Android
 * `AutoStaggeredGridLayoutManager.getSpanCountForMinSize()` (STRATEGY_MIN_SIZE):
 *
 * ```
 * spanCount = max(1, floor(totalSpace / columnSize))
 * ```
 *
 * The column count is NEVER hardcoded; it auto-derives from the observed
 * container width ÷ the configured minimum column width, exactly matching the
 * Android layout manager (`contracts/responsive-strategy.md` §3–4). Column
 * widths follow the Android dimens per mode:
 *
 * | Mode         | columnWidth |
 * |--------------|-------------|
 * | List long    | 480         |
 * | List short   | 320         |
 * | Grid large   | 160         |
 * | Grid middle  | 120         |
 * | Grid small   | 80          |
 *
 * Optionally breakpoint-aware: pass a `BreakpointColumnWidth[]` list to step
 * the minimum column width at viewport breakpoints (Android `sw600dp` /
 * `sw720dp` resource qualifiers).
 *
 * Native browser APIs only (`ResizeObserver` + passive resize listener).
 */

import { computed, onMounted, onUnmounted, ref, watch, type Ref } from 'vue'

/**
 * One viewport breakpoint step (Android resource-qualifier equivalent,
 * e.g. `{ minWidth: 600, columnWidth: 160 }` mirrors `values-sw600dp`).
 */
export interface BreakpointColumnWidth {
  /** CSS `min-width` of the breakpoint in viewport px. */
  minWidth: number
  /** Minimum column width to use at or above `minWidth`. */
  columnWidth: number
}

/** Options for {@link useResponsiveColumns}. */
export interface ResponsiveColumnsOptions {
  /**
   * Minimum column width in px — a constant, or a breakpoint table resolved
   * against the viewport width (largest `minWidth` ≤ viewport wins; the
   * smallest entry is the sub-breakpoint default).
   */
  columnWidth: number | readonly BreakpointColumnWidth[]
  /** Gap between columns in px (`--gallery-grid-interval`). @default 4 */
  gap?: number
  /** Template ref of the grid container whose width drives the layout. */
  containerRef: Ref<HTMLElement | null>
}

/**
 * Derive a reactive column count from a container's live width, replicating
 * `AutoStaggeredGridLayoutManager` auto-spanning for the gallery grid/list.
 *
 * With gaps, `n` columns of width `w` need `n·w + (n−1)·gap ≤ containerWidth`,
 * so the count is `max(1, floor((width + gap) / (columnWidth + gap)))` —
 * identical to the Android formula when `gap = 0`. `actualColumnWidth` is the
 * width each column should actually take to fill the container evenly
 * (feed it to `grid-template-columns: repeat(n, …)` or thumb sizing).
 *
 * @example
 * ```vue
 * <script setup lang="ts">
 * const containerRef = ref<HTMLElement | null>(null)
 * // Grid "middle" mode with a sw600dp step-up, per responsive-strategy.md:
 * const { columnCount, actualColumnWidth } = useResponsiveColumns({
 *   columnWidth: [
 *     { minWidth: 0, columnWidth: 120 },
 *     { minWidth: 600, columnWidth: 160 },
 *   ],
 *   gap: 4,
 *   containerRef,
 * })
 * </script>
 *
 * <template>
 *   <div
 *     ref="containerRef"
 *     :style="{
 *       display: 'grid',
 *       gridTemplateColumns: `repeat(${columnCount}, 1fr)`,
 *       gap: '4px',
 *     }"
 *   >…</div>
 * </template>
 * ```
 *
 * @param options - Minimum column width (constant or breakpoints), gap and
 *   container ref.
 */
export function useResponsiveColumns(options: ResponsiveColumnsOptions) {
  const { columnWidth, containerRef } = options
  const gap = Math.max(0, options.gap ?? 4)

  /** Live content-box width of the container in px (0 until measured). */
  const containerWidth: Ref<number> = ref(0)
  /** Live viewport width, used to resolve breakpoint tables. */
  const windowWidth: Ref<number> = ref(typeof window !== 'undefined' ? window.innerWidth : 0)

  /** The minimum column width in effect after breakpoint resolution. */
  const effectiveColumnWidth = computed<number>(() => {
    if (typeof columnWidth === 'number') return columnWidth

    const viewport = windowWidth.value
    let best: number | null = null
    let bestMinWidth = -Infinity
    for (const bp of columnWidth) {
      if (bp.minWidth <= viewport && bp.minWidth > bestMinWidth) {
        bestMinWidth = bp.minWidth
        best = bp.columnWidth
      }
    }
    // No breakpoint matched (empty table or narrower than every minWidth):
    // fall back to the smallest declared width.
    if (best === null) {
      for (const bp of columnWidth) {
        if (best === null || bp.minWidth < bestMinWidth) {
          bestMinWidth = bp.minWidth
          best = bp.columnWidth
        }
      }
    }
    return best ?? 0
  })

  /**
   * Column count: `max(1, floor((containerWidth + gap) / (columnWidth + gap)))`
   * — the AutoStaggeredGridLayoutManager span formula, gap-aware.
   */
  const columnCount = computed<number>(() => {
    const minColumnWidth = effectiveColumnWidth.value
    const width = containerWidth.value
    if (minColumnWidth <= 0 || width <= 0) return 1
    return Math.max(1, Math.floor((width + gap) / (minColumnWidth + gap)))
  })

  /**
   * Width each column should take to fill the container evenly:
   * `(containerWidth − (columns − 1) · gap) / columns`. Falls back to the
   * configured minimum width before the container is measured.
   */
  const actualColumnWidth = computed<number>(() => {
    const width = containerWidth.value
    if (width <= 0) return effectiveColumnWidth.value
    const columns = columnCount.value
    return (width - gap * (columns - 1)) / columns
  })

  let resizeObserver: ResizeObserver | null = null
  let observedElement: HTMLElement | null = null

  /** Track viewport width for breakpoint resolution (passive listener). */
  function onWindowResize(): void {
    windowWidth.value = window.innerWidth
    // Fallback when ResizeObserver is unavailable: approximate container
    // width from its client width on window resizes.
    if (resizeObserver === null && containerRef.value) {
      containerWidth.value = containerRef.value.clientWidth
    }
  }

  /** Observe the container's content-box width. */
  function bind(el: HTMLElement): void {
    unbind()
    observedElement = el
    containerWidth.value = el.clientWidth

    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver((entries) => {
        const entry = entries[0]
        if (entry) containerWidth.value = entry.contentRect.width
      })
      resizeObserver.observe(el)
    }
  }

  /** Stop observing the current container. */
  function unbind(): void {
    if (observedElement && resizeObserver) {
      resizeObserver.unobserve(observedElement)
    }
    observedElement = null
    if (resizeObserver) {
      resizeObserver.disconnect()
      resizeObserver = null
    }
  }

  // Re-bind when the container element appears or is swapped (v-if, routing).
  watch(containerRef, (el) => {
    if (el) bind(el)
    else unbind()
  }, { flush: 'post' })

  onMounted(() => {
    window.addEventListener('resize', onWindowResize, { passive: true })
    if (containerRef.value) bind(containerRef.value)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', onWindowResize)
    unbind()
  })

  return { columnCount, actualColumnWidth, containerWidth, effectiveColumnWidth }
}
