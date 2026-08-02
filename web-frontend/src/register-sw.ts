/**
 * Service Worker registration (roadmap Phase 3.3 — PWA infrastructure).
 *
 * Production-only: the Vite dev server live-reloads modules, which fights
 * the SW cache and makes HMR unreliable, so registration is skipped in dev.
 *
 * Update flow: when a new SW finishes installing while an older one still
 * controls the page, we tell the new worker to SKIP_WAITING (handled in
 * sw.js). Once it takes control (`controllerchange`), the page reloads so
 * the app shell matches the cached assets.
 */

/** Guards against double registration / double reload wiring. */
let registered = false
let reloading = false

export function registerServiceWorker(): void {
  if (!import.meta.env.PROD) return
  if (!('serviceWorker' in navigator)) return
  if (registered) return
  registered = true

  // Was this page loaded under an existing SW? Used below to tell a genuine
  // update (old controller → new controller) apart from the first-install
  // claim, where there was no controller to begin with.
  const hadControllerAtLoad = navigator.serviceWorker.controller !== null

  // A new worker took control — reload once so UI and cached shell match.
  // Skip the reload on first visit: sw.js claims clients on activate, which
  // also fires controllerchange, but there is no stale UI to refresh then.
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!hadControllerAtLoad) return
    if (reloading) return
    reloading = true
    window.location.reload()
  })

  // Register after first paint so the SW install doesn't contend with
  // the initial app render for network/CPU.
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('/sw.js')
      .then((registration) => {
        registration.addEventListener('updatefound', () => {
          const installing = registration.installing
          if (!installing) return
          installing.addEventListener('statechange', () => {
            // "installed" + existing controller = update ready (not first
            // visit). Ask the new worker to activate immediately.
            if (installing.state === 'installed' && navigator.serviceWorker.controller) {
              installing.postMessage({ type: 'SKIP_WAITING' })
            }
          })
        })
      })
      .catch((error) => {
        // SW is progressive enhancement — log and carry on without it.
        console.error('[sw] registration failed:', error)
      })
  })
}

/**
 * Ask the active SW to wipe the image cache (sw.js message protocol).
 * Used by the settings page cache-management controls (roadmap 3.5).
 * Resolves when the SW confirms; rejects on timeout or if no SW controls
 * the page.
 */
export function clearImageCache(timeoutMs = 5000): Promise<void> {
  return new Promise((resolve, reject) => {
    const controller = navigator.serviceWorker?.controller
    if (!controller) {
      reject(new Error('No active service worker'))
      return
    }

    let timer: ReturnType<typeof setTimeout> | undefined
    const onMessage = (event: MessageEvent) => {
      if (event.data?.type === 'IMAGE_CACHE_CLEARED') {
        navigator.serviceWorker.removeEventListener('message', onMessage)
        if (timer !== undefined) clearTimeout(timer)
        resolve()
      }
    }

    navigator.serviceWorker.addEventListener('message', onMessage)
    controller.postMessage({ type: 'CLEAR_IMAGE_CACHE' })

    timer = setTimeout(() => {
      navigator.serviceWorker.removeEventListener('message', onMessage)
      reject(new Error('Timed out waiting for image cache clear'))
    }, timeoutMs)
  })
}

/**
 * Cache statistics reported by the SW (sw.js `collectCacheStats`).
 * Entry counts are keyed by the SW's versioned cache names
 * (e.g. `anotherviewer-v1-shell`), so iterate entries rather than relying on
 * fixed keys.
 */
export interface CacheStats {
  /** Entry count per SW cache, keyed by cache name. */
  [cacheName: string]: number | undefined
  /** Bytes used by this origin, from navigator.storage.estimate(). */
  storageUsage?: number
  /** Storage quota granted to this origin, in bytes. */
  storageQuota?: number
}

/**
 * Ask the active SW for per-cache entry counts and the origin's storage
 * usage/quota (sw.js message protocol, backed by navigator.storage.estimate).
 * Intended for the settings page cache-management UI (roadmap 3.5).
 * Resolves when the SW replies; rejects on timeout or if no SW controls
 * the page.
 */
export function getCacheStats(timeoutMs = 5000): Promise<CacheStats> {
  return new Promise((resolve, reject) => {
    const controller = navigator.serviceWorker?.controller
    if (!controller) {
      reject(new Error('No active service worker'))
      return
    }

    let timer: ReturnType<typeof setTimeout> | undefined
    const onMessage = (event: MessageEvent) => {
      if (event.data?.type === 'CACHE_STATS' && event.data.stats) {
        navigator.serviceWorker.removeEventListener('message', onMessage)
        if (timer !== undefined) clearTimeout(timer)
        resolve(event.data.stats as CacheStats)
      }
    }

    navigator.serviceWorker.addEventListener('message', onMessage)
    controller.postMessage({ type: 'GET_CACHE_STATS' })

    timer = setTimeout(() => {
      navigator.serviceWorker.removeEventListener('message', onMessage)
      reject(new Error('Timed out waiting for cache stats'))
    }, timeoutMs)
  })
}
