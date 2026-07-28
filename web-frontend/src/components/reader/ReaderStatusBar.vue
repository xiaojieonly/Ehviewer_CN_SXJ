<template>
  <header
    class="reader-status-bar"
    :class="{ 'reader-status-bar--hidden': !visible }"
    :aria-hidden="!visible"
  >
    <time class="reader-status-bar__clock">{{ clockText }}</time>

    <span class="reader-status-bar__progress">{{ progressText }}</span>

    <span class="reader-status-bar__battery" role="img" aria-label="Battery">
      <!-- Static placeholder glyph standing in for hippo BatteryView -->
      <svg viewBox="0 0 25 12" aria-hidden="true" focusable="false">
        <rect
          x="0.75"
          y="0.75"
          width="20.5"
          height="10.5"
          rx="2.25"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        />
        <rect x="2.75" y="2.75" width="12.5" height="6.5" rx="1" fill="currentColor" />
        <rect x="22.5" y="3.5" width="2.5" height="5" rx="1.25" fill="currentColor" />
      </svg>
    </span>
  </header>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * ReaderStatusBar.vue — replicates the `GalleryHeader` overlay from
 * `activity_gallery.xml`: clock + stroked page progress + battery, floating
 * over the reader with a transparent background.
 *
 * Android sources:
 * - `GalleryHeader` ViewGroup: battery at the left edge, progress centered,
 *   clock at the right edge, all with 12dp margins (`gallery_widget_margin_*`)
 *   and display-cutout avoidance.
 * - `progress` is a `StrokeTextView` — 56sp (`gallery_page_text`), black
 *   0.5dp outline drawn BEHIND the fill (`paint-order: stroke fill` here),
 *   text = `"(index + 1) + "/" + size` from `GalleryActivity.updateProgress()`.
 * - Auto-hide uses `GalleryActivity.HIDE_SLIDER_DELAY` (3000 ms): while
 *   visible, 3 s without interaction emits `update:visible(false)`
 *   (v-model:visible); the parent re-shows the bar on reader tap.
 *
 * Note: per the F7 task spec this web replica places the clock on the LEFT
 * and the battery placeholder on the RIGHT (mirrored relative to the Android
 * layout, which puts battery left / clock right).
 */
interface ReaderStatusBarProps {
  /** Current page, 1-based. */
  currentPage: number
  /** Total page count of the gallery. */
  totalPages: number
  /** Whether the bar is shown. v-model:visible. */
  visible: boolean
}

interface ReaderStatusBarEmits {
  /**
   * Idle timeout fired (3 s without interaction) — the parent should hide
   * the bar. v-model:visible.
   */
  (e: 'update:visible', visible: boolean): void
}

const props = defineProps<ReaderStatusBarProps>()
const emit = defineEmits<ReaderStatusBarEmits>()

/** `GalleryActivity.HIDE_SLIDER_DELAY`. */
const IDLE_HIDE_DELAY_MS = 3000

/* ------------------------------------------------------------------ */
/* Clock — web stand-in for hippo `TextClock` (HH:mm, 24h)             */
/* ------------------------------------------------------------------ */

const now = ref(new Date())
let clockTimer: ReturnType<typeof setInterval> | null = null

const pad2 = (n: number) => String(n).padStart(2, '0')

const clockText = computed(() => `${pad2(now.value.getHours())}:${pad2(now.value.getMinutes())}`)

onMounted(() => {
  clockTimer = setInterval(() => {
    now.value = new Date()
  }, 1000)
})

/* ------------------------------------------------------------------ */
/* Page progress — "(index + 1) + '/' + size" (updateProgress)         */
/* ------------------------------------------------------------------ */

const progressText = computed(() =>
  props.totalPages > 0 && props.currentPage >= 1
    ? `${props.currentPage}/${props.totalPages}`
    : '',
)

/* ------------------------------------------------------------------ */
/* Idle auto-hide (HIDE_SLIDER_DELAY = 3000)                           */
/* ------------------------------------------------------------------ */

let idleTimer: ReturnType<typeof setTimeout> | null = null

function clearIdleTimer() {
  if (idleTimer != null) {
    clearTimeout(idleTimer)
    idleTimer = null
  }
}

function restartIdleTimer() {
  clearIdleTimer()
  if (props.visible) {
    idleTimer = setTimeout(() => {
      idleTimer = null
      emit('update:visible', false)
    }, IDLE_HIDE_DELAY_MS)
  }
}

// Re-arm whenever the bar is (re-)shown; the parent re-asserts `visible`
// on reader taps, which restarts the countdown.
watch(() => props.visible, restartIdleTimer, { immediate: true })

onBeforeUnmount(() => {
  clearIdleTimer()
  if (clockTimer != null) clearInterval(clockTimer)
})
</script>

<style scoped>
.reader-status-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 10;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: start;
  /* gallery_widget_margin_v / gallery_widget_margin_h = 12dp */
  padding: var(--gallery-widget-margin-v) var(--gallery-widget-margin-h);
  background: transparent;
  /* Taps pass through to the reader surface (tap-to-toggle zone). */
  pointer-events: none;
  visibility: visible;
  opacity: 1;
  transform: translateY(0);
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    transform var(--duration-scene-opacity) var(--ease-decelerate-quart),
    visibility 0s linear 0s;
}

.reader-status-bar--hidden {
  visibility: hidden;
  opacity: 0;
  transform: translateY(-8px);
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    transform var(--duration-scene-opacity) var(--ease-decelerate-quart),
    visibility 0s linear var(--duration-scene-opacity);
}

/* Clock — textColorSecondary-ish over imagery, tabular so it never jitters */
.reader-status-bar__clock {
  justify-self: start;
  color: rgba(255, 255, 255, 0.7);
  font-size: var(--text-little-small); /* 16sp */
  font-variant-numeric: tabular-nums;
  line-height: 1.4;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.6);
}

/*
 * Page progress — StrokeTextView replica: 56sp text with a black outline
 * rendered BEHIND the fill (the Android widget draws an outlined copy first,
 * then the plain text on top; `paint-order: stroke fill` is the CSS
 * equivalent). 0.5dp stroke ≈ 1px at Android 2x density.
 */
.reader-status-bar__progress {
  justify-self: center;
  color: rgba(255, 255, 255, 0.92);
  font-size: var(--gallery-page-text); /* 56sp */
  font-variant-numeric: tabular-nums;
  line-height: 1;
  -webkit-text-stroke: 1px rgba(0, 0, 0, 0.9);
  paint-order: stroke fill;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
}

/* Battery placeholder — static glyph in place of hippo BatteryView */
.reader-status-bar__battery {
  justify-self: end;
  display: inline-flex;
  align-items: center;
  padding-top: 2px;
  color: rgba(255, 255, 255, 0.7);
}

.reader-status-bar__battery svg {
  width: 25px;
  height: 12px;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.5));
}
</style>
