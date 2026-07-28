<template>
  <div class="reader-view">
    <div v-if="loadState === 'loading'" class="reader-view__center">
      <ProgressSpinner size="large" />
      <p class="reader-view__hint">Loading gallery…</p>
    </div>

    <div v-else-if="loadState === 'error'" class="reader-view__center" role="alert">
      <p class="reader-view__hint">{{ errorMessage }}</p>
      <button type="button" class="reader-view__retry" @click="load">Retry</button>
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
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { galleryApi } from '@/api/gallery'
import { useKeyboardNav } from '@/composables/useKeyboardNav'
import { useEnhancedImage } from '@/composables/useEnhancedImage'
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

const gid = computed(() => Number(route.params.gid))

const loadState = ref<'loading' | 'error' | 'ready'>('loading')
const errorMessage = ref('Failed to load gallery')
const title = ref('')
const totalPages = ref(0)
/** 0-based reading position. */
const currentPage = ref(0)
/** Zoom factor (page mode only); intentionally NOT persisted. */
const zoom = ref(1)

const readerRef = ref<InstanceType<typeof ImageReader> | null>(null)

/* ------------------------------------------------------------------ */
/* Persisted reader preferences                                        */
/* ------------------------------------------------------------------ */

const SETTINGS_STORAGE_KEY = 'ehviewer-web.reader-settings'

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
    zoom.value = Math.min(3, Math.round((zoom.value + 0.25) * 100) / 100)
  },
  onZoomOut: () => {
    zoom.value = Math.max(0.5, Math.round((zoom.value - 0.25) * 100) / 100)
  },
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
  loadState.value = 'loading'
  try {
    const detail = await galleryApi.getDetail(gid.value)
    const pages = Number(detail?.pages)
    if (!Number.isFinite(pages) || pages <= 0) {
      throw new Error('Gallery has no pages')
    }
    title.value = detail.title || `Gallery ${gid.value}`
    totalPages.value = pages

    const start = Number(route.params.page)
    currentPage.value = Number.isFinite(start)
      ? Math.min(Math.max(start, 0), pages - 1)
      : 0

    loadState.value = 'ready'
    document.title = title.value
    subscribeEnhanced()
  } catch (error) {
    console.error('Failed to load gallery', error)
    errorMessage.value = 'Failed to load gallery'
    loadState.value = 'error'
  }
}

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
  load()
})

onBeforeUnmount(() => {
  landscapeQuery?.removeEventListener('change', onLandscapeChange)
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
