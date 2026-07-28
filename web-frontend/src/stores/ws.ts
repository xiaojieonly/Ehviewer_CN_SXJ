/**
 * WebSocket connection store (I2 — real-time feedback).
 *
 * A thin Pinia façade over the singleton connection manager in
 * `composables/useWebSocket.ts`, giving any component reactive access to the
 * connection lifecycle plus a ready-made map of live download progress.
 *
 * Single source of truth: {@link connectionState} and {@link lastError} are the
 * very same shared refs owned by the connection manager — they are re-exposed
 * here (not copied), so a status change made by the manager is instantly
 * visible to every component reading either the store or the composable.
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  connect as wsConnect,
  disconnect as wsDisconnect,
  subscribe,
  connectionState,
  lastError,
  TOPIC_DOWNLOAD_ALL,
} from '@/composables/useWebSocket'
import type { DownloadProgress } from '@/composables/useWebSocket'

export const useWsStore = defineStore('ws', () => {
  /**
   * Latest download-progress sample per gallery id (gid), fed from the legacy
   * `/topic/download/all` bare-DTO stream. Reactive: Vue tracks `Map` mutations
   * performed through the ref.
   */
  const downloadProgress = ref<Map<number, DownloadProgress>>(new Map())

  /**
   * Unsubscribe handle for the store's own download-progress subscription.
   * Module-scoped (not returned) — it is internal bookkeeping, not state.
   */
  let unsubscribeDownloads: (() => void) | null = null

  /** Convenience boolean mirroring `connectionState === 'connected'`. */
  const isConnected = computed(() => connectionState.value === 'connected')

  /**
   * Record a download-progress sample, keyed by gallery id. Called for every
   * message on the download-progress stream; also safe to call directly (e.g.
   * to seed state from a REST snapshot after a reconnect — contract §5.3).
   *
   * @param progress - A bare download-progress DTO.
   */
  function updateDownloadProgress(progress: DownloadProgress): void {
    downloadProgress.value.set(progress.gid, progress)
  }

  /**
   * Acquire the shared connection (reference-counted) and, on first call,
   * subscribe to the all-downloads stream so {@link downloadProgress} stays
   * live. Idempotent: repeated calls only add connection references.
   */
  function connect(): void {
    wsConnect()
    if (!unsubscribeDownloads) {
      unsubscribeDownloads = subscribe<DownloadProgress>(
        TOPIC_DOWNLOAD_ALL,
        updateDownloadProgress,
      )
    }
  }

  /**
   * Release the store's connection reference and drop its download-progress
   * subscription. The underlying socket is deactivated only when the last
   * reference across the app is released.
   */
  function disconnect(): void {
    unsubscribeDownloads?.()
    unsubscribeDownloads = null
    wsDisconnect()
  }

  /**
   * Look up the latest progress sample for a gallery.
   *
   * @param gid - Gallery id.
   * @returns the latest sample, or `undefined` if none has been received.
   */
  function progressFor(gid: number): DownloadProgress | undefined {
    return downloadProgress.value.get(gid)
  }

  return {
    connectionState,
    lastError,
    downloadProgress,
    isConnected,
    connect,
    disconnect,
    updateDownloadProgress,
    progressFor,
  }
})
