/**
 * Image preloading composable with priority ordering and LRU cache eviction.
 *
 * Preloads surrounding pages ahead of and behind the reader's current
 * position so that page turns are instant. Stale preloads (from a previous
 * position) are cancelled via a generation counter — their results are
 * silently discarded when they resolve.
 *
 * Priority order: current page → next N ahead → previous M behind.
 */
import { ref, onUnmounted, type Ref } from 'vue'

/** Configuration for {@link useImagePreload}. */
export interface ImagePreloadOptions {
  /** Number of pages to preload ahead of the current page. @default 2 */
  ahead?: number
  /** Number of pages to preload behind the current page. @default 1 */
  behind?: number
  /** Maximum number of images retained in the LRU cache. @default 10 */
  maxCache?: number
}

/**
 * Preload and cache images around the reader's current position.
 *
 * @param options - Tuning knobs for lookahead/lookbehind distance and cache
 *   size. All fields are optional with sensible defaults.
 *
 * @example
 * ```ts
 * const { preload, getCached, cancelAll, preloading } = useImagePreload({
 *   ahead: 3,
 *   behind: 1,
 *   maxCache: 12,
 * })
 *
 * // On every page change:
 * preload(imageUrls, currentIndex)
 *
 * // In the reader component, check the cache before hitting the network:
 * const cached = getCached(url)
 * if (cached) { /* use cached element *\/ }
 * ```
 */
export function useImagePreload(options?: ImagePreloadOptions): {
  preload: (urls: string[], currentIndex: number) => void
  getCached: (url: string) => HTMLImageElement | undefined
  cancelAll: () => void
  preloading: Ref<Set<string>>
} {
  const ahead = options?.ahead ?? 2
  const behind = options?.behind ?? 1
  const maxCache = options?.maxCache ?? 10

  /**
   * LRU image cache. Map insertion order is used as the recency order:
   * the **first** key is the least-recently-used entry, the **last** is
   * the most-recently-used.
   */
  const cache = new Map<string, HTMLImageElement>()

  /**
   * Reactive set of URLs whose preload is currently in flight.
   * Consumers can use this to show a subtle loading indicator on
   * adjacent pages.
   */
  const preloading = ref(new Set<string>())

  /**
   * Monotonically increasing generation counter. Each call to
   * {@link preload} increments it; callbacks from earlier generations
   * are discarded, effectively cancelling stale preloads.
   */
  let generation = 0

  /* ---- internal helpers ---- */

  /**
   * Insert an image into the LRU cache, evicting the oldest entry when
   * the cache exceeds {@link maxCache}.
   */
  function addToCache(url: string, img: HTMLImageElement): void {
    // Delete first so re-insertion moves the key to the end (most recent).
    cache.delete(url)
    cache.set(url, img)

    // Evict LRU entries (first keys in insertion order).
    while (cache.size > maxCache) {
      const oldest = cache.keys().next().value as string | undefined
      if (oldest === undefined) break
      cache.delete(oldest)
    }
  }

  /**
   * Build a priority-ordered list of page indices to preload:
   * current → next 1…N ahead → prev 1…M behind.
   */
  function buildPriorityIndices(
    totalUrls: number,
    currentIndex: number,
  ): number[] {
    const indices: number[] = []

    // Current page has the highest priority.
    if (currentIndex >= 0 && currentIndex < totalUrls) {
      indices.push(currentIndex)
    }

    // Next N pages (ahead).
    for (let i = 1; i <= ahead; i++) {
      const idx = currentIndex + i
      if (idx < totalUrls) indices.push(idx)
    }

    // Previous M pages (behind).
    for (let i = 1; i <= behind; i++) {
      const idx = currentIndex - i
      if (idx >= 0) indices.push(idx)
    }

    return indices
  }

  /* ---- public API ---- */

  /**
   * Preload images around `currentIndex` in priority order.
   *
   * Calling this function again (e.g. after a page jump) automatically
   * invalidates all in-flight preloads from the previous call — their
   * results will be discarded even if they resolve later.
   *
   * @param urls - Full list of image URLs for the gallery.
   * @param currentIndex - The 0-based page the reader is showing.
   */
  function preload(urls: string[], currentIndex: number): void {
    // Invalidate all previous in-flight preloads.
    generation++
    const currentGen = generation

    // Reset the reactive preloading set for the new position.
    preloading.value = new Set<string>()

    const indices = buildPriorityIndices(urls.length, currentIndex)

    for (const idx of indices) {
      const url = urls[idx]
      if (!url) continue

      // Skip URLs already in the LRU cache — no need to re-fetch.
      if (cache.has(url)) continue

      preloading.value.add(url)

      const img = new Image()

      img.onload = () => {
        // Discard results from stale generations.
        if (currentGen !== generation) return
        preloading.value.delete(url)
        addToCache(url, img)
      }

      img.onerror = () => {
        if (currentGen !== generation) return
        preloading.value.delete(url)
      }

      img.src = url
    }
  }

  /**
   * Retrieve a previously preloaded image element from the LRU cache.
   *
   * Accessing an entry promotes it to most-recently-used so it is less
   * likely to be evicted.
   *
   * @param url - The image URL to look up.
   * @returns The cached `HTMLImageElement`, or `undefined` on cache miss.
   */
  function getCached(url: string): HTMLImageElement | undefined {
    const img = cache.get(url)
    if (img) {
      // Promote: delete + re-insert moves the key to the end (MRU).
      cache.delete(url)
      cache.set(url, img)
    }
    return img
  }

  /**
   * Cancel all in-flight preloads and clear the preloading indicator.
   *
   * Cached images are **not** evicted — call this when the reader is
   * temporarily paused (e.g. tab hidden) and you want to stop network
   * activity without losing already-downloaded images.
   */
  function cancelAll(): void {
    generation++
    preloading.value = new Set<string>()
  }

  /* ---- cleanup on component unmount ---- */

  onUnmounted(() => {
    cancelAll()
    cache.clear()
  })

  return { preload, getCached, cancelAll, preloading }
}
