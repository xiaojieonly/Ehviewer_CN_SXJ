/**
 * AI Enhanced Image Source Switching (I3).
 *
 * Subscribes to the WebSocket topic `/topic/gallery/{gid}/enhanced` for
 * `image.enhanced.ready` notifications (contracts/websocket-protocol.md §3.3)
 * through the app-wide singleton connection manager
 * (`composables/useWebSocket.ts`) instead of creating its own STOMP client.
 * The manager reference-counts the single `/ws` connection, sends the bearer
 * token in the STOMP `CONNECT` headers (contract §1.3), retries with
 * exponential backoff (§1.5), and re-establishes every registered subscription
 * automatically after a reconnect (§1.5 step 4) — so the reader never opens a
 * second socket and never needs to re-subscribe on its own.
 *
 * When an enhanced version of a page becomes available, its URL is constructed
 * against the backend image endpoint (`GET /api/v1/image/{galleryId}/{page}`,
 * contracts/openapi.yaml `streamGalleryImage` — `w` + `enhanced` query
 * parameters), preloaded in the background, and the reactive URL map is updated
 * only after the preload succeeds — guaranteeing a seamless hot-swap with no
 * flash or broken image.
 *
 * Page indexing: the WS protocol uses **1-based** page numbers; this composable
 * converts to **0-based** indices so callers can pass the reader's
 * `currentIndex` directly to {@link getImageUrl}.
 */
import {
  ref,
  computed,
  watch,
  onUnmounted,
  type Ref,
  type ComputedRef,
} from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'
import type { WsEnvelope, ProcessingType } from '@/composables/useWebSocket'

/* ------------------------------------------------------------------ */
/* Wire types (contracts/websocket-protocol.md §3.3 + Appendix A)      */
/* ------------------------------------------------------------------ */

/** Payload for `image.enhanced.ready` (§3.3). */
interface ImageEnhancedReadyPayload {
  galleryId: number
  /** Page number, **1-based** per protocol spec. */
  page: number
  enhancedUrl: string
  originalUrl: string
  processingType: ProcessingType
  width: number
  height: number
  fileSize: number
}

/* ------------------------------------------------------------------ */
/* Composable                                                          */
/* ------------------------------------------------------------------ */

/**
 * Reactive AI-enhanced image source switching for a single gallery.
 *
 * @param gid - Reactive gallery ID. When the value changes the composable
 *   automatically unsubscribes from the old gallery topic, clears cached
 *   enhanced URLs, and re-subscribes to the new topic.
 *
 * @example
 * ```vue
 * <script setup lang="ts">
 * const gid = computed(() => Number(route.params.gid))
 * const { getImageUrl, enhancing, enhancedPages, connect, disconnect } =
 *   useEnhancedImage(gid)
 *
 * connect()
 *
 * // In template:
 * // <img :src="getImageUrl(currentIndex, originalUrls[currentIndex])" />
 * </script>
 * ```
 */
export function useEnhancedImage(gid: Ref<number>): {
  enhancedUrls: Ref<Map<number, string>>
  getImageUrl: (page: number, originalUrl: string) => string
  enhancing: Ref<boolean>
  enhancedPages: ComputedRef<number[]>
  connect: () => void
  disconnect: () => void
} {
  /**
   * Reactive map of **0-based** page index → enhanced image URL.
   * Entries are added only after the enhanced image has been successfully
   * preloaded, so consumers never see a URL that would produce a broken image.
   */
  const enhancedUrls = ref(new Map<number, string>())

  /**
   * `true` while at least one enhanced image is being preloaded for the
   * current gallery. Useful for showing a subtle "enhancing…" indicator.
   */
  const enhancing = ref(false)

  /** Sorted list of 0-based page indices that have enhanced versions. */
  const enhancedPages = computed<number[]>(() =>
    Array.from(enhancedUrls.value.keys()).sort((a, b) => a - b),
  )

  /* ---- internal state ---- */

  // Scoped handle on the shared singleton manager: `connect`/`disconnect`
  // are reference-counted (one `/ws` socket per app) and `subscribe`
  // auto-cleans its subscriptions when the host component unmounts.
  const { connect: connectWs, disconnect: disconnectWs, subscribe } = useWebSocket()

  /** Unsubscribe handle for the current gallery topic. */
  let unsubscribe: (() => void) | null = null
  /** `true` once the shared connection reference has been acquired. */
  let wsRefAcquired = false
  /** Number of in-flight preload operations (drives `enhancing`). */
  let activePreloads = 0

  /* ---- helpers ---- */

  /**
   * Return the best available URL for a page: the enhanced URL when one has
   * been preloaded, otherwise the caller-supplied original URL.
   *
   * @param page - **0-based** page index (matches the reader's `currentIndex`).
   * @param originalUrl - Fallback URL (typically from the gallery API).
   */
  function getImageUrl(page: number, originalUrl: string): string {
    return enhancedUrls.value.get(page) ?? originalUrl
  }

  /**
   * Build the enhanced-variant URL against the backend image endpoint
   * (contracts/openapi.yaml `streamGalleryImage`, verified against
   * `ImageProxyController.kt`): `GET /api/v1/image/{galleryId}/{page}` with the
   * `w` (delivery width) and `enhanced` query parameters. The endpoint's page
   * numbers are **0-based**, matching the composable's internal map.
   */
  function buildEnhancedUrl(galleryId: number, pageIndex: number, width: number): string {
    return `/api/v1/image/${galleryId}/${pageIndex}?w=${Math.max(1, Math.round(width))}&enhanced=1`
  }

  /**
   * Handle a single `image.enhanced.ready` payload: preload the enhanced
   * image and, on success, atomically update the reactive map.
   */
  function handleEnhancedReady(payload: ImageEnhancedReadyPayload): void {
    // Protocol pages are 1-based; internal map is 0-based.
    const pageIndex = payload.page - 1
    const url = buildEnhancedUrl(payload.galleryId, pageIndex, payload.width)

    activePreloads++
    enhancing.value = true

    const img = new Image()

    img.onload = () => {
      // Atomic swap — the reader re-renders with the enhanced URL.
      enhancedUrls.value.set(pageIndex, url)
      decrementPreloads()
    }

    img.onerror = () => {
      // Silently keep the original on failure (§3.3 client behavior #5).
      decrementPreloads()
    }

    img.src = url
  }

  function decrementPreloads(): void {
    activePreloads--
    if (activePreloads <= 0) {
      activePreloads = 0
      enhancing.value = false
    }
  }

  /**
   * Subscribe to the gallery-specific enhanced-image topic through the shared
   * manager. The subscription is recorded in the manager's registry and
   * re-established automatically after every reconnect (§1.5 step 4), so no
   * manual re-subscribe on connect is needed. Safe to call while the socket is
   * down — it activates once the connection comes up.
   */
  function subscribeToGallery(galleryId: number): void {
    unsubscribe?.()
    unsubscribe = subscribe<WsEnvelope<ImageEnhancedReadyPayload>>(
      `/topic/gallery/${galleryId}/enhanced`,
      (envelope) => {
        if (envelope.type === 'image.enhanced.ready') {
          handleEnhancedReady(envelope.payload)
        }
      },
    )
  }

  /** Reset all per-gallery state (map, enhancing flag, preload counter). */
  function resetState(): void {
    enhancedUrls.value.clear()
    enhancing.value = false
    activePreloads = 0
  }

  /* ---- public lifecycle ---- */

  /**
   * Acquire a reference to the shared connection (reference-counted singleton)
   * and subscribe to the current gallery's enhanced-image topic. Connection
   * lifecycle, auth headers, and reconnect handling are all owned by the
   * shared manager. Safe to call multiple times.
   */
  function connect(): void {
    if (wsRefAcquired) return
    wsRefAcquired = true
    connectWs()
    subscribeToGallery(gid.value)
  }

  /**
   * Unsubscribe from the gallery topic, release the shared connection
   * reference, and clear all cached enhanced URLs. Safe to call multiple times.
   */
  function disconnect(): void {
    unsubscribe?.()
    unsubscribe = null
    if (wsRefAcquired) {
      wsRefAcquired = false
      disconnectWs()
    }
    resetState()
  }

  /* ---- gallery change handling ---- */

  watch(gid, (newGid, oldGid) => {
    if (newGid === oldGid) return

    unsubscribe?.()
    resetState()

    // Re-subscribe immediately — the manager registers the subscription
    // regardless of the current connection state.
    subscribeToGallery(newGid)
  })

  /* ---- cleanup on component unmount ---- */

  onUnmounted(() => {
    disconnect()
  })

  return { enhancedUrls, getImageUrl, enhancing, enhancedPages, connect, disconnect }
}
