/**
 * WebSocket connection manager (I2 — real-time feedback).
 *
 * A single, app-wide STOMP-over-SockJS client lives at module scope and is
 * shared by every consumer through reference counting, so mounting/unmounting
 * views never tears down a connection another view still needs.
 *
 * Contract: `contracts/websocket-protocol.md` (FROZEN, v1.0.0).
 *
 * Highlights:
 * - **Singleton + refcount**: one `Client` for the whole app; `connect()` /
 *   `disconnect()` increment / decrement a reference counter and only activate
 *   or deactivate the underlying socket when the counter crosses zero.
 * - **Exponential backoff**: native `@stomp/stompjs` EXPONENTIAL reconnection
 *   (1s → 2s → 4s → 8s … capped at 30s), reset on every successful connect
 *   (contract §1.5).
 * - **Heartbeat**: 10s outgoing / 10s incoming (contract §1.4).
 * - **Token auth**: the bearer token is read from `localStorage` and sent in
 *   the STOMP `CONNECT` headers (`login` per contract §1.3, plus an
 *   `Authorization: Bearer …` header for clients/middleware that expect it).
 * - **Auto re-subscribe**: every subscription is recorded in a registry and
 *   re-established automatically after a reconnect (contract §1.5 step 4).
 * - **Reactive state**: {@link connectionState} / {@link lastError} are shared
 *   refs — the single source of truth re-exposed by `stores/ws.ts`.
 *
 * Backward compatibility: the legacy bare-DTO helpers {@link subscribeDownload}
 * and {@link subscribeAll} (topics `/topic/download/{gid}` and
 * `/topic/download/all`, deprecated per contract §7.4) are preserved — they are
 * thin adapters over the same subscription engine and still return the live
 * `StompSubscription` so existing callers (e.g. `DownloadView.vue`) keep
 * type-checking unchanged.
 */
import {
  ref,
  computed,
  onUnmounted,
  getCurrentInstance,
  type Ref,
  type ComputedRef,
} from 'vue'
import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import type { IMessage, StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'

/* ------------------------------------------------------------------ */
/* Wire types (contracts/websocket-protocol.md)                        */
/* ------------------------------------------------------------------ */

/** Reactive lifecycle state of the shared connection. */
export type ConnectionState = 'connecting' | 'connected' | 'disconnected' | 'error'

/**
 * Legacy bare download-progress DTO (contract §2.1 / §7.4). Superseded by the
 * enveloped `download.progress` payload but still published by the server on
 * `/topic/download/{gid}` and `/topic/download/all` during migration. Kept
 * verbatim so existing consumers continue to work.
 */
export interface DownloadProgress {
  gid: number
  state: number
  downloaded: number
  total: number
  speed: number
  label: number
}

/** Unified server → client envelope (contract §2). */
export interface WsEnvelope<T = unknown> {
  /** Message type identifier (`{domain}.{event}`). */
  type: string
  /** Epoch millis when the event occurred server-side. */
  timestamp: number
  /** Protocol version (semver) for forward compatibility. */
  version: string
  /** Payload — shape determined by {@link type}. */
  payload: T
}

/** Image processing type (contract §3.2). */
export type ProcessingType = 'UPSCALE_2X' | 'UPSCALE_4X' | 'DENOISE' | 'DENOISE_UPSCALE'

/** `process.started` payload (contract §3.2). */
export interface ProcessStartedPayload {
  taskId: string
  galleryId: number
  totalPages: number
  processingType: ProcessingType
  processorId: string
}

/** `process.progress` payload (contract §3.2). */
export interface ProcessProgressPayload {
  taskId: string
  galleryId: number
  processedPages: number
  totalPages: number
  currentPage: number
}

/** `process.completed` payload (contract §3.2). */
export interface ProcessCompletedPayload {
  taskId: string
  galleryId: number
  enhancedPages: number
  elapsedMs: number
}

/** `process.failed` payload (contract §3.2). */
export interface ProcessFailedPayload {
  taskId: string
  galleryId: number
  error: string
  failedPage?: number
  processedBeforeFailure: number
}

/** Union of all image-processing payloads. */
export type ProcessPayload =
  | ProcessStartedPayload
  | ProcessProgressPayload
  | ProcessCompletedPayload
  | ProcessFailedPayload

/**
 * An enveloped processing event as delivered by {@link subscribeProcessing}.
 * Discriminate on {@link WsEnvelope.type} (`process.started`, `process.progress`,
 * `process.completed`, `process.failed`).
 */
export type ProcessingEvent = WsEnvelope<ProcessPayload>

/* ------------------------------------------------------------------ */
/* Constants                                                           */
/* ------------------------------------------------------------------ */

/** SockJS endpoint — relative path, no hardcoded host (proxied to the server). */
const WS_ENDPOINT = '/ws'
/** localStorage key holding the bearer token (see `stores/auth.ts`). */
const TOKEN_STORAGE_KEY = 'token'
/** Backoff initial delay (contract §1.5). */
const INITIAL_RECONNECT_DELAY_MS = 1000
/** Backoff ceiling (contract §1.5). */
const MAX_RECONNECT_DELAY_MS = 30000
/** STOMP heartbeat interval, both directions (contract §1.4). */
const HEARTBEAT_MS = 10000

/** Legacy all-downloads topic (bare DTO, contract §7.4). */
export const TOPIC_DOWNLOAD_ALL = '/topic/download/all'
/** Legacy single-gallery topic factory (bare DTO, contract §7.4). */
const topicDownloadOne = (gid: number): string => `/topic/download/${gid}`
/** All processing events topic (contract §6.1 / Appendix B). */
const TOPIC_PROCESS_ALL = '/topic/process/all'

/* ------------------------------------------------------------------ */
/* Shared reactive state — single source of truth                      */
/* ------------------------------------------------------------------ */

/** Reactive connection lifecycle state, shared across the whole app. */
export const connectionState = ref<ConnectionState>('disconnected')

/** Human-readable description of the last connection error, if any. */
export const lastError = ref<string | null>(null)

/* ------------------------------------------------------------------ */
/* Singleton internals                                                 */
/* ------------------------------------------------------------------ */

let client: Client | null = null
let refCount = 0

/**
 * A registered subscription. The registry survives reconnects so every active
 * subscription can be re-established automatically once the socket is back up.
 */
interface RegistryEntry {
  id: number
  destination: string
  onMessage: (message: IMessage) => void
  /** Live STOMP subscription handle (null while disconnected). */
  live: StompSubscription | null
}

const registry = new Map<number, RegistryEntry>()
let nextRegistryId = 1

/** Read the bearer token from localStorage, tolerating storage failures. */
function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_STORAGE_KEY)
  } catch {
    return null
  }
}

/**
 * Build the STOMP `CONNECT` headers. The token is sent as `login` (the wire
 * format the server expects — contract §1.3) and mirrored as a conventional
 * `Authorization: Bearer …` header.
 */
function buildConnectHeaders(): { [key: string]: string } {
  const headers: { [key: string]: string } = { passcode: '' }
  const token = readToken()
  if (token) {
    headers.login = token
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

/**
 * Build a STOMP message handler that JSON-parses the body into `T` and
 * discards malformed messages with a warning (contract §7.2: log + discard).
 */
function makeJsonHandler<T>(
  destination: string,
  callback: (parsed: T) => void,
): (message: IMessage) => void {
  return (message: IMessage) => {
    try {
      callback(JSON.parse(message.body) as T)
    } catch (error) {
      console.warn(`[ws] discarding malformed message on ${destination}`, error)
    }
  }
}

/** Re-establish every registered subscription on the live client. */
function resubscribeAll(): void {
  if (!client) return
  for (const entry of registry.values()) {
    entry.live = client.subscribe(entry.destination, entry.onMessage)
  }
}

/**
 * Register a subscription. If the client is already connected the live STOMP
 * subscription is created immediately; otherwise it is created on the next
 * {@link resubscribeAll} (i.e. on connect / reconnect).
 *
 * @returns the registry id, used to remove the subscription later.
 */
function addSubscription(
  destination: string,
  onMessage: (message: IMessage) => void,
): number {
  const id = nextRegistryId++
  const entry: RegistryEntry = { id, destination, onMessage, live: null }
  if (client?.connected) {
    entry.live = client.subscribe(destination, onMessage)
  }
  registry.set(id, entry)
  return id
}

/** Remove a registered subscription and tear down its live handle. */
function removeSubscription(id: number): void {
  const entry = registry.get(id)
  if (!entry) return
  try {
    entry.live?.unsubscribe()
  } catch {
    // The socket may already be closed — nothing to unsubscribe.
  }
  registry.delete(id)
}

/** Lazily create the singleton STOMP client and wire its lifecycle callbacks. */
function ensureClient(): Client {
  if (client) return client

  client = new Client({
    webSocketFactory: () => new SockJS(WS_ENDPOINT),
    // Exponential backoff: 1s → 2s → 4s → 8s … capped at 30s; the library
    // resets to the initial delay on every successful connect (contract §1.5).
    reconnectDelay: INITIAL_RECONNECT_DELAY_MS,
    maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
    connectHeaders: buildConnectHeaders(),
    onConnect: () => {
      connectionState.value = 'connected'
      lastError.value = null
      resubscribeAll()
    },
    onDisconnect: () => {
      if (refCount === 0) connectionState.value = 'disconnected'
    },
    onStompError: (frame) => {
      connectionState.value = 'error'
      lastError.value = frame.headers['message'] || frame.body || 'STOMP protocol error'
    },
    onWebSocketClose: () => {
      // Still wanted (refCount > 0) → the library is about to retry: connecting.
      // Otherwise this close is the result of an intentional disconnect.
      connectionState.value = refCount > 0 ? 'connecting' : 'disconnected'
    },
    onWebSocketError: () => {
      lastError.value = 'WebSocket transport error'
    },
  })

  return client
}

/* ------------------------------------------------------------------ */
/* Module-level public API (usable outside component setup)            */
/* ------------------------------------------------------------------ */

/**
 * Acquire a reference to the shared connection and activate it on first use.
 * Reference-counted: pair every call with a {@link disconnect}.
 */
export function connect(): void {
  refCount++
  const c = ensureClient()
  // Refresh auth headers on each (re)activation in case the token rotated.
  c.connectHeaders = buildConnectHeaders()
  if (!c.active) {
    connectionState.value = 'connecting'
    c.activate()
  }
}

/**
 * Release a reference to the shared connection. The socket is deactivated only
 * when the last reference is released. Safe to call more times than
 * {@link connect} (the counter never goes below zero).
 */
export function disconnect(): void {
  if (refCount > 0) refCount--
  if (refCount === 0 && client) {
    void client.deactivate()
    connectionState.value = 'disconnected'
    for (const entry of registry.values()) entry.live = null
  }
}

/**
 * Subscribe to any destination, JSON-parsing each message body into `T`.
 *
 * @param destination - STOMP destination, e.g. `/topic/download/progress`.
 * @param callback - Invoked with the parsed payload for each message.
 * @returns an unsubscribe function that removes the subscription.
 */
export function subscribe<T>(
  destination: string,
  callback: (message: T) => void,
): () => void {
  const id = addSubscription(destination, makeJsonHandler(destination, callback))
  return () => removeSubscription(id)
}

/**
 * Subscribe to download progress for a single gallery on the legacy bare-DTO
 * topic `/topic/download/{gid}` (contract §7.4 — deprecated but still served).
 *
 * @returns the live `StompSubscription`, or `undefined` if not yet connected.
 */
export function subscribeDownload(
  gid: number,
  callback: (progress: DownloadProgress) => void,
): StompSubscription | undefined {
  const destination = topicDownloadOne(gid)
  const id = addSubscription(destination, makeJsonHandler(destination, callback))
  return registry.get(id)?.live ?? undefined
}

/**
 * Subscribe to download progress for all galleries on the legacy bare-DTO topic
 * `/topic/download/all` (contract §7.4 — deprecated but still served).
 *
 * @returns the live `StompSubscription`, or `undefined` if not yet connected.
 */
export function subscribeAll(
  callback: (progress: DownloadProgress) => void,
): StompSubscription | undefined {
  const id = addSubscription(TOPIC_DOWNLOAD_ALL, makeJsonHandler(TOPIC_DOWNLOAD_ALL, callback))
  return registry.get(id)?.live ?? undefined
}

/**
 * Subscribe to image-processing events for a single gallery.
 *
 * The frozen contract exposes processing events on `/topic/process/all` (and
 * per-`taskId`), keyed by `taskId` rather than gallery — there is no
 * per-gallery process topic. This helper subscribes to `/topic/process/all`
 * and filters client-side to events whose `payload.galleryId === gid`.
 *
 * @param gid - Gallery ID to filter processing events for.
 * @param callback - Invoked with each matching enveloped processing event.
 * @returns an unsubscribe function.
 */
export function subscribeProcessing(
  gid: number,
  callback: (event: ProcessingEvent) => void,
): () => void {
  return subscribe<ProcessingEvent>(TOPIC_PROCESS_ALL, (event) => {
    if (
      event &&
      typeof event.type === 'string' &&
      event.type.startsWith('process.') &&
      event.payload?.galleryId === gid
    ) {
      callback(event)
    }
  })
}

/* ------------------------------------------------------------------ */
/* Component composable                                                */
/* ------------------------------------------------------------------ */

/** Return shape of {@link useWebSocket}. */
export interface UseWebSocketReturn {
  /** Shared reactive connection state. */
  connectionState: Ref<ConnectionState>
  /** Shared reactive last-error message. */
  lastError: Ref<string | null>
  /** Convenience boolean — `true` when {@link connectionState} is `connected`. */
  connected: ComputedRef<boolean>
  /** Acquire a reference to the shared connection (reference-counted). */
  connect: () => void
  /** Release a reference to the shared connection (reference-counted). */
  disconnect: () => void
  /** Generic JSON subscription; returns an unsubscribe function. */
  subscribe: <T>(destination: string, callback: (message: T) => void) => () => void
  /** Legacy per-gallery download subscription (bare DTO). */
  subscribeDownload: (
    gid: number,
    callback: (progress: DownloadProgress) => void,
  ) => StompSubscription | undefined
  /** Legacy all-galleries download subscription (bare DTO). */
  subscribeAll: (
    callback: (progress: DownloadProgress) => void,
  ) => StompSubscription | undefined
  /** Gallery-scoped image-processing subscription. */
  subscribeProcessing: (
    gid: number,
    callback: (event: ProcessingEvent) => void,
  ) => () => void
}

/**
 * Component-facing WebSocket composable.
 *
 * Wraps the module-level singleton so that every subscription created through
 * the returned helpers is tracked and automatically torn down when the host
 * component unmounts, and the connection reference is released. Subscriptions
 * created via the bare module-level functions are NOT auto-cleaned and must be
 * unsubscribed manually (used by `stores/ws.ts`).
 *
 * @example
 * ```vue
 * <script setup lang="ts">
 * const { connected, connect, subscribeAll } = useWebSocket()
 * watch(connected, (up) => { if (up) subscribeAll(handleProgress) }, { immediate: true })
 * onMounted(() => connect())
 * </script>
 * ```
 */
export function useWebSocket(): UseWebSocketReturn {
  /** Registry ids owned by this component instance. */
  const owned = new Set<number>()

  const connected = computed(() => connectionState.value === 'connected')

  /** `addSubscription` + ownership tracking for component-scoped cleanup. */
  function track(destination: string, onMessage: (message: IMessage) => void): number {
    const id = addSubscription(destination, onMessage)
    owned.add(id)
    return id
  }

  /** Remove a subscription and drop its ownership record. */
  function release(id: number): void {
    owned.delete(id)
    removeSubscription(id)
  }

  function scopedSubscribe<T>(
    destination: string,
    callback: (message: T) => void,
  ): () => void {
    const id = track(destination, makeJsonHandler(destination, callback))
    return () => release(id)
  }

  function scopedSubscribeDownload(
    gid: number,
    callback: (progress: DownloadProgress) => void,
  ): StompSubscription | undefined {
    const destination = topicDownloadOne(gid)
    const id = track(destination, makeJsonHandler(destination, callback))
    return registry.get(id)?.live ?? undefined
  }

  function scopedSubscribeAll(
    callback: (progress: DownloadProgress) => void,
  ): StompSubscription | undefined {
    const id = track(TOPIC_DOWNLOAD_ALL, makeJsonHandler(TOPIC_DOWNLOAD_ALL, callback))
    return registry.get(id)?.live ?? undefined
  }

  function scopedSubscribeProcessing(
    gid: number,
    callback: (event: ProcessingEvent) => void,
  ): () => void {
    const id = track(
      TOPIC_PROCESS_ALL,
      makeJsonHandler<ProcessingEvent>(TOPIC_PROCESS_ALL, (event) => {
        if (
          event &&
          typeof event.type === 'string' &&
          event.type.startsWith('process.') &&
          event.payload?.galleryId === gid
        ) {
          callback(event)
        }
      }),
    )
    return () => release(id)
  }

  // Only register the unmount hook inside a real component setup context so the
  // composable can also be exercised in isolation (e.g. unit tests).
  if (getCurrentInstance()) {
    onUnmounted(() => {
      for (const id of owned) removeSubscription(id)
      owned.clear()
      disconnect()
    })
  }

  return {
    connectionState,
    lastError,
    connected,
    connect,
    disconnect,
    subscribe: scopedSubscribe,
    subscribeDownload: scopedSubscribeDownload,
    subscribeAll: scopedSubscribeAll,
    subscribeProcessing: scopedSubscribeProcessing,
  }
}
