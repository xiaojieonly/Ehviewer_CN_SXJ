/* ==========================================================================
 * AnotherViewer Service Worker
 *
 * Caching architecture (roadmap Phase 3.3):
 *   - App Shell pre-cache on install (entry HTML + manifest + icons).
 *     Vite emits content-hashed JS/CSS bundles whose names are unknown at
 *     SW authoring time, so those are captured into the shell cache at
 *     runtime (CacheFirst — safe because hashed names are immutable).
 *   - API responses (/api/*): NetworkFirst with cache fallback, so gallery
 *     lists stay fresh online but remain readable offline; cached entries
 *     carry a timestamp and are only served within API_MAX_AGE_MS (30 min),
 *     stale entries are evicted instead of being served indefinitely.
 *   - Images: CacheFirst with expiration (max 500 entries, 30 days) and
 *     quota-aware eviction — navigator.storage.estimate() is checked on
 *     every insert and the entry budget halves above 80% of the origin
 *     quota, so cached galleries stay readable offline without unbounded
 *     growth.
 *   - Navigations: NetworkFirst, falling back to the cached app shell.
 *
 * Cache versioning: bump CACHE_NAME (e.g. 'ehviewer-v2') to invalidate
 * every sub-cache on the next activate.
 * ========================================================================== */

'use strict'

const CACHE_NAME = 'ehviewer-v1'

const SHELL_CACHE = `${CACHE_NAME}-shell`
const API_CACHE = `${CACHE_NAME}-api`
const IMAGE_CACHE = `${CACHE_NAME}-images`

const CURRENT_CACHES = new Set([SHELL_CACHE, API_CACHE, IMAGE_CACHE])

/** Image cache limits (roadmap: max 500 entries / 30 days). */
const IMAGE_MAX_ENTRIES = 500
const IMAGE_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000

/**
 * API cache freshness (NetworkFirst fallback): an entry is only served
 * offline while younger than this. 30 minutes keeps offline-read galleries
 * fresh without pinning stale data into the cache forever.
 */
const API_MAX_AGE_MS = 30 * 60 * 1000

/**
 * Storage-pressure threshold (navigator.storage.estimate). Once the origin
 * uses more than this fraction of its quota, the image budget is halved so
 * we shed entries before the browser evicts the whole origin itself.
 */
const STORAGE_PRESSURE_RATIO = 0.8

/** Header stamped onto cached image responses for age checks. */
const CACHED_AT_HEADER = 'x-sw-cached-at'

/**
 * App shell pre-cached on install. Hashed build assets (assets/*.js,
 * assets/*.css) are added to SHELL_CACHE at runtime on first fetch.
 */
const APP_SHELL_URLS = [
  '/',
  '/index.html',
  '/manifest.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
]

/* --------------------------------------------------------------------------
 * Install — pre-cache the app shell
 * ------------------------------------------------------------------------ */

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll(APP_SHELL_URLS))
      .then(() => self.skipWaiting())
  )
})

/* --------------------------------------------------------------------------
 * Activate — purge caches from previous SW versions
 * ------------------------------------------------------------------------ */

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith('ehviewer-') && !CURRENT_CACHES.has(key))
            .map((key) => caches.delete(key))
        )
      )
      .then(() => self.clients.claim())
  )
})

/* --------------------------------------------------------------------------
 * Fetch — route requests to the matching caching strategy
 * ------------------------------------------------------------------------ */

self.addEventListener('fetch', (event) => {
  const { request } = event
  const url = new URL(request.url)

  // Only handle http(s). Leaves WebSocket upgrades (/ws) and data:/blob:
  // URLs alone.
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return

  // Never intercept non-GET requests (mutations must always hit network).
  if (request.method !== 'GET') return

  // Range requests (video/audio seeking, partial image resume) bypass the
  // cache — serving a full cached body for a Range request is invalid.
  if (request.headers.has('range')) return

  // 1. Navigation requests — NetworkFirst, fall back to cached shell.
  if (request.mode === 'navigate') {
    event.respondWith(networkFirstNavigation(request))
    return
  }

  // 2. Images (same-origin /api/v1/image/* and any cross-origin image) —
  //    CacheFirst with expiration. Must precede the generic /api/ rule so
  //    same-origin reader images are never diverted into the API cache.
  if (isImageRequest(request, url)) {
    event.respondWith(cacheFirstImage(request))
    return
  }

  // 3. API GETs (images excluded by rule 2) — NetworkFirst with cache
  //    fallback and a 30-minute freshness window.
  if (url.origin === self.location.origin && url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirstApi(request))
    return
  }

  // 4. Same-origin static assets (hashed JS/CSS bundles, fonts, icons) —
  //    CacheFirst into the shell cache (immutable, content-hashed names).
  if (url.origin === self.location.origin) {
    event.respondWith(cacheFirstStatic(request))
  }
  // Everything else falls through to the browser default (network).
})

/* --------------------------------------------------------------------------
 * Strategy: navigation — NetworkFirst
 * ------------------------------------------------------------------------ */

async function networkFirstNavigation(request) {
  try {
    const response = await fetch(request)
    if (response.ok) {
      const cache = await caches.open(SHELL_CACHE)
      cache.put(request, response.clone())
    }
    return response
  } catch (err) {
    const cache = await caches.open(SHELL_CACHE)
    // Try the exact URL first (SPA routes are all served by index.html),
    // then the cached shell entry.
    const cached = (await cache.match(request)) || (await cache.match('/index.html'))
    return cached || Response.error()
  }
}

/* --------------------------------------------------------------------------
 * Strategy: API — NetworkFirst with cache fallback (30-minute TTL)
 * ------------------------------------------------------------------------ */

async function networkFirstApi(request) {
  try {
    const response = await fetch(request)
    if (response.ok) {
      const cache = await caches.open(API_CACHE)
      cache.put(request, stampResponse(response.clone()))
    }
    return response
  } catch (err) {
    const cache = await caches.open(API_CACHE)
    const cached = await cache.match(request)
    if (cached && isFreshApi(cached)) return cached
    if (cached) await cache.delete(request) // Stale — evict, don't serve.
    // Offline and no fresh cache — return a parseable JSON error so the app
    // can show a friendly offline state instead of a network error.
    return new Response(JSON.stringify({ error: 'offline', message: 'No cached data available' }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' },
    })
  }
}

/* --------------------------------------------------------------------------
 * Strategy: images — CacheFirst with expiration (500 entries / 30 days)
 * ------------------------------------------------------------------------ */

async function cacheFirstImage(request) {
  const cache = await caches.open(IMAGE_CACHE)
  const cached = await cache.match(request)

  if (cached) {
    if (isFreshImage(cached)) return cached
    // Expired — evict and revalidate from network.
    await cache.delete(request)
  }

  try {
    const response = await fetch(request)
    if (isCacheableImage(response)) {
      await cache.put(request, stampResponse(response.clone()))
      await enforceImageLimits(cache)
    }
    return response
  } catch (err) {
    // Network failed — serve the stale entry if we have one (stale beats
    // nothing for an offline reader).
    return cached || Response.error()
  }
}

function isImageRequest(request, url) {
  if (request.destination === 'image') return true
  // Reader images (streamGalleryImage) and proxy images live under
  // /api/v1/image/; match the prefix (and the legacy /api/image/ form) so
  // query variants like ?w= or ?enhanced= stay inside this rule. Cache keys
  // are the full request URL including the query string, so each width /
  // enhancement variant is cached separately.
  if (url.pathname.startsWith('/api/v1/image/')) return true
  if (url.pathname.startsWith('/api/image/')) return true
  // pathname never carries the query string, so extension checks are
  // query-agnostic by construction.
  return /\.(png|jpe?g|gif|webp|avif|svg|ico)$/i.test(url.pathname)
}

function isCacheableImage(response) {
  // Same-origin 200s, or cross-origin opaque responses (CDN thumbnails).
  return response.ok || response.type === 'opaque'
}

/** Clone the response with a cache timestamp header attached. */
function stampResponse(response) {
  // Opaque responses (cross-origin, no-cors) report status 0 and expose no
  // writable headers — the Response constructor rejects status 0 with a
  // RangeError. Cache them unstamped instead; isFreshImage() then treats
  // them as due-for-revalidation online, and they still serve offline.
  if (response.type === 'opaque') return response
  const headers = new Headers(response.headers)
  headers.set(CACHED_AT_HEADER, String(Date.now()))
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  })
}

function isFresh(response, maxAgeMs) {
  const cachedAt = Number(response.headers.get(CACHED_AT_HEADER))
  if (!cachedAt) return false // Legacy entry without a stamp — revalidate.
  return Date.now() - cachedAt < maxAgeMs
}

function isFreshImage(response) {
  return isFresh(response, IMAGE_MAX_AGE_MS)
}

function isFreshApi(response) {
  return isFresh(response, API_MAX_AGE_MS)
}

/**
 * Evict oldest entries beyond the image budget. cache.keys() preserves
 * insertion order, so deleting from the front approximates LRU.
 *
 * The budget is quota-aware: navigator.storage.estimate() is consulted on
 * every pass, and under storage pressure (> STORAGE_PRESSURE_RATIO of the
 * origin quota in use) it is halved to proactively free space.
 */
async function enforceImageLimits(cache) {
  let maxEntries = IMAGE_MAX_ENTRIES
  if (self.navigator && self.navigator.storage && self.navigator.storage.estimate) {
    try {
      const estimate = await self.navigator.storage.estimate()
      const usage = estimate.usage || 0
      const quota = estimate.quota || 0
      if (quota > 0 && usage / quota > STORAGE_PRESSURE_RATIO) {
        maxEntries = Math.floor(IMAGE_MAX_ENTRIES / 2)
      }
    } catch (err) {
      // estimate() unavailable — keep the default budget.
    }
  }
  const keys = await cache.keys()
  if (keys.length <= maxEntries) return
  const excess = keys.slice(0, keys.length - maxEntries)
  await Promise.all(excess.map((key) => cache.delete(key)))
}

/* --------------------------------------------------------------------------
 * Strategy: same-origin static assets — CacheFirst (immutable bundles)
 * ------------------------------------------------------------------------ */

async function cacheFirstStatic(request) {
  const cache = await caches.open(SHELL_CACHE)
  const cached = await cache.match(request)
  if (cached) return cached

  try {
    const response = await fetch(request)
    if (response.ok) {
      await cache.put(request, response.clone())
    }
    return response
  } catch (err) {
    return Response.error()
  }
}

/* --------------------------------------------------------------------------
 * Message handler — cache management from the app
 *
 *   { type: 'SKIP_WAITING' }       → activate the waiting worker (updates)
 *   { type: 'CLEAR_IMAGE_CACHE' }  → wipe the image cache (settings page)
 *   { type: 'GET_CACHE_STATS' }    → reply with per-cache entry counts
 * ------------------------------------------------------------------------ */

self.addEventListener('message', (event) => {
  const data = event.data
  if (!data || typeof data.type !== 'string') return

  switch (data.type) {
    case 'SKIP_WAITING':
      self.skipWaiting()
      break

    case 'CLEAR_IMAGE_CACHE':
      event.waitUntil(
        caches
          .delete(IMAGE_CACHE)
          .then(() => reply(event, { type: 'IMAGE_CACHE_CLEARED' }))
      )
      break

    case 'GET_CACHE_STATS':
      event.waitUntil(
        collectCacheStats().then((stats) =>
          reply(event, { type: 'CACHE_STATS', stats })
        )
      )
      break
  }
})

function reply(event, message) {
  // Prefer the source client; fall back to broadcasting to everyone.
  if (event.source && typeof event.source.postMessage === 'function') {
    event.source.postMessage(message)
    return
  }
  return self.clients.matchAll().then((clients) => {
    clients.forEach((client) => client.postMessage(message))
  })
}

async function collectCacheStats() {
  const stats = {}
  for (const name of [SHELL_CACHE, API_CACHE, IMAGE_CACHE]) {
    const cache = await caches.open(name)
    const keys = await cache.keys()
    stats[name] = keys.length
  }
  if (self.navigator && self.navigator.storage && self.navigator.storage.estimate) {
    const estimate = await self.navigator.storage.estimate()
    stats.storageUsage = estimate.usage || 0
    stats.storageQuota = estimate.quota || 0
  }
  return stats
}
