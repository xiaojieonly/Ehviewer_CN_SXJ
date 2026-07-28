/**
 * AI Enhanced Image Source Switching (I3).
 *
 * Subscribes to the WebSocket topic `/topic/gallery/{gid}/enhanced` for
 * `image.enhanced.ready` notifications (contracts/websocket-protocol.md §3.3).
 * When an enhanced version of a page becomes available, the image is preloaded
 * in the background and the reactive URL map is updated only after the preload
 * succeeds — guaranteeing a seamless hot-swap with no flash or broken image.
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
import { Client } from '@stomp/stompjs'
import type { StompSubscription, IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'

/* ------------------------------------------------------------------ */
/* Wire types (contracts/websocket-protocol.md §2 + §3.3 + Appendix A) */
/* ------------------------------------------------------------------ */

/** Unified server → client envelope (§2). */
interface WsEnvelope<T = unknown> {
  type: string
  timestamp: number
  version: string
  payload: T
}

/** Image processing type (§3.2). */
type ProcessingType =
  | 'UPSCALE_2X'
  | 'UPSCALE_4X'
  | 'DENOISE'
  | 'DENOISE_UPSCALE'

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
 *   enhanced URLs, and re-subscribes to the new topic (if connected).
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
  let client: Client | null = null
  let subscription: StompSubscription | null = null
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
   * Handle a single `image.enhanced.ready` payload: preload the enhanced
   * image and, on success, atomically update the reactive map.
   */
  function handleEnhancedReady(payload: ImageEnhancedReadyPayload): void {
    // Protocol pages are 1-based; internal map is 0-based.
    const pageIndex = payload.page - 1
    const url = payload.enhancedUrl

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

  /** Subscribe to the gallery-specific enhanced-image topic. */
  function subscribeToGallery(galleryId: number): void {
    if (!client || !client.connected) return

    subscription = client.subscribe(
      `/topic/gallery/${galleryId}/enhanced`,
      (message: IMessage) => {
        try {
          const envelope = JSON.parse(message.body) as WsEnvelope<ImageEnhancedReadyPayload>
          if (envelope.type === 'image.enhanced.ready') {
            handleEnhancedReady(envelope.payload)
          }
        } catch {
          // Ignore malformed messages (§7.2: log + discard unknown types).
        }
      },
    )
  }

  /** Unsubscribe from the current gallery topic (idempotent). */
  function unsubscribeCurrent(): void {
    subscription?.unsubscribe()
    subscription = null
  }

  /** Reset all per-gallery state (map, enhancing flag, preload counter). */
  function resetState(): void {
    enhancedUrls.value.clear()
    enhancing.value = false
    activePreloads = 0
  }

  /* ---- public lifecycle ---- */

  /**
   * Create the STOMP client (if not already created) and activate it.
   * On successful connection the composable subscribes to the current
   * gallery's enhanced-image topic. Re-subscription after automatic
   * reconnects is handled by the `onConnect` callback.
   */
  function connect(): void {
    if (client) return

    client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        subscribeToGallery(gid.value)
      },
    })

    client.activate()
  }

  /**
   * Unsubscribe, deactivate the STOMP client, and clear all cached
   * enhanced URLs. Safe to call multiple times.
   */
  function disconnect(): void {
    unsubscribeCurrent()
    client?.deactivate()
    client = null
    resetState()
  }

  /* ---- gallery change handling ---- */

  watch(gid, (newGid, oldGid) => {
    if (newGid === oldGid) return

    unsubscribeCurrent()
    resetState()

    // Re-subscribe immediately if the client is already connected.
    if (client?.connected) {
      subscribeToGallery(newGid)
    }
  })

  /* ---- cleanup on component unmount ---- */

  onUnmounted(() => {
    disconnect()
  })

  return { enhancedUrls, getImageUrl, enhancing, enhancedPages, connect, disconnect }
}
