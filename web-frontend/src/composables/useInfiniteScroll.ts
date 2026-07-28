/**
 * Infinite-scroll pagination — the web counterpart of Android `ContentLayout`'s
 * footer refresh: "scrolling near the bottom auto-triggers footer refresh
 * (infinite paging)", with `RefreshLayout` pull-to-refresh mapping to
 * {@link useInfiniteScroll}'s `reset()`.
 *
 * A sentinel element at the bottom of the list is watched with an
 * `IntersectionObserver`; when it nears the viewport the next page is fetched
 * through the supplied `onLoadMore` callback, guarded against re-entrant
 * loads and exhausted pages.
 *
 * Native browser APIs only; when `IntersectionObserver` is unavailable the
 * sentinel is inert but `loadMore()` remains callable manually.
 */

import { onUnmounted, ref, watch, type Ref } from 'vue'

/** Options for {@link useInfiniteScroll}. */
export interface InfiniteScrollOptions {
  /**
   * Bottom root margin in px — how far BEFORE the sentinel becomes visible
   * the next page starts loading (prefetch distance). @default 200
   */
  threshold?: number
  /**
   * Fetch logic for the next page, invoked with the 1-based page number to
   * load (the initial page-0 content is owned by the caller). Throw to signal
   * failure — the page counter rolls back and the error is captured in the
   * returned `error` ref.
   */
  onLoadMore?: (page: number) => Promise<void>
}

/**
 * Sentinel-driven infinite pagination with loading/end guards.
 *
 * Place `sentinelRef` on an element after the last list row; the observer
 * calls `loadMore()` whenever it nears the viewport. `loadMore` is guarded by
 * `isLoading` (no re-entrant fetches) and `hasMore` (stops after the last
 * page). `reset()` returns to page 0 for pull-to-refresh, invalidating any
 * in-flight load.
 *
 * @example
 * ```vue
 * <script setup lang="ts">
 * const items = ref<GalleryInfo[]>([])
 * const { sentinelRef, isLoading, hasMore, reset } = useInfiniteScroll({
 *   threshold: 400,
 *   onLoadMore: async (page) => {
 *     const res = await api.fetchGalleryList({ page })
 *     items.value = [...items.value, ...res.data]
 *     setHasMore(items.value.length < res.total)
 *   },
 * })
 * </script>
 *
 * <template>
 *   <div ref="sentinelRef" class="list-footer">
 *     <ProgressSpinner v-if="isLoading" size="small" />
 *   </div>
 * </template>
 * ```
 *
 * @param options - Prefetch distance and page-fetch callback.
 */
export function useInfiniteScroll(options: InfiniteScrollOptions = {}) {
  const { threshold = 200, onLoadMore } = options

  /** Template ref for the sentinel element placed after the list content. */
  const sentinelRef: Ref<HTMLElement | null> = ref(null)
  /** True while a `loadMore` fetch is in flight (re-entrancy guard). */
  const isLoading: Ref<boolean> = ref(false)
  /** False once the last page has loaded; stops further triggering. */
  const hasMore: Ref<boolean> = ref(true)
  /** Current 0-based page index (initial content = page 0). */
  const page: Ref<number> = ref(0)
  /** Last `onLoadMore` failure, cleared on the next successful load. */
  const error: Ref<unknown> = ref(null)

  let observer: IntersectionObserver | null = null
  let sentinelVisible = false
  /** Invalidates in-flight loads when `reset()` fires mid-fetch. */
  let resetToken = 0

  /**
   * Load the next page. Guarded: resolves immediately when already loading
   * or when `hasMore` is false. On failure the page counter is left
   * unchanged so the same page can be retried.
   */
  async function loadMore(): Promise<void> {
    if (isLoading.value || !hasMore.value) return

    isLoading.value = true
    const token = resetToken
    const next = page.value + 1

    try {
      await onLoadMore?.(next)
      if (token === resetToken) {
        page.value = next
        error.value = null
      }
    } catch (err) {
      if (token === resetToken) {
        error.value = err
      }
    } finally {
      if (token === resetToken) {
        isLoading.value = false
      }
    }
  }

  /**
   * Reset to page 0 (pull-to-refresh). Clears loading/end state and
   * invalidates any in-flight load; the caller re-fetches page 0 itself.
   */
  function reset(): void {
    resetToken++
    page.value = 0
    hasMore.value = true
    isLoading.value = false
    error.value = null
  }

  /**
   * Update the end-of-list flag (call after each fetch with
   * `loaded < total`). Re-arms immediately when content ran out but the
   * sentinel is still on screen (e.g. short first page on a tall viewport).
   */
  function setHasMore(value: boolean): void {
    hasMore.value = value
    if (value && !isLoading.value && sentinelVisible) {
      void loadMore()
    }
  }

  // (Re-)observe whenever the sentinel element mounts or is swapped (v-if).
  watch(sentinelRef, (el, _previous, onCleanup) => {
    if (!el) {
      sentinelVisible = false
      return
    }
    if (typeof IntersectionObserver === 'undefined') return

    if (!observer) {
      observer = new IntersectionObserver(
        (entries) => {
          const entry = entries[0]
          if (!entry) return
          sentinelVisible = entry.isIntersecting
          if (entry.isIntersecting) {
            void loadMore()
          }
        },
        { rootMargin: `0px 0px ${threshold}px 0px` },
      )
    }

    observer.observe(el)
    onCleanup(() => {
      observer?.unobserve(el)
      sentinelVisible = false
    })
  }, { flush: 'post' })

  onUnmounted(() => {
    observer?.disconnect()
    observer = null
  })

  return { sentinelRef, isLoading, hasMore, page, error, loadMore, reset, setHasMore }
}
