<template>
  <div class="reader-view" :style="readerRootStyle">
    <div v-if="loadState === 'loading'" class="reader-view__center">
      <ProgressSpinner size="large" />
      <p class="reader-view__hint">正在加载画廊…</p>
    </div>

    <div v-else-if="loadState === 'error'" class="reader-view__center" role="alert">
      <p class="reader-view__hint">{{ errorMessage }}</p>
      <button type="button" class="reader-view__retry" @click="load">重试</button>
    </div>

    <ImageReader
      v-else
      ref="readerRef"
      :gid="gid"
      :title="title"
      :total-pages="totalPages"
      :current-page="currentPage"
      :direction="direction"
      :page-mode="pageModePref"
      :mode="resolvedMode"
      :zoom="zoom"
      :brightness="brightness"
      :auto-play="autoPlay"
      :auto-play-progress="autoPlayProgress"
      :enhanced-urls="enhancedUrls"
      @update:current-page="onPageChange"
      @update:direction="direction = $event"
      @update:page-mode="pageModePref = $event"
      @update:zoom="zoom = $event"
      @update:brightness="brightness = $event"
      @update:auto-play="autoPlay = $event"
      @prev="prevPage"
      @next="nextPage"
      @back="goBack"
    />

    <div
      v-if="degraded && loadState === 'ready'"
      class="reader-view__degraded"
      role="status"
      data-testid="reader-degraded-banner"
    >
      画廊详情不可用（站点不可达或本地无元数据）——无法加载的页面会在页内提示
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * ReaderView.vue (S3) — orchestrates the gallery reader, the web counterpart
 * of `GalleryActivity`:
 *
 * - Loads the gallery (`GET /api/v1/gallery/{gid}`) and resolves the starting
 *   page from the `/reader/:gid/:page?` route (0-based, deep-linkable).
 * - Owns the reader state the chrome components bind to: current page,
 *   reading direction (LTR/RTL/vertical), page-mode preference, zoom,
 *   brightness and auto-play. Direction / page mode / brightness persist to
 *   localStorage (the web equivalent of `Settings.PutReadingDirection`…).
 * - Resolves the effective mode: vertical direction forces scroll; `auto`
 *   page mode follows the viewport — dual at `min-aspect-ratio: 1/1`
 *   (responsive-strategy §6 rules 1–3), single otherwise.
 * - Navigation is spread-aware in dual mode (cover alone, then (2,3), (4,5)…
 *   in 1-based terms), matching `SpreadLayoutManager` paging.
 * - Keyboard: ←/→ (or A/D) turn pages — mirrored under RTL — Home/End jump,
 *   Space/Esc toggle the chrome, +/− zoom.
 * - Auto-play advances on a 100 ms-ticked timer (progress drives the countdown
 *   chip) and stops at the last page; it pauses while the tab is hidden.
 * - WebSocket: subscribes to `/topic/gallery/{gid}/enhanced` and hot-swaps
 *   `image.enhanced.ready` pages per websocket-protocol §3.3 (preload the
 *   enhanced file, swap atomically on load, silently keep the original on
 *   failure).
 * - The current page is synced back to the route (debounced) so refresh /
 *   back-navigation restore the reading position.
 * - F1: the visit is written to server history on entry, throttled mid-reading
 *   (≥10 pages or ≥30 s since the last write), and flushed once on
 *   leave/pagehide — so web reading lands in History and syncs back to the
 *   app. Failures degrade to console.warn.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { galleryApi } from '@/api/gallery'
import { useKeyboardNav } from '@/composables/useKeyboardNav'
import { useEnhancedImage } from '@/composables/useEnhancedImage'
import { usePreferencesStore } from '@/stores/preferences'
import { DEFAULT_READER_PREFERENCES } from '@/api/preferences'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'
import ImageReader from '@/components/reader/ImageReader.vue'
import {
  AUTO_PLAY_INTERVALS_MS,
  PAGE_MODE_PREFS,
  READING_DIRECTIONS,
  firstPageOfSpread,
  spreadIndexOf,
} from '@/components/reader/PageMode.vue'
import type {
  AutoPlayState,
  PageModePref,
  ReadingDirection,
  ResolvedReaderMode,
} from '@/components/reader/PageMode.vue'

const route = useRoute()
const router = useRouter()
const preferencesStore = usePreferencesStore()

const gid = computed(() => Number(route.params.gid))

const loadState = ref<'loading' | 'error' | 'ready'>('loading')
const errorMessage = ref('Failed to load gallery')
/** 统一阅读器：detail 拉取失败时仍打开阅读器壳（单页错误由 PageMode 呈现）。 */
const degraded = ref(false)
const title = ref('')
/** Gallery site token from the detail response — required by history writeback. */
const galleryToken = ref('')
const totalPages = ref(0)
/** 0-based reading position. */
const currentPage = ref(0)
/** Zoom factor (page mode only); intentionally NOT persisted. */
const zoom = ref(1)

/* Wave-1 1c: reader preference consumption (defaults when prefs unloaded). */
const readerPrefs = computed(() => preferencesStore.prefs?.reader ?? DEFAULT_READER_PREFERENCES)

const READER_BACKGROUNDS: Record<string, string> = {
  black: '#080808',
  gray: '#1c1c1e',
  white: '#f7f7f5',
}

const readerRootStyle = computed(() => ({
  background: READER_BACKGROUNDS[readerPrefs.value.backgroundColor] ?? READER_BACKGROUNDS.black,
  '--reader-dual-gap': `${readerPrefs.value.dualPageGap}px`,
  '--reader-page-transition':
    readerPrefs.value.pageTransition === 'none'
      ? 'none'
      : readerPrefs.value.pageTransition === 'fade'
        ? 'opacity 200ms var(--ease-decelerate-quart)'
        : 'opacity 200ms var(--ease-decelerate-quart), transform 200ms var(--ease-decelerate-quart)',
}))
/** Monotonic guard so stale loads (superseded navigation) are dropped. */
let loadSeq = 0

const readerRef = ref<InstanceType<typeof ImageReader> | null>(null)

/* ------------------------------------------------------------------ */
/* Persisted reader preferences                                        */
/* ------------------------------------------------------------------ */

const SETTINGS_STORAGE_KEY = 'anotherviewer-web.reader-settings'

interface PersistedReaderSettings {
  direction: ReadingDirection
  pageMode: PageModePref
  brightness: number
}

const direction = ref<ReadingDirection>('ltr')
const pageModePref = ref<PageModePref>('auto')
const brightness = ref(0)

function applyStoredSettings() {
  try {
    const raw = localStorage.getItem(SETTINGS_STORAGE_KEY)
    if (!raw) return
    const stored = JSON.parse(raw) as Partial<PersistedReaderSettings>
    if (stored.direction && READING_DIRECTIONS.includes(stored.direction)) {
      direction.value = stored.direction
    }
    if (stored.pageMode && PAGE_MODE_PREFS.includes(stored.pageMode)) {
      pageModePref.value = stored.pageMode
    }
    if (typeof stored.brightness === 'number' && Number.isFinite(stored.brightness)) {
      brightness.value = Math.min(100, Math.max(0, Math.round(stored.brightness)))
    }
  } catch {
    // Corrupted storage — fall back to defaults.
  }
}

watch([direction, pageModePref, brightness], () => {
  try {
    const payload: PersistedReaderSettings = {
      direction: direction.value,
      pageMode: pageModePref.value,
      brightness: brightness.value,
    }
    localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(payload))
  } catch {
    // Storage unavailable / full — reading continues unimpaired.
  }
})

/* ------------------------------------------------------------------ */
/* Mode resolution — dual at min-aspect-ratio 1/1 (responsive §6)      */
/* ------------------------------------------------------------------ */

const landscape = ref(false)
let landscapeQuery: MediaQueryList | null = null

function onLandscapeChange(event: { matches: boolean }) {
  landscape.value = event.matches
}

const resolvedMode = computed<ResolvedReaderMode>(() => {
  if (direction.value === 'vertical') return 'scroll'
  switch (pageModePref.value) {
    case 'scroll':
      return 'scroll'
    case 'dual':
      return 'dual'
    case 'single':
      return 'page'
    default:
      return landscape.value ? 'dual' : 'page'
  }
})

/* ------------------------------------------------------------------ */
/* Navigation — spread-aware in dual mode                              */
/* ------------------------------------------------------------------ */

function onPageChange(page: number) {
  currentPage.value = Math.min(Math.max(page, 0), Math.max(0, totalPages.value - 1))
}

/** Advance one page — or one whole spread in dual mode. Returns false at the end. */
function nextPage(): boolean {
  const max = totalPages.value - 1
  if (resolvedMode.value === 'dual') {
    const target = firstPageOfSpread(spreadIndexOf(currentPage.value) + 1)
    if (target > max || target === currentPage.value) return false
    currentPage.value = target
    return true
  }
  if (currentPage.value >= max) return false
  currentPage.value += 1
  return true
}

/** Step back one page — to the start of the previous spread in dual mode. */
function prevPage(): boolean {
  if (resolvedMode.value === 'dual') {
    const target = firstPageOfSpread(spreadIndexOf(currentPage.value) - 1)
    if (target >= currentPage.value) return false
    currentPage.value = target
    return true
  }
  if (currentPage.value <= 0) return false
  currentPage.value -= 1
  return true
}

function goBack() {
  router.push(`/gallery/${gid.value}`)
}

/* ------------------------------------------------------------------ */
/* Keyboard — ←/→ mirror under RTL, Space/Esc toggle chrome            */
/* ------------------------------------------------------------------ */

useKeyboardNav({
  onPrev: () => {
    if (direction.value === 'rtl') nextPage()
    else prevPage()
  },
  onNext: () => {
    if (direction.value === 'rtl') prevPage()
    else nextPage()
  },
  onFirst: () => {
    currentPage.value = 0
  },
  onLast: () => {
    currentPage.value = Math.max(0, totalPages.value - 1)
  },
  onToggleToolbar: () => {
    readerRef.value?.toggleChrome()
  },
  onZoomIn: () => {
    zoom.value = Math.min(readerPrefs.value.maxZoom, Math.round((zoom.value + readerPrefs.value.zoomStep) * 100) / 100)
  },
  onZoomOut: () => {
    zoom.value = Math.max(0.5, Math.round((zoom.value - readerPrefs.value.zoomStep) * 100) / 100)
  },
  pagingEnabled: () => readerPrefs.value.keyboardPaging,
})

/* ------------------------------------------------------------------ */
/* Auto-play — 100 ms tick; pauses when the tab is hidden              */
/* ------------------------------------------------------------------ */

const AUTO_PLAY_TICK_MS = 100

const autoPlay = ref<AutoPlayState>({
  enabled: false,
  intervalMs: AUTO_PLAY_INTERVALS_MS[1] ?? 3000,
})
const autoPlayProgress = ref(0)
let autoPlayTimer: ReturnType<typeof setInterval> | null = null

watch(
  [() => autoPlay.value.enabled, () => autoPlay.value.intervalMs],
  () => {
    if (autoPlayTimer !== null) {
      clearInterval(autoPlayTimer)
      autoPlayTimer = null
    }
    autoPlayProgress.value = 0
    if (!autoPlay.value.enabled) return

    let elapsed = 0
    autoPlayTimer = setInterval(() => {
      if (typeof document !== 'undefined' && document.hidden) return
      elapsed += AUTO_PLAY_TICK_MS
      autoPlayProgress.value = Math.min(1, elapsed / autoPlay.value.intervalMs)
      if (elapsed >= autoPlay.value.intervalMs) {
        elapsed = 0
        if (!nextPage()) {
          // Reached the last page / spread — stop.
          autoPlay.value = { ...autoPlay.value, enabled: false }
        }
      }
    }, AUTO_PLAY_TICK_MS)
  },
)

/* ------------------------------------------------------------------ */
/* Route sync — debounced so scroll-mode tracking doesn't spam history */
/* ------------------------------------------------------------------ */

let routeSyncTimer: ReturnType<typeof setTimeout> | null = null

watch(currentPage, () => {
  if (loadState.value !== 'ready') return
  if (routeSyncTimer !== null) clearTimeout(routeSyncTimer)
  routeSyncTimer = setTimeout(() => {
    router
      .replace({
        name: 'Reader',
        params: { gid: String(gid.value), page: String(currentPage.value) },
      })
      .catch(() => {
        // Navigation superseded (e.g. leaving the reader) — harmless.
      })
  }, 250)
})

/* ------------------------------------------------------------------ */
/* F1 — history writeback                                              */
/* ------------------------------------------------------------------ */

/**
 * Mid-reading writeback strategy (at most one request per window):
 *
 * - Entering the reader records the visit once (`load()` success).
 * - Afterwards a page change triggers a writeback only when EITHER
 *   ≥ {@link HISTORY_WRITE_PAGE_STRIDE} pages flipped since the last write,
 *   OR ≥ {@link HISTORY_WRITE_MIN_INTERVAL_MS} elapsed since it.
 * - `pagehide` / unmount flush the tail once (best effort, fire-and-forget).
 *
 * Rationale: fast flippers keep the synced position within ~10 pages of
 * reality without per-flip spam; slow readers still checkpoint every ~30 s.
 * The server upserts one history row (timestamp refresh), so extra writes
 * are idempotent but not free — hence the throttle. Failures degrade to
 * console.warn; reading is never disturbed by a history outage.
 */
const HISTORY_WRITE_PAGE_STRIDE = 10
const HISTORY_WRITE_MIN_INTERVAL_MS = 30_000

/** Page already persisted by the last writeback (-1 = nothing yet). */
let lastHistoryPage = -1
/** Wall clock of the last writeback attempt — anti-spam bound, not delivery tracking. */
let lastHistoryAt = 0

function pushHistory(): void {
  // 统一阅读器：降级态（无 detail token）不做回写——同步脏标记防 leave-flush 误发。
  if (degraded.value || !galleryToken.value) {
    lastHistoryPage = currentPage.value
    lastHistoryAt = Date.now()
    return
  }
  lastHistoryPage = currentPage.value
  lastHistoryAt = Date.now()
  galleryApi
    .addHistory(gid.value, { token: galleryToken.value, title: title.value })
    .catch((error) => {
      console.warn(`[reader] history writeback failed (gid=${gid.value})`, error)
    })
}

/** Throttle gate for mid-reading page changes (see strategy above). */
function syncReadingProgress(): void {
  if (loadState.value !== 'ready') return
  const flipped = Math.abs(currentPage.value - lastHistoryPage)
  if (flipped === 0) return
  if (
    flipped < HISTORY_WRITE_PAGE_STRIDE &&
    Date.now() - lastHistoryAt < HISTORY_WRITE_MIN_INTERVAL_MS
  ) {
    return
  }
  pushHistory()
}

/** Last-chance flush on leave/unload: bypasses the throttle, skips no-op writes. */
function flushHistoryOnLeave(): void {
  if (loadState.value !== 'ready') return
  if (currentPage.value === lastHistoryPage) return
  pushHistory()
}

watch(currentPage, () => {
  syncReadingProgress()
})

/* ------------------------------------------------------------------ */
/* WebSocket — AI enhancement hot-swap (websocket-protocol §3.3/§6.3)  */
/* ------------------------------------------------------------------ */

// I3 composable — exclusively owns `/topic/gallery/{gid}/enhanced`
// (websocket-protocol §3.3/§6.3): preloads each `image.enhanced.ready` file
// and exposes it in the reactive map only after a successful load, so the
// reader hot-swaps atomically and silently keeps the original on failure.
// Page keys are 0-based; cleanup happens on unmount inside the composable.
const { enhancedUrls, connect: connectEnhanced } = useEnhancedImage(gid)

function subscribeEnhanced() {
  try {
    connectEnhanced()
  } catch {
    // WebSocket is an optional enhancement channel; reading works without it.
  }
}

/* ------------------------------------------------------------------ */
/* Gallery load                                                        */
/* ------------------------------------------------------------------ */

async function load() {
  const seq = ++loadSeq
  loadState.value = 'loading'
  try {
    const detail = await galleryApi.getDetail(gid.value)
    if (seq !== loadSeq) return
    const pages = Number(detail?.pages)
    if (!Number.isFinite(pages) || pages <= 0) {
      throw new Error('Gallery has no pages')
    }
    title.value = detail.title || `Gallery ${gid.value}`
    galleryToken.value = detail.token || ''
    totalPages.value = pages

    const start = Number(route.params.page)
    currentPage.value = Number.isFinite(start)
      ? Math.min(Math.max(start, 0), pages - 1)
      : 0

    loadState.value = 'ready'
    document.title = title.value
    subscribeEnhanced()
    // F1: entering the reader records the visit immediately (server upserts
    // the history row and refreshes its timestamp).
    pushHistory()
  } catch (error) {
    if (seq !== loadSeq) return
    console.error('Failed to load gallery', error)
    // 统一阅读器（2026-08-24）：detail 拉不到（EH 不可达 / 无本地元数据）也必须
    // 打开阅读器壳——单页失败由 PageMode 的错误覆盖层呈现（含重试）。
    // total 置 1 让阅读器停在可交互状态；历史回写跳过（无 token）。
    title.value = `Gallery ${gid.value}`
    totalPages.value = 1
    currentPage.value = 0
    degraded.value = true
    loadState.value = 'ready'
    document.title = title.value
  }
}

/* ------------------------------------------------------------------ */
/* Route param changes — the view is reused across navigations         */
/* ------------------------------------------------------------------ */

/** Reset all per-gallery state before (re)loading a different gid. */
function resetReaderState() {
  loadState.value = 'loading'
  errorMessage.value = 'Failed to load gallery'
  degraded.value = false
  title.value = ''
  galleryToken.value = ''
  lastHistoryPage = -1
  lastHistoryAt = 0
  totalPages.value = 0
  currentPage.value = 0
  zoom.value = 1
  autoPlay.value = { enabled: false, intervalMs: AUTO_PLAY_INTERVALS_MS[1] ?? 3000 }
  autoPlayProgress.value = 0
  if (autoPlayTimer !== null) clearInterval(autoPlayTimer)
  autoPlayTimer = null
  if (routeSyncTimer !== null) clearTimeout(routeSyncTimer)
  routeSyncTimer = null
}

// gid change → full reset + reload. The `useEnhancedImage` composable
// unsubscribes / re-subscribes the WebSocket topic on gid change itself.
watch(
  gid,
  () => {
    resetReaderState()
    void load()
  },
  { immediate: true },
)

// Same gallery, a different page param (deep-link / back-nav): jump to the
// page without reloading the gallery. Echoes of our own route-sync replace
// are skipped via the target === currentPage guard; page changes that arrive
// mid-load are picked up by `load()` reading `route.params.page`.
watch(
  () => route.params.page,
  (page) => {
    if (loadState.value !== 'ready') return
    const target = Number(page)
    if (!Number.isFinite(target) || target === currentPage.value) return
    currentPage.value = Math.min(Math.max(target, 0), Math.max(0, totalPages.value - 1))
  },
)

/* ------------------------------------------------------------------ */
/* Lifecycle                                                           */
/* ------------------------------------------------------------------ */

onMounted(() => {
  if (typeof window.matchMedia === 'function') {
    landscapeQuery = window.matchMedia('(min-aspect-ratio: 1/1)')
    landscape.value = landscapeQuery.matches
    landscapeQuery.addEventListener('change', onLandscapeChange)
  }
  applyStoredSettings()
  // F1: tab close / refresh — one best-effort writeback of the tail position.
  window.addEventListener('pagehide', flushHistoryOnLeave)
})

onBeforeUnmount(() => {
  landscapeQuery?.removeEventListener('change', onLandscapeChange)
  window.removeEventListener('pagehide', flushHistoryOnLeave)
  // Leaving the reader (router navigation) ends the session — flush the tail.
  flushHistoryOnLeave()
  if (autoPlayTimer !== null) clearInterval(autoPlayTimer)
  if (routeSyncTimer !== null) clearTimeout(routeSyncTimer)
  // The WebSocket subscription + connection ref are released by the
  // composable's own unmount hook.
})
</script>

<style scoped>
.reader-view {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: var(--grey-975); /* #080808 */
}

.reader-view__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--keyline-margin);
  height: 100%;
  padding: var(--keyline-margin);
  text-align: center;
}

.reader-view__hint {
  margin: 0;
  color: var(--grey-500);
  font-size: var(--text-little-small); /* 16sp */
}

.reader-view__degraded {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  padding: 4px 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.65);
  color: var(--grey-300, #aaa);
  font-size: var(--text-little-small);
  pointer-events: none;
}

.reader-view__retry {
  padding: 10px 32px;
  border: none;
  border-radius: var(--card-radius); /* 2dp */
  background: var(--color-primary);
  color: var(--color-white);
  font-size: var(--text-small); /* 14sp */
  font-weight: 500;
  cursor: pointer;
  transition:
    background 150ms var(--ease-decelerate-quart),
    transform 150ms var(--ease-decelerate-quart);
}

.reader-view__retry:hover {
  background: var(--color-primary-dark);
}

.reader-view__retry:active {
  transform: scale(0.97);
}

.reader-view__retry:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}
</style>
