/**
 * IntersectionObserver-based lazy loading — the web counterpart of the
 * Android list adapters' deferred thumb binding (`TileThumb` / `FixedThumb`
 * only fetch once scrolled near the viewport).
 *
 * Two utilities:
 * - {@link useLazyLoad}: observe an element and flip a reactive ref the first
 *   time it approaches the viewport (one-shot, then unobserved).
 * - {@link useLazyImage}: build `?w={width}` URLs and `srcset` strings per
 *   `contracts/responsive-strategy.md` §8 (server-side on-demand resizing,
 *   DPR-aware).
 *
 * Native browser APIs only; degrades to eager loading when
 * `IntersectionObserver` is unavailable.
 */

import { onUnmounted, ref, watch, type Ref } from 'vue'

/** Options for {@link useLazyLoad}. */
export interface LazyLoadOptions {
  /**
   * CSS margin around the root used to start loading BEFORE the element
   * becomes visible (prefetch distance). @default '200px'
   */
  rootMargin?: string
  /** Visibility ratio that counts as intersecting. @default 0 */
  threshold?: number
}

/**
 * One-shot lazy loading of an element via `IntersectionObserver`.
 *
 * Bind `elementRef` as a template ref (or call `observe` manually for
 * dynamically created elements). The first time the element enters the
 * viewport — including the `rootMargin` prefetch margin — `isIntersecting`
 * flips to `true` permanently and the element is unobserved. When
 * `IntersectionObserver` is unavailable the ref flips immediately (eager
 * fallback).
 *
 * @example
 * ```vue
 * <script setup lang="ts">
 * const { isIntersecting, elementRef } = useLazyLoad({ rootMargin: '300px' })
 * </script>
 *
 * <template>
 *   <div ref="elementRef" class="thumb-container">
 *     <img v-if="isIntersecting" :src="src" decoding="async" />
 *   </div>
 * </template>
 * ```
 *
 * @param options - Prefetch margin and intersection threshold.
 */
export function useLazyLoad(options: LazyLoadOptions = {}) {
  const { rootMargin = '200px', threshold = 0 } = options

  /** Flips to `true` once the observed element nears the viewport (one-shot). */
  const isIntersecting: Ref<boolean> = ref(false)
  /** Template ref to attach; observation starts automatically once set. */
  const elementRef: Ref<HTMLElement | null> = ref(null)

  const supported = typeof IntersectionObserver !== 'undefined'
  let observer: IntersectionObserver | null = null

  /** Lazily create the shared one-shot observer. */
  function getObserver(): IntersectionObserver | null {
    if (!supported) return null
    if (!observer) {
      observer = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (entry.isIntersecting) {
              isIntersecting.value = true
              // One-shot: load once, then stop watching this element.
              observer?.unobserve(entry.target)
            }
          }
        },
        { rootMargin, threshold },
      )
    }
    return observer
  }

  /**
   * Manually register an element (for dynamic/v-for content that does not go
   * through `elementRef`). Loads eagerly when IntersectionObserver is
   * unsupported.
   */
  function observe(el: HTMLElement): void {
    if (!supported) {
      isIntersecting.value = true
      return
    }
    getObserver()?.observe(el)
  }

  /** Stop watching a single element (no-op if not observed). */
  function unobserve(el: HTMLElement): void {
    observer?.unobserve(el)
  }

  /** Stop watching all elements and release the observer. */
  function disconnect(): void {
    observer?.disconnect()
    observer = null
  }

  // Auto-observe once the template ref is assigned; unobserve the previous
  // element when the ref is swapped or cleared.
  watch(elementRef, (el, _previous, onCleanup) => {
    if (!el) return
    observe(el)
    onCleanup(() => {
      unobserve(el)
    })
  }, { flush: 'post' })

  onUnmounted(() => {
    disconnect()
  })

  return { isIntersecting, elementRef, observe, unobserve, disconnect }
}

/**
 * Append the server's on-demand resize parameter (`?w={width}`,
 * `contracts/responsive-strategy.md` §8) to an image URL, tolerating URLs
 * that already carry a query string.
 *
 * @param url - Base image URL (e.g. `/api/image/{gid}/{page}`).
 * @param width - Target width in physical px.
 */
function appendWidthParam(url: string, width: number): string {
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}w=${width}`
}

/**
 * Resolution-selection helpers for responsive images, mirroring the Android
 * per-context width hints (grid small/middle/large 80/120/160dp, list 80dp,
 * detail 128dp) scaled by device pixel ratio.
 *
 * @example
 * ```ts
 * const { getResponsiveSrc, getSrcset } = useLazyImage()
 * // Retina grid thumb in a 120px column:
 * const src = getResponsiveSrc(`/api/image/${gid}/0`, 120) // → …?w=240 at dpr 2
 * const srcset = getSrcset(`/api/image/${gid}/0`, [120, 240, 480])
 * // → '/api/image/…?w=120 120w, /api/image/…?w=240 240w, /api/image/…?w=480 480w'
 * ```
 */
export function useLazyImage() {
  /**
   * Pick the image width for a container: `containerWidth × dpr`, rounded to
   * whole physical pixels (minimum 1).
   *
   * @param baseUrl - Image URL without a `w` parameter.
   * @param containerWidth - CSS-pixel width the image will occupy.
   * @param dpr - Device pixel ratio override; defaults to
   *   `window.devicePixelRatio` (1 outside a browser).
   */
  function getResponsiveSrc(baseUrl: string, containerWidth: number, dpr?: number): string {
    const ratio = dpr ?? (typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1)
    const target = Math.max(1, Math.round(containerWidth * ratio))
    return appendWidthParam(baseUrl, target)
  }

  /**
   * Build a `srcset` descriptor list from candidate widths, e.g.
   * `url?w=120 120w, url?w=240 240w`. Non-positive widths are dropped and the
   * remainder is sorted ascending; the input array is not mutated.
   *
   * @param baseUrl - Image URL without a `w` parameter.
   * @param widths - Candidate widths in CSS px.
   */
  function getSrcset(baseUrl: string, widths: number[]): string {
    return [...widths]
      .filter((w) => Number.isFinite(w) && w > 0)
      .sort((a, b) => a - b)
      .map((w) => `${appendWidthParam(baseUrl, Math.round(w))} ${Math.round(w)}w`)
      .join(', ')
  }

  return { getResponsiveSrc, getSrcset }
}
