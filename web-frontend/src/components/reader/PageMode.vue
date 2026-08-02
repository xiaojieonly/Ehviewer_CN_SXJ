<template>
  <div
    ref="stageRef"
    class="page-mode"
    :class="{ 'page-mode--zoomed': zoomed }"
    role="region"
    :aria-label="`Page ${page + 1} of ${totalPages}`"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
  >
    <!-- :key remounts the frame per page so the enter animation replays -->
    <div :key="page" class="page-mode__frame" :class="enterClass">
      <img
        v-if="!error"
        ref="imgRef"
        class="page-mode__img"
        :class="{
          'page-mode__img--loaded': loaded,
          'page-mode__img--gesture': gesturing,
        }"
        :src="src"
        :srcset="srcset"
        :alt="`Page ${page + 1} of ${totalPages}`"
        :style="imgTransform"
        draggable="false"
        decoding="async"
        @load="onImageLoad"
        @error="onImageError"
      />
    </div>

    <Transition name="page-mode-fade">
      <div v-if="!loaded && !error" class="page-mode__overlay" aria-hidden="true">
        <ProgressSpinner size="large" />
      </div>
    </Transition>

    <div v-if="error" class="page-mode__overlay page-mode__overlay--error" role="alert">
      <p class="page-mode__error-text">Failed to load page {{ page + 1 }}</p>
      <button type="button" class="page-mode__retry" @click="retry">Retry</button>
    </div>
  </div>
</template>

<script lang="ts">
/**
 * Shared reader utilities — exported from this SFC's plain `<script>` block
 * (hoisted module scope) so sibling reader components (`DualPageMode`,
 * `ScrollMode`, `ImageReader`, `ReaderSettings`, `ReaderView`) can import
 * them WITHOUT creating a module cycle: `ImageReader` imports all three mode
 * components, so the shared code lives in a leaf they all already depend on.
 *
 * Design authority:
 * - Reading direction / page mode semantics: Android `GalleryActivity`
 *   (`READING_DIRECTION_*`, `PagerLayoutManager` / `ScrollLayoutManager` /
 *   `SpreadLayoutManager`).
 * - Image URLs: `contracts/openapi.yaml` `GET /api/v1/image/{galleryId}/{page}`
 *   (0-based page, `?w=` responsive width) + `contracts/responsive-strategy.md`
 *   §8 (reader srcset = container width, 1x/2x candidates).
 * - Dual-page pairing: `contracts/responsive-strategy.md` §6 — page 1 (cover)
 *   alone, pairs (2,3), (4,5)… i.e. 0-based pairs (1,2), (3,4)…
 */
import { onBeforeUnmount, onMounted } from 'vue'
import type { Ref } from 'vue'
import { useSwipeGesture } from '@/composables/useSwipeGesture'

/** Android `Settings.READING_DIRECTION_*`: LTR / RTL / vertical (scroll). */
export type ReadingDirection = 'ltr' | 'rtl' | 'vertical'

/** The two horizontal directions relevant to paged (non-scroll) modes. */
export type HorizontalDirection = 'ltr' | 'rtl'

/**
 * User-selected page mode preference. `auto` resolves by viewport
 * (responsive-strategy §6 rule 3: "force single / force dual / auto"):
 * landscape (aspect ≥ 1) → dual, portrait → single.
 */
export type PageModePref = 'auto' | 'single' | 'dual' | 'scroll'

/** The mode actually rendered after resolving `auto` + reading direction. */
export type ResolvedReaderMode = 'page' | 'dual' | 'scroll'

/** Auto-play (Android `auto_transfer`) state. */
export interface AutoPlayState {
  enabled: boolean
  intervalMs: number
}

export const READING_DIRECTIONS: readonly ReadingDirection[] = ['ltr', 'rtl', 'vertical']
export const PAGE_MODE_PREFS: readonly PageModePref[] = ['auto', 'single', 'dual', 'scroll']
export const AUTO_PLAY_INTERVALS_MS: readonly number[] = [2000, 3000, 5000, 8000]

/**
 * Responsive reader image URL — `streamGalleryImage` endpoint (0-based page)
 * with the `?w=` width hint (container width × DPR at the call site).
 */
export function pageImageUrl(gid: number, page: number, widthPx: number): string {
  return `/api/v1/image/${gid}/${page}?w=${Math.max(1, Math.round(widthPx))}`
}

/**
 * Reader srcset per responsive-strategy.md §8: `1x` at container width,
 * `2x` at double. The browser picks by devicePixelRatio.
 */
export function pageImageSrcset(gid: number, page: number, cssWidth: number): string {
  const w1 = Math.max(1, Math.round(cssWidth))
  return `${pageImageUrl(gid, page, w1)} 1x, ${pageImageUrl(gid, page, w1 * 2)} 2x`
}

/**
 * Spread (dual-page) index containing a 0-based page.
 * Page 0 (cover) is spread 0 and displayed alone; pages (1,2) → spread 1,
 * (3,4) → spread 2, … matching "pairs (2,3), (4,5)" in 1-based terms.
 */
export function spreadIndexOf(page: number): number {
  return page <= 0 ? 0 : Math.floor((page - 1) / 2) + 1
}

/** First (reading-order) 0-based page of a spread index. */
export function firstPageOfSpread(spread: number): number {
  return spread <= 0 ? 0 : 2 * spread - 1
}

/* ------------------------------------------------------------------------ */
/* Shared tap / swipe gesture handling                                       */
/* ------------------------------------------------------------------------ */

export interface ReaderGestureOptions {
  /** The full-bleed gesture surface. */
  el: Ref<HTMLElement | null>
  /** RTL reading — mirrors tap zones and swipe direction. */
  isRtl: () => boolean
  /**
   * When true, single-tap zones and swipes are ignored (e.g. zoomed: the
   * pointer is panning the image). Double-tap still works so the user can
   * zoom back out.
   */
  suppressed: () => boolean
  onPrev: () => void
  onNext: () => void
  onToggleChrome: () => void
  /** Double-tap action (zoom cycle); omit when the mode has no zoom. */
  onDoubleTap?: () => void
}

export interface ReaderGestures {
  /** Swallow the click that follows a custom gesture (pinch / pan). */
  suppressTaps: () => void
}

const DOUBLE_TAP_WINDOW_MS = 280
const TAP_SUPPRESS_WINDOW_MS = 400

/**
 * Tap-zone + swipe navigation shared by the paged reader modes:
 * - left ⅓ = prev, right ⅓ = next, center = toggle chrome (mirrored in RTL,
 *   like Tachiyomi/AnotherViewer RTL pagers);
 * - swipe left = next / swipe right = prev in LTR, reversed in RTL (the next
 *   page slides in from the left, so the finger sweeps right);
 * - single taps are delayed by the double-tap window so double-tap zoom never
 *   triggers a stray page turn.
 */
export function useReaderGestures(options: ReaderGestureOptions): ReaderGestures {
  let lastTapAt = 0
  let tapTimer: ReturnType<typeof setTimeout> | null = null
  let suppressUntil = 0

  function suppressTaps() {
    suppressUntil = Date.now() + TAP_SUPPRESS_WINDOW_MS
  }

  useSwipeGesture(options.el, {
    onSwipeLeft: () => {
      suppressTaps()
      if (options.suppressed()) return
      ;(options.isRtl() ? options.onPrev : options.onNext)()
    },
    onSwipeRight: () => {
      suppressTaps()
      if (options.suppressed()) return
      ;(options.isRtl() ? options.onNext : options.onPrev)()
    },
  })

  function onClick(event: MouseEvent) {
    const el = options.el.value
    if (!el || Date.now() < suppressUntil) return

    const now = Date.now()
    if (options.onDoubleTap && now - lastTapAt <= DOUBLE_TAP_WINDOW_MS) {
      if (tapTimer !== null) {
        clearTimeout(tapTimer)
        tapTimer = null
      }
      lastTapAt = 0
      options.onDoubleTap()
      return
    }

    // Zoomed: the pointer is for panning — no zone navigation.
    if (options.suppressed()) return

    lastTapAt = now
    const x = event.clientX - el.getBoundingClientRect().left
    const width = el.clientWidth
    tapTimer = setTimeout(() => {
      tapTimer = null
      if (x < width / 3) {
        ;(options.isRtl() ? options.onNext : options.onPrev)()
      } else if (x > (2 * width) / 3) {
        ;(options.isRtl() ? options.onPrev : options.onNext)()
      } else {
        options.onToggleChrome()
      }
    }, DOUBLE_TAP_WINDOW_MS)
  }

  onMounted(() => {
    options.el.value?.addEventListener('click', onClick)
  })

  onBeforeUnmount(() => {
    options.el.value?.removeEventListener('click', onClick)
    if (tapTimer !== null) clearTimeout(tapTimer)
  })

  return { suppressTaps }
}
</script>

<script setup lang="ts">
// NOTE: `pageImageUrl`, `pageImageSrcset`, `useReaderGestures`, the
// `HorizontalDirection` / `ReaderGestures` types AND the `onMounted` /
// `onBeforeUnmount` lifecycle helpers come from the plain `<script>` block
// above — both blocks compile into ONE module, so re-importing them here
// would be a duplicate identifier (and a self-import would be a cycle).
import { computed, reactive, ref, watch } from 'vue'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'

interface PageModeProps {
  gid: number
  /** 0-based page index. */
  page: number
  totalPages: number
  direction: HorizontalDirection
  /** Zoom factor (1 = fit). v-model:zoom. */
  zoom: number
  /** AI-enhanced hot-swap URLs keyed by 0-based page (websocket-protocol §3.3). */
  enhancedUrls?: ReadonlyMap<number, string>
}

interface PageModeEmits {
  (e: 'prev'): void
  (e: 'next'): void
  (e: 'toggle-chrome'): void
  (e: 'update:zoom', zoom: number): void
}

const props = withDefaults(defineProps<PageModeProps>(), {
  enhancedUrls: undefined,
})
const emit = defineEmits<PageModeEmits>()

const ZOOM_MIN = 1
const ZOOM_MAX = 3
const DOUBLE_TAP_STEPS = [1, 2, 3]

const stageRef = ref<HTMLElement | null>(null)
const imgRef = ref<HTMLImageElement | null>(null)

const loaded = ref(false)
const error = ref(false)
const hasLoaded = ref(false)
const retryTick = ref(0)
const panX = ref(0)
const panY = ref(0)
const gesturing = ref(false)
const enterClass = ref('')

const zoomed = computed(() => props.zoom > 1.001)

/* ------------------------------------------------------------------ */
/* Responsive image URLs (§8: container width × DPR)                   */
/* ------------------------------------------------------------------ */

const stageSize = reactive({ width: 800, height: 600 })
let stageObserver: ResizeObserver | null = null

function devicePixelRatio(): number {
  return typeof window !== 'undefined' && window.devicePixelRatio > 0
    ? window.devicePixelRatio
    : 1
}

const src = computed(() => {
  const enhanced = props.enhancedUrls?.get(props.page)
  if (enhanced) return enhanced
  const base = pageImageUrl(props.gid, props.page, stageSize.width * devicePixelRatio())
  return retryTick.value > 0 ? `${base}&_r=${retryTick.value}` : base
})

const srcset = computed(() =>
  props.enhancedUrls?.get(props.page)
    ? undefined
    : pageImageSrcset(props.gid, props.page, stageSize.width),
)

onMounted(() => {
  const el = stageRef.value
  if (!el) return
  stageSize.width = el.clientWidth || window.innerWidth
  stageSize.height = el.clientHeight || window.innerHeight
  if (typeof ResizeObserver !== 'undefined') {
    stageObserver = new ResizeObserver((entries) => {
      const rect = entries[0]?.contentRect
      if (rect && rect.width > 0) {
        stageSize.width = rect.width
        stageSize.height = rect.height
      }
    })
    stageObserver.observe(el)
  }
})

onBeforeUnmount(() => {
  stageObserver?.disconnect()
})

/* ------------------------------------------------------------------ */
/* Load / error                                                        */
/* ------------------------------------------------------------------ */

// Only a PAGE change resets the load state (the :key remount shows the
// spinner). A src change from re-sizing keeps the previous bitmap visible
// while the sharper version loads — no spinner flash mid-read.
watch(
  () => props.page,
  () => {
    loaded.value = false
    error.value = false
    hasLoaded.value = false
  },
)

function onImageLoad() {
  loaded.value = true
  hasLoaded.value = true
  clampPan()
}

function onImageError() {
  if (hasLoaded.value) {
    // websocket-protocol §3.3 step 5: a failing enhanced hot-swap must
    // silently keep the original — the element retains the old bitmap.
    console.debug('[PageMode] enhanced image failed to load, keeping original', props.page)
    return
  }
  error.value = true
}

function retry() {
  error.value = false
  retryTick.value += 1
}

/* ------------------------------------------------------------------ */
/* Page-turn enter animation (slides in from the reading edge)         */
/* ------------------------------------------------------------------ */

watch(
  () => props.page,
  (next, prev) => {
    if (prev === undefined) return
    const forward = next > prev
    const fromRight = props.direction === 'rtl' ? !forward : forward
    enterClass.value = fromRight
      ? 'page-mode__frame--from-right'
      : 'page-mode__frame--from-left'
  },
)

/* ------------------------------------------------------------------ */
/* Zoom: double-tap cycle (1→2→3→1), pinch, settings                   */
/* ------------------------------------------------------------------ */

function clampZoom(value: number): number {
  return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, value))
}

function cycleZoom() {
  const next = DOUBLE_TAP_STEPS.find((step) => step > props.zoom + 0.01) ?? 1
  emit('update:zoom', next)
}

const imgTransform = computed(() => ({
  transform: `translate(${panX.value}px, ${panY.value}px) scale(${props.zoom})`,
}))

function clampPan() {
  const img = imgRef.value
  const stage = stageRef.value
  if (!img || !stage) return
  // clientWidth/Height are the layout (zoom-1) box — transform is visual only.
  const maxX = Math.max(0, (img.clientWidth * props.zoom - stage.clientWidth) / 2)
  const maxY = Math.max(0, (img.clientHeight * props.zoom - stage.clientHeight) / 2)
  panX.value = Math.min(maxX, Math.max(-maxX, panX.value))
  panY.value = Math.max(-maxY, Math.min(maxY, panY.value))
}

watch(
  () => props.zoom,
  (zoom) => {
    if (zoom <= 1.001) {
      panX.value = 0
      panY.value = 0
    } else {
      clampPan()
    }
  },
)

/* ------------------------------------------------------------------ */
/* Gestures: tap zones + swipe (shared), pinch + pan (local)           */
/* ------------------------------------------------------------------ */

const gestures: ReaderGestures = useReaderGestures({
  el: stageRef,
  isRtl: () => props.direction === 'rtl',
  suppressed: () => zoomed.value,
  onPrev: () => emit('prev'),
  onNext: () => emit('next'),
  onToggleChrome: () => emit('toggle-chrome'),
  onDoubleTap: cycleZoom,
})

// --- Pinch-to-zoom (two-finger touch) ---------------------------------

let pinchStartDist = 0
let pinchStartZoom = 1

function touchDistance(event: TouchEvent): number {
  const a = event.touches[0]
  const b = event.touches[1]
  return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
}

function onTouchStart(event: TouchEvent) {
  if (event.touches.length === 2) {
    pinchStartDist = touchDistance(event)
    pinchStartZoom = props.zoom
    gesturing.value = true
    gestures.suppressTaps()
  }
}

function onTouchMove(event: TouchEvent) {
  if (pinchStartDist > 0 && event.touches.length === 2) {
    const scale = touchDistance(event) / pinchStartDist
    emit('update:zoom', clampZoom(pinchStartZoom * scale))
    gestures.suppressTaps()
  }
}

function onTouchEnd(event: TouchEvent) {
  if (event.touches.length < 2 && pinchStartDist > 0) {
    pinchStartDist = 0
    pinchStartZoom = 1
    gesturing.value = false
  }
}

onMounted(() => {
  const el = stageRef.value
  if (!el) return
  el.addEventListener('touchstart', onTouchStart, { passive: true })
  el.addEventListener('touchmove', onTouchMove, { passive: true })
  el.addEventListener('touchend', onTouchEnd, { passive: true })
  el.addEventListener('touchcancel', onTouchEnd, { passive: true })
})

onBeforeUnmount(() => {
  const el = stageRef.value
  if (!el) return
  el.removeEventListener('touchstart', onTouchStart)
  el.removeEventListener('touchmove', onTouchMove)
  el.removeEventListener('touchend', onTouchEnd)
  el.removeEventListener('touchcancel', onTouchEnd)
})

// --- Drag-to-pan while zoomed (pointer events: mouse + touch + pen) ----

let panPointerId: number | null = null
let panLastX = 0
let panLastY = 0

function onPointerDown(event: PointerEvent) {
  if (!zoomed.value || pinchStartDist > 0) return
  panPointerId = event.pointerId
  panLastX = event.clientX
  panLastY = event.clientY
  gesturing.value = true
  gestures.suppressTaps()
  stageRef.value?.setPointerCapture?.(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (panPointerId !== event.pointerId || pinchStartDist > 0) return
  panX.value += event.clientX - panLastX
  panY.value += event.clientY - panLastY
  panLastX = event.clientX
  panLastY = event.clientY
  clampPan()
}

function onPointerUp(event: PointerEvent) {
  if (panPointerId !== event.pointerId) return
  panPointerId = null
  gesturing.value = false
}
</script>

<style scoped>
.page-mode {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--grey-975);
  touch-action: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.page-mode--zoomed {
  cursor: grab;
}

.page-mode__frame {
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 100%;
  max-height: 100%;
}

.page-mode__frame--from-right {
  animation: page-mode-enter-right var(--duration-scene-translate)
    var(--ease-decelerate-quint);
}

.page-mode__frame--from-left {
  animation: page-mode-enter-left var(--duration-scene-translate)
    var(--ease-decelerate-quint);
}

.page-mode__img {
  max-width: 100%;
  max-height: calc(100vh - var(--seekbar-panel-height));
  max-height: calc(100dvh - var(--seekbar-panel-height));
  object-fit: contain;
  opacity: 0;
  transition:
    transform var(--duration-scene-opacity) var(--ease-decelerate-quart),
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
  will-change: transform;
}

.page-mode__img--loaded {
  opacity: 1;
}

/* No transform transition mid-gesture — the image must track the fingers. */
.page-mode__img--gesture {
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.page-mode__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing);
}

.page-mode__error-text {
  color: var(--grey-450);
  font-size: var(--text-little-small);
}

.page-mode__retry {
  padding: 8px 28px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: var(--text-small);
  font-weight: 500;
  cursor: pointer;
  transition:
    background 150ms var(--ease-decelerate-quart),
    transform 150ms var(--ease-decelerate-quart);
}

.page-mode__retry:hover {
  background: var(--color-primary-dark);
}

.page-mode__retry:active {
  transform: scale(0.97);
}

.page-mode__retry:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.page-mode-fade-enter-active,
.page-mode-fade-leave-active {
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.page-mode-fade-enter-from,
.page-mode-fade-leave-to {
  opacity: 0;
}

@keyframes page-mode-enter-right {
  from {
    transform: translateX(6%);
    opacity: 0.3;
  }
  to {
    transform: none;
    opacity: 1;
  }
}

@keyframes page-mode-enter-left {
  from {
    transform: translateX(-6%);
    opacity: 0.3;
  }
  to {
    transform: none;
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .page-mode__frame--from-right,
  .page-mode__frame--from-left {
    animation: none;
  }
}
</style>
