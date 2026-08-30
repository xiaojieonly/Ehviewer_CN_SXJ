<template>
  <div ref="rootRef" class="image-reader">
    <!-- Page area — fills the viewport behind the overlaid chrome -->
    <PageMode
      v-if="mode === 'page'"
      :gid="gid"
      :page="currentPage"
      :total-pages="totalPages"
      :direction="direction === 'rtl' ? 'rtl' : 'ltr'"
      :zoom="zoom"
      :enhanced-urls="enhancedUrls"
      @update:zoom="(value) => emit('update:zoom', value)"
      @prev="emit('prev')"
      @next="emit('next')"
      @toggle-chrome="toggleChrome"
    />
    <DualPageMode
      v-else-if="mode === 'dual'"
      :gid="gid"
      :page="currentPage"
      :total-pages="totalPages"
      :direction="direction === 'rtl' ? 'rtl' : 'ltr'"
      :enhanced-urls="enhancedUrls"
      @prev="emit('prev')"
      @next="emit('next')"
      @toggle-chrome="toggleChrome"
    />
    <ScrollMode
      v-else
      :gid="gid"
      :total-pages="totalPages"
      :current-page="currentPage"
      :enhanced-urls="enhancedUrls"
      @update:current-page="(page) => emit('update:currentPage', page)"
      @toggle-chrome="toggleChrome"
    />

    <!--
      GalleryHeader replica (clock / stroked N/M progress / battery),
      docked just under the toolbar; auto-hides after 3 s idle
      (HIDE_SLIDER_DELAY) via its own v-model:visible.
    -->
    <div class="image-reader__status-bar" :aria-hidden="!chromeVisible">
      <!-- :key remount re-arms the bar's internal HIDE_SLIDER_DELAY countdown
           after interactions the idle guard swallowed (scrub, settings). -->
      <ReaderStatusBar
        :key="statusBarEpoch"
        :current-page="currentPage + 1"
        :total-pages="totalPages"
        :visible="chromeVisible"
        @update:visible="onStatusBarVisibility"
      />
    </div>

    <ReaderToolbar
      :visible="chromeVisible"
      :title="title"
      :rtl="direction === 'rtl'"
      @back="emit('back')"
      @open-settings="settingsVisible = true"
    />

    <!-- SeekBarPanel (activity_gallery.xml bottom panel), 1-based contract -->
    <div
      class="image-reader__seekbar"
      :class="{ 'image-reader__seekbar--hidden': !chromeVisible }"
      :aria-hidden="!chromeVisible"
    >
      <SeekBarPanel
        :current-page="currentPage + 1"
        :total-pages="totalPages"
        :reversed="direction === 'rtl'"
        @change="onSeekCommit"
        @seek-start="onSeekStart"
        @seek-end="onSeekEnd"
      />
    </div>

    <!-- auto_transfer indicator: countdown ring, tap to stop -->
    <button
      v-if="autoPlay.enabled"
      type="button"
      class="image-reader__autoplay"
      :aria-label="`Auto-play every ${autoPlay.intervalMs / 1000} seconds — activate to stop`"
      @click="stopAutoPlay"
    >
      <ProgressSpinner
        size="small"
        :indeterminate="false"
        :progress="autoPlayProgress"
        color="var(--color-white)"
      />
      <span class="image-reader__autoplay-label">{{ autoPlay.intervalMs / 1000 }}s</span>
    </button>

    <ReaderSettings
      :visible="settingsVisible"
      :direction="direction"
      :page-mode="pageMode"
      :zoom="zoom"
      :auto-play="autoPlay"
      :brightness="brightness"
      @close="closeSettings"
      @update:direction="(value) => emit('update:direction', value)"
      @update:page-mode="(value) => emit('update:pageMode', value)"
      @update:zoom="(value) => emit('update:zoom', value)"
      @update:auto-play="(value) => emit('update:autoPlay', value)"
      @update:brightness="(value) => emit('update:brightness', value)"
    />

    <!-- Brightness mask — the activity_gallery.xml `mask` ColorView -->
    <div class="image-reader__mask" :style="{ opacity: maskOpacity }" aria-hidden="true" />
  </div>
</template>

<script setup lang="ts">
/**
 * ImageReader.vue — the reader chrome hub, composing the frozen contract
 * components exactly like `activity_gallery.xml` + `GalleryActivity`:
 *
 * - `GalleryHeader` (ReaderStatusBar) floats over the page and auto-hides
 *   after `HIDE_SLIDER_DELAY` (3 s) of idle — re-armed on every interaction.
 * - `SeekBarPanel` + `ReversibleSeekBar` dock at the bottom (48dp,
 *   `gallerySliderBackgroundColor`), mirrored for RTL reading; the page jump
 *   applies on release (`change`), while labels track the drag live.
 * - Brightness is the `mask` ColorView: a black overlay whose opacity
 *   follows the brightness setting (0 = follow system).
 * - Auto-play mirrors `auto_transfer`: a countdown chip above the seek bar.
 * - Adjacent pages are preloaded (next 2 / prev 1) for zero-wait turns, at
 *   the same responsive `?w=` width the page components request.
 *
 * Page navigation itself is owned by the parent (ReaderView): this component
 * re-emits semantic `prev` / `next` (spread-awareness included) and direct
 * `update:currentPage` jumps from the seek bar.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'
import { usePreferencesStore } from '@/stores/preferences'

const preferencesStore = usePreferencesStore()
import ReaderStatusBar from './ReaderStatusBar.vue'
import SeekBarPanel from './SeekBarPanel.vue'
import ReaderToolbar from './ReaderToolbar.vue'
import ReaderSettings from './ReaderSettings.vue'
import PageMode from './PageMode.vue'
import DualPageMode from './DualPageMode.vue'
import ScrollMode from './ScrollMode.vue'
import { pageImageUrl } from './PageMode.vue'
import type {
  AutoPlayState,
  PageModePref,
  ReadingDirection,
  ResolvedReaderMode,
} from './PageMode.vue'

interface ImageReaderProps {
  gid: number
  title: string
  totalPages: number
  /** 0-based current page. v-model:currentPage. */
  currentPage: number
  /** Reading direction setting. v-model:direction. */
  direction: ReadingDirection
  /** User page-mode preference (settings). v-model:pageMode. */
  pageMode: PageModePref
  /** Resolved mode after `auto` + direction (parent owns the viewport watch). */
  mode: ResolvedReaderMode
  /** Zoom factor for page mode. v-model:zoom. */
  zoom: number
  /** 0 = system brightness; 1–100 dims via the mask. v-model:brightness. */
  brightness: number
  /** Auto-play state. v-model:autoPlay. */
  autoPlay: AutoPlayState
  /** Countdown progress of the current auto-play tick, 0–1. */
  autoPlayProgress: number
  /** AI-enhanced hot-swap URLs keyed by 0-based page. */
  enhancedUrls?: ReadonlyMap<number, string>
}

interface ImageReaderEmits {
  (e: 'update:currentPage', page: number): void
  (e: 'update:direction', direction: ReadingDirection): void
  (e: 'update:pageMode', mode: PageModePref): void
  (e: 'update:zoom', zoom: number): void
  (e: 'update:brightness', brightness: number): void
  (e: 'update:autoPlay', state: AutoPlayState): void
  /** Semantic navigation — the parent maps to page indices (spread-aware). */
  (e: 'prev'): void
  (e: 'next'): void
  (e: 'back'): void
}

const props = withDefaults(defineProps<ImageReaderProps>(), {
  enhancedUrls: undefined,
})
const emit = defineEmits<ImageReaderEmits>()

const rootRef = ref<HTMLElement | null>(null)
const rootWidth = ref(0)

/** Chrome (status bar + toolbar + seek bar) visibility — tap to toggle. */
const chromeVisible = ref(true)
const settingsVisible = ref(false)
/** True while the seek bar is being scrubbed. */
const seeking = ref(false)
/** Bumped to remount ReaderStatusBar and restart its idle countdown. */
const statusBarEpoch = ref(0)

/* ------------------------------------------------------------------ */
/* Chrome toggling + GalleryHeader idle interplay                      */
/* ------------------------------------------------------------------ */

function toggleChrome() {
  chromeVisible.value = !chromeVisible.value
}

function onStatusBarVisibility(visible: boolean) {
  // The 3 s idle countdown must not fire mid-scrub or while the settings
  // sheet is open — GalleryActivity likewise keeps the slider while tracking.
  if (!visible && (seeking.value || settingsVisible.value)) return
  chromeVisible.value = visible
}

function onSeekStart() {
  seeking.value = true
  chromeVisible.value = true
}

function onSeekEnd() {
  seeking.value = false
  // Re-arm the header's idle countdown — mirrors GalleryActivity re-posting
  // HIDE_SLIDER_DELAY in onStopTrackingTouch. The guard in
  // onStatusBarVisibility swallowed the mid-scrub timeout, so remount the
  // bar to restart its countdown without a visibility flicker.
  if (chromeVisible.value) statusBarEpoch.value += 1
}

function closeSettings() {
  settingsVisible.value = false
  // Same re-arm as after a scrub: the idle timeout that fired while the
  // sheet was open was ignored, so restart the countdown now.
  if (chromeVisible.value) statusBarEpoch.value += 1
}

/** Seek bar release — the page jump applies here (1-based contract). */
function onSeekCommit(page: number) {
  emit('update:currentPage', page - 1)
}

function stopAutoPlay() {
  emit('update:autoPlay', { enabled: false, intervalMs: props.autoPlay.intervalMs })
}

/* ------------------------------------------------------------------ */
/* Brightness mask (activity_gallery.xml `mask` ColorView)             */
/* ------------------------------------------------------------------ */

const maskOpacity = computed(() =>
  props.brightness <= 0 ? 0 : (1 - props.brightness / 100) * 0.9,
)

/* ------------------------------------------------------------------ */
/* Preload adjacent pages (next 2 / prev 1) for zero-wait turns        */
/* ------------------------------------------------------------------ */

let rootObserver: ResizeObserver | null = null

function devicePixelRatio(): number {
  return typeof window !== 'undefined' && window.devicePixelRatio > 0
    ? window.devicePixelRatio
    : 1
}

/** CSS width of ONE page in the current mode (dual splits the viewport). */
const perPageCssWidth = computed(() =>
  props.mode === 'dual' ? rootWidth.value / 2 : rootWidth.value,
)

const preloaded = new Set<string>()

function preloadPage(page: number) {
  if (page < 0 || (props.totalPages > 0 && page >= props.totalPages) || perPageCssWidth.value <= 0) return
  const url =
    props.enhancedUrls?.get(page) ??
    pageImageUrl(props.gid, page, perPageCssWidth.value * devicePixelRatio())
  if (preloaded.has(url)) return
  preloaded.add(url)
  const img = new Image()
  img.decoding = 'async'
  img.src = url
}

// The map's identity never changes (entries are added in place), so react
// to its size to re-run preloads when an enhanced version arrives.
watch(
  [() => props.currentPage, perPageCssWidth, () => props.enhancedUrls?.size],
  () => {
    const page = props.currentPage
    // Wave-1 1c: preloadCount (0 = off); prev page always primed for back-nav.
    const ahead = preferencesStore.prefs?.reader.preloadCount ?? 2
    for (let i = 1; i <= ahead; i++) preloadPage(page + i)
    preloadPage(page - 1)
  },
  { immediate: true },
)

onMounted(() => {
  const el = rootRef.value
  if (!el) return
  rootWidth.value = el.clientWidth || window.innerWidth
  if (typeof ResizeObserver !== 'undefined') {
    rootObserver = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width
      if (width && width > 0) rootWidth.value = width
    })
    rootObserver.observe(el)
  }
})

onBeforeUnmount(() => {
  rootObserver?.disconnect()
})

/* ------------------------------------------------------------------ */
/* Zoom resets per page (each page starts fit, like the Android pager) */
/* ------------------------------------------------------------------ */

watch(
  () => props.currentPage,
  () => {
    if (props.zoom !== 1) emit('update:zoom', 1)
  },
)

defineExpose({ toggleChrome })
</script>

<style scoped>
.image-reader {
  position: relative;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--grey-975); /* #080808 — reader backdrop */
}

/* GalleryHeader sits just under the toolbar scrim (which now grows by the
   status-bar / cutout inset). */
.image-reader__status-bar {
  position: absolute;
  top: calc(var(--toolbar-height) + var(--safe-area-top));
  left: 0;
  right: 0;
  z-index: 20;
  pointer-events: none;
}

/* Bottom SeekBarPanel slides away with the chrome. Padded so the slider
   clears the home indicator; the slide-out transform still hides it fully. */
.image-reader__seekbar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  padding-bottom: var(--safe-area-bottom);
  transition:
    transform var(--duration-scene-translate) var(--ease-decelerate-quint),
    visibility 0s linear 0s;
}

.image-reader__seekbar--hidden {
  transform: translateY(100%);
  visibility: hidden;
  transition:
    transform var(--duration-scene-translate) var(--ease-decelerate-quint),
    visibility 0s linear var(--duration-scene-translate);
}

/* auto_transfer chip — floats above the seek bar, end-aligned (45dp icon
   + margins in activity_gallery.xml ≈ this pill). */
.image-reader__autoplay {
  position: absolute;
  right: var(--keyline-margin);
  /* Sits above the seekbar, which is itself raised by the home-indicator inset. */
  bottom: calc(var(--seekbar-panel-height) + var(--safe-area-bottom) + 12px);
  z-index: 40;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px 7px 9px;
  border: none;
  border-radius: 17px;
  background: color-mix(in srgb, var(--grey-800) 88%, transparent);
  color: var(--color-white);
  font-size: var(--text-super-small); /* 12sp */
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition:
    background 150ms var(--ease-decelerate-quart),
    transform 150ms var(--ease-decelerate-quart);
}

.image-reader__autoplay:hover {
  background: color-mix(in srgb, var(--grey-750) 90%, transparent);
}

.image-reader__autoplay:active {
  transform: scale(0.95);
}

.image-reader__autoplay:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.image-reader__autoplay-label {
  line-height: 1;
}

/* Brightness mask — always on top (the ColorView is the last child of the
   activity's FrameLayout) but never intercepts input. */
.image-reader__mask {
  position: absolute;
  inset: 0;
  z-index: 60;
  background: var(--color-black);
  pointer-events: none;
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

@media (prefers-reduced-motion: reduce) {
  .image-reader__seekbar,
  .image-reader__seekbar--hidden {
    transition-duration: 1ms;
  }
}
</style>
