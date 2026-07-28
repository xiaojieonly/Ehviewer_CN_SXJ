<template>
  <div
    class="fast-scroller"
    :class="{ 'is-visible': visible, 'is-dragging': dragging }"
  >
    <!-- The scrollable content the scroller is attached to (frozen slot contract). -->
    <div
      ref="containerRef"
      class="fast-scroller__container"
      @scroll.passive="onScroll"
    >
      <slot />
    </div>

    <!-- 30dp right-edge touch zone (Android FastScroller handler track). -->
    <div ref="trackRef" class="fast-scroller__track" aria-hidden="true">
      <div
        class="fast-scroller__thumb"
        role="scrollbar"
        aria-orientation="vertical"
        aria-label="Scroll position"
        :aria-valuenow="Math.round(scrollRatio * 100)"
        aria-valuemin="0"
        aria-valuemax="100"
        :style="{
          height: `${thumbHeight}px`,
          transform: `translateY(${thumbTop}px)`,
        }"
        @pointerdown="onThumbPointerDown"
      />
    </div>

    <!-- Optional position bubble shown while dragging. -->
    <div
      v-if="showBubble"
      v-show="dragging"
      class="fast-scroller__bubble"
      :style="{ top: `${thumbTop}px` }"
    >
      {{ bubbleLabel }}
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * FastScroller — web replica of `com.hippo.easyrecyclerview.FastScroller`.
 *
 * Wraps scrollable content (default slot) in its own overflow container and
 * overlays a right-edge drag handle:
 * - 30px wide touch zone pinned to the right edge (Android 30dp handler zone).
 * - Accent-colored handler that tracks the scroll position proportionally.
 * - Auto-hides after `autoHideDelay` ms of scroll idle (Android default 1500).
 * - Dragging the handler scrolls the content proportionally; an optional
 *   bubble label shows the relative position while dragging.
 *
 * The scroll element is exposed (`containerRef`) so composites like
 * `ContentLayout` can attach pull-to-refresh / load-more listeners to it.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { FastScrollerSlots } from '@/types/components'

const props = withDefaults(
  defineProps<{
    /** Idle time in ms before the handle fades out. @default 1500 */
    autoHideDelay?: number
    /** Show a position bubble while the thumb is dragged. @default false */
    showBubble?: boolean
  }>(),
  {
    autoHideDelay: 1500,
    showBubble: false,
  },
)

defineSlots<FastScrollerSlots>()

/** Minimum thumb height so it stays grabbable on very long lists. */
const THUMB_MIN_HEIGHT = 32

const containerRef = ref<HTMLElement | null>(null)
const trackRef = ref<HTMLElement | null>(null)

/** Whether the thumb is currently faded in. */
const visible = ref(false)
/** Whether a thumb drag gesture is in flight. */
const dragging = ref(false)
/** Thumb geometry in track-local px. */
const thumbTop = ref(0)
const thumbHeight = ref(0)
/** Scroll progress in [0, 1] — drives the bubble label. */
const scrollRatio = ref(0)

let hideTimer: ReturnType<typeof setTimeout> | null = null
let resizeObserver: ResizeObserver | null = null

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

/** Recompute thumb size/position from the container's scroll metrics. */
function updateThumb(): void {
  const el = containerRef.value
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  if (clientHeight === 0 || scrollHeight <= clientHeight) {
    // Content does not overflow — nothing to scroll.
    thumbHeight.value = 0
    thumbTop.value = 0
    scrollRatio.value = 0
    return
  }
  const height = Math.max(
    (clientHeight * clientHeight) / scrollHeight,
    THUMB_MIN_HEIGHT,
  )
  const maxScroll = scrollHeight - clientHeight
  const ratio = clamp(scrollTop / maxScroll, 0, 1)
  thumbHeight.value = height
  scrollRatio.value = ratio
  thumbTop.value = ratio * (clientHeight - height)
}

const bubbleLabel = computed(() => `${Math.round(scrollRatio.value * 100)}%`)

/** Show the thumb and (re)arm the auto-hide timer. */
function flash(): void {
  visible.value = true
  if (hideTimer !== null) clearTimeout(hideTimer)
  hideTimer = setTimeout(() => {
    if (!dragging.value) visible.value = false
  }, props.autoHideDelay)
}

function onScroll(): void {
  updateThumb()
  flash()
}

/**
 * Thumb drag — the 30px-wide thumb is the hit target; the visible bar is its
 * 8px right-aligned ::after. Pointer position maps linearly onto the scroll
 * range, exactly like the Android handler drag.
 */
function onThumbPointerDown(event: PointerEvent): void {
  const el = containerRef.value
  const track = trackRef.value
  if (!el || !track) return
  event.preventDefault()
  dragging.value = true
  flash()

  const trackRect = track.getBoundingClientRect()

  const onMove = (ev: PointerEvent): void => {
    const maxTop = trackRect.height - thumbHeight.value
    const y = ev.clientY - trackRect.top - thumbHeight.value / 2
    const ratio = maxTop > 0 ? clamp(y / maxTop, 0, 1) : 0
    el.scrollTop = ratio * (el.scrollHeight - el.clientHeight)
    updateThumb()
  }
  const onUp = (): void => {
    dragging.value = false
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    flash()
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}

onMounted(() => {
  updateThumb()
  const el = containerRef.value
  // ResizeObserver is unavailable in some test environments — degrade quietly.
  if (el && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => updateThumb())
    resizeObserver.observe(el)
  }
  window.addEventListener('resize', updateThumb)
})

onBeforeUnmount(() => {
  if (hideTimer !== null) clearTimeout(hideTimer)
  resizeObserver?.disconnect()
  resizeObserver = null
  window.removeEventListener('resize', updateThumb)
})

defineExpose({ containerRef, updateThumb })
</script>

<style scoped>
.fast-scroller {
  position: relative;
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

.fast-scroller__container {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

/* 30px right-edge touch zone — pointer-transparent except on the thumb. */
.fast-scroller__track {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 30px;
  z-index: 10;
  pointer-events: none;
}

/* Full 30px width is the hit target; the visible bar is the 8px ::after. */
.fast-scroller__thumb {
  position: absolute;
  top: 0;
  right: 0;
  width: 30px;
  cursor: grab;
  pointer-events: auto;
  opacity: 0;
  transition: opacity 150ms linear;
  touch-action: none;
}

.fast-scroller__thumb::after {
  content: '';
  position: absolute;
  top: 0;
  right: 4px;
  bottom: 0;
  width: 8px;
  border-radius: 4px;
  background: var(--color-accent); /* accent-colored handler drawable */
  opacity: 0.85;
}

.fast-scroller.is-visible .fast-scroller__thumb,
.fast-scroller.is-dragging .fast-scroller__thumb {
  opacity: 1;
}

.fast-scroller.is-dragging .fast-scroller__thumb {
  cursor: grabbing;
}

.fast-scroller__bubble {
  position: absolute;
  right: 40px;
  min-width: 48px;
  padding: 6px 10px;
  border-radius: var(--card-radius);
  background: var(--grey-800);
  color: var(--color-white);
  font-size: var(--text-super-small);
  text-align: center;
  z-index: 11;
  pointer-events: none;
}
</style>
