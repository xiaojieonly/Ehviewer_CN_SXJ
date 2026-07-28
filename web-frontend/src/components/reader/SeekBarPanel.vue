<template>
  <div
    class="seekbar-panel"
    :class="{ 'seekbar-panel--reversed': reversed }"
    role="group"
    aria-label="Page position"
  >
    <span class="seekbar-panel__label seekbar-panel__label--left" aria-hidden="true">
      {{ leftText }}
    </span>

    <!-- Mirrors the 48dp FrameLayout wrapping ReversibleSeekBar in activity_gallery.xml -->
    <div class="seekbar-panel__track">
      <input
        class="seekbar-panel__slider"
        :class="{ 'seekbar-panel__slider--reversed': reversed }"
        type="range"
        :min="0"
        :max="sliderMax"
        step="1"
        :value="sliderValue"
        :aria-label="`Page ${displayPage} of ${totalPages}`"
        :aria-valuetext="`${displayPage} / ${totalPages}`"
        :style="{ '--seekbar-fill': `${fillPercent}%` }"
        @pointerdown="onPointerDown"
        @pointerup="onPointerUp"
        @pointercancel="onPointerCancel"
        @input="onInput"
        @change="onChange"
      />
    </div>

    <span class="seekbar-panel__label seekbar-panel__label--right" aria-hidden="true">
      {{ rightText }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SeekBarPanelEmits, SeekBarPanelProps } from '@/types/components'

/**
 * SeekBarPanel.vue — replicates the reader bottom panel from
 * `activity_gallery.xml` (`SeekBarPanel` + `ReversibleSeekBar` + two 32dp page
 * labels), wired exactly like `GalleryActivity`:
 *
 * - Panel: `?gallerySliderBackgroundColor` (#424242 light/dark, #212121 black
 *   → `--gallery-slider-background`), 48dp content height, 16dp h-padding.
 * - Slider is 0-based internally (`max = totalPages - 1`,
 *   `progress = currentPage - 1`); the public contract is 1-based pages.
 * - LTR: left label = current page, right label = total. Reversed (RTL
 *   reading, `GalleryActivity.isRightToLeft()`): labels swap, and the slider
 *   is mirrored — `ReversibleSeekBar` flips the canvas AND the touch input,
 *   which on the web is exactly what `transform: scaleX(-1)` does to the
 *   native range input (rendering and pointer hit-testing both mirror).
 * - While scrubbing, the "start" label tracks the drag live even before the
 *   parent round-trips the prop (`onProgressChanged(..., fromUser = true)`).
 * - `GalleryActivity.onStartTrackingTouch` / `onStopTrackingTouch` map to the
 *   `seek-start` / `seek-end` events; the page jump on release maps to the
 *   frozen-contract `change` event.
 *
 * The frozen prop/emit contract lives in `@/types/components`
 * (`SeekBarPanelProps` / `SeekBarPanelEmits`); the drag-lifecycle events are
 * added here without touching the frozen file.
 */
interface SeekBarPanelExtraEmits extends SeekBarPanelEmits {
  /** Drag started — Android `onStartTrackingTouch`. */
  (e: 'seek-start'): void
  /** Drag ended — Android `onStopTrackingTouch`. */
  (e: 'seek-end'): void
}

const props = withDefaults(defineProps<SeekBarPanelProps>(), {
  reversed: false,
})

const emit = defineEmits<SeekBarPanelExtraEmits>()

/** True between pointerdown and the commit/cancel that ends the gesture. */
const dragging = ref(false)
/** Live 0-based position while scrubbing (takes precedence over the prop). */
const dragValue = ref<number | null>(null)
/** Whether the pointer actually moved the value during this gesture. */
const movedDuringDrag = ref(false)

const hasPages = computed(() => props.totalPages > 0)

/** Native slider max — `mSeekBar.setMax(mSize - 1)` in GalleryActivity. */
const sliderMax = computed(() => Math.max(0, props.totalPages - 1))

const clampedPage = computed(() =>
  Math.min(Math.max(1, props.currentPage), Math.max(1, props.totalPages)),
)

/** 0-based value rendered by the native slider; the drag wins while active. */
const sliderValue = computed(() => {
  const raw = dragging.value && dragValue.value != null ? dragValue.value : clampedPage.value - 1
  return Math.min(Math.max(0, raw), sliderMax.value)
})

/** 1-based page shown on the "start" label — live while scrubbing. */
const displayPage = computed(() => sliderValue.value + 1)

/** `updateSlider()`: start label = current page, end label = total; swapped when reversed. */
const leftText = computed(() =>
  hasPages.value ? String(props.reversed ? props.totalPages : displayPage.value) : '',
)
const rightText = computed(() =>
  hasPages.value ? String(props.reversed ? displayPage.value : props.totalPages) : '',
)

/** Primary-color fill percentage for the 2px track gradient. */
const fillPercent = computed(() =>
  sliderMax.value === 0 ? 0 : (sliderValue.value / sliderMax.value) * 100,
)

function onPointerDown() {
  dragging.value = true
  movedDuringDrag.value = false
  dragValue.value = null
  emit('seek-start')
}

function onInput(event: Event) {
  const value = Number((event.target as HTMLInputElement).value)
  if (dragging.value) {
    dragValue.value = value
    movedDuringDrag.value = true
  }
  emit('update:currentPage', value + 1)
}

function onPointerUp() {
  if (!dragging.value) return
  if (!movedDuringDrag.value) {
    // Pressed and released without changing the value: the browser fires no
    // `change`, so close the tracking session here.
    dragging.value = false
    dragValue.value = null
    emit('seek-end')
  }
  // When the value did move, the native `change` event fires right after
  // pointerup and commits the page + ends the session (see onChange).
}

function onPointerCancel() {
  if (!dragging.value) return
  dragging.value = false
  dragValue.value = null
  emit('seek-end')
}

/**
 * Native commit point: pointer release after a drag, or a keyboard
 * adjustment. Mirrors `onStopTrackingTouch` — the page jump (`change`)
 * applies here.
 */
function onChange(event: Event) {
  const page = Number((event.target as HTMLInputElement).value) + 1
  const wasDragging = dragging.value
  dragging.value = false
  dragValue.value = null
  if (wasDragging) emit('seek-end')
  emit('change', page)
}
</script>

<style scoped>
.seekbar-panel {
  display: flex;
  align-items: center;
  height: var(--seekbar-panel-height); /* 48dp */
  padding: 0 var(--seekbar-panel-padding-h); /* 16dp */
  background: var(--gallery-slider-background); /* #424242 / #212121 */
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

/* 32dp page labels — white monospace, centered like the Android TextViews. */
.seekbar-panel__label {
  flex: 0 0 var(--seekbar-page-label);
  width: var(--seekbar-page-label);
  color: var(--color-white);
  font-family: 'Roboto Mono', ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: var(--text-small); /* 14sp */
  font-variant-numeric: tabular-nums;
  line-height: 1;
  text-align: center;
}

/* The 48dp FrameLayout that vertically centers the seek bar. */
.seekbar-panel__track {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  align-items: center;
  height: var(--seekbar-panel-height);
}

/*
 * Native range styled as a Material seek bar. The element itself is 44px
 * tall — the minimum comfortable touch target — while the drawn track stays
 * the Android-spec 2px.
 */
.seekbar-panel__slider {
  --seekbar-fill: 0%;
  -webkit-appearance: none;
  appearance: none;
  width: 100%;
  height: 44px;
  margin: 0;
  background: transparent;
  cursor: pointer;
  touch-action: pan-y;
}

.seekbar-panel__slider:focus {
  outline: none;
}
.seekbar-panel__slider:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
  border-radius: 2px;
}

/* --- WebKit / Blink ------------------------------------------------------ */
.seekbar-panel__slider::-webkit-slider-runnable-track {
  height: 2px;
  border-radius: 1px;
  background: linear-gradient(
    to right,
    var(--color-primary) 0,
    var(--color-primary) var(--seekbar-fill),
    rgba(255, 255, 255, 0.3) var(--seekbar-fill),
    rgba(255, 255, 255, 0.3) 100%
  );
}

.seekbar-panel__slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 12px;
  height: 12px;
  margin-top: -5px; /* center the 12px thumb on the 2px track */
  border: none;
  border-radius: 50%;
  background: var(--color-primary);
  transition:
    transform 120ms var(--ease-decelerate-quart),
    box-shadow 120ms var(--ease-decelerate-quart);
}

/* --- Firefox -------------------------------------------------------------- */
.seekbar-panel__slider::-moz-range-track {
  height: 2px;
  border-radius: 1px;
  background: rgba(255, 255, 255, 0.3);
}

.seekbar-panel__slider::-moz-range-progress {
  height: 2px;
  border-radius: 1px;
  background: var(--color-primary);
}

.seekbar-panel__slider::-moz-range-thumb {
  width: 12px;
  height: 12px;
  border: none;
  border-radius: 50%;
  background: var(--color-primary);
  transition:
    transform 120ms var(--ease-decelerate-quart),
    box-shadow 120ms var(--ease-decelerate-quart);
}

/* Material-style thumb feedback: grows on hover, blooms while pressed. */
.seekbar-panel__slider:hover::-webkit-slider-thumb {
  transform: scale(1.25);
}
.seekbar-panel__slider:active::-webkit-slider-thumb {
  transform: scale(1.5);
  box-shadow: 0 0 0 8px color-mix(in srgb, var(--color-primary) 20%, transparent);
}
.seekbar-panel__slider:hover::-moz-range-thumb {
  transform: scale(1.25);
}
.seekbar-panel__slider:active::-moz-range-thumb {
  transform: scale(1.5);
  box-shadow: 0 0 0 8px color-mix(in srgb, var(--color-primary) 20%, transparent);
}

/*
 * Reversed (RTL reading) — `ReversibleSeekBar` scales the canvas by -1 and
 * mirrors the touch x-coordinate; `scaleX(-1)` does both for the native
 * input (rendering AND pointer hit-testing are transformed).
 */
.seekbar-panel__slider--reversed {
  transform: scaleX(-1);
}
</style>
