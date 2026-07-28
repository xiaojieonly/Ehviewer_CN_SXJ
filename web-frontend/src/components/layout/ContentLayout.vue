<template>
  <div class="content-layout">
    <!-- Loading: center ProgressView replica (Large, 76px). -->
    <div
      v-if="state === 'loading'"
      key="loading"
      class="content-layout__center"
      data-testid="content-state-loading"
    >
      <span class="pl-spinner pl-spinner--large" role="progressbar" aria-label="Loading">
        <svg viewBox="0 0 48 48" aria-hidden="true">
          <circle cx="24" cy="24" r="20" fill="none" stroke-width="4" />
        </svg>
      </span>
    </div>

    <!-- Empty: big sadpanda + tip text; tapping the tip retriggers a refresh
         (Android tip onClick → refresh()). -->
    <button
      v-else-if="state === 'empty'"
      key="empty"
      type="button"
      class="content-layout__center content-layout__tip"
      data-testid="content-state-empty"
      @click="emit('retry')"
    >
      <slot name="empty">
        <AppIcon name="sad-panda-primary" size="64px" class="content-layout__tip-icon" />
        <p class="content-layout__tip-text">{{ emptyText }}</p>
      </slot>
    </button>

    <!-- Error: alert icon + message + retry button (emits refresh). -->
    <div
      v-else-if="state === 'error'"
      key="error"
      class="content-layout__center"
      data-testid="content-state-error"
    >
      <AppIcon name="alert-red" size="48px" class="content-layout__error-icon" />
      <p class="content-layout__tip-text">{{ errorText }}</p>
      <button
        type="button"
        class="content-layout__retry"
        data-testid="content-retry"
        @click="emit('refresh')"
      >
        重试
      </button>
    </div>

    <!-- Content: pull-to-refresh header + scrollable list + footer pager
         spinner + right-edge FastScroller. -->
    <div v-else key="content" class="content-layout__body" data-testid="content-state-content">
      <!-- Pull-to-refresh header (slides down while pulling / refreshing). -->
      <div class="content-layout__refresh" :style="refreshHeaderStyle" aria-hidden="true">
        <span class="pl-spinner pl-spinner--header">
          <svg viewBox="0 0 48 48" aria-hidden="true">
            <circle cx="24" cy="24" r="20" fill="none" stroke-width="4" />
          </svg>
        </span>
      </div>

      <div class="content-layout__scroller-wrap" :style="scrollerStyle">
        <FastScroller v-if="fastScroll" ref="fastScrollerRef" class="content-layout__scroller">
          <div class="content-layout__list">
            <slot />
          </div>
          <div
            v-if="loadingMore"
            class="content-layout__footer"
            data-testid="content-loading-more"
          >
            <span class="pl-spinner pl-spinner--footer" role="progressbar" aria-label="Loading more">
              <svg viewBox="0 0 48 48" aria-hidden="true">
                <circle cx="24" cy="24" r="20" fill="none" stroke-width="4" />
              </svg>
            </span>
          </div>
        </FastScroller>

        <div v-else ref="plainScrollRef" class="content-layout__scroller content-layout__plain-scroll">
          <div class="content-layout__list">
            <slot />
          </div>
          <div
            v-if="loadingMore"
            class="content-layout__footer"
            data-testid="content-loading-more"
          >
            <span class="pl-spinner pl-spinner--footer" role="progressbar" aria-label="Loading more">
              <svg viewBox="0 0 48 48" aria-hidden="true">
                <circle cx="24" cy="24" r="20" fill="none" stroke-width="4" />
              </svg>
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import type { ContentState } from '@/types/components'

/**
 * The view states of this layout. Extends the frozen {@link ContentState}
 * ('loading' | 'content' | 'empty') with an `error` state (message + retry
 * button) required by the F4/F5 task spec.
 */
export type ContentLayoutState = ContentState | 'error'
</script>

<script setup lang="ts">
/**
 * ContentLayout — web replica of `com.hippo.widget.ContentLayout`:
 * ProgressView + tip view + pull-to-refresh header + scrollable list +
 * right-edge FastScroller, plus a footer "load next page" spinner for
 * infinite paging.
 *
 * State machine:
 * - `loading` — center large ProgressView replica (76px).
 * - `empty`   — big sadpanda above `emptyText`; tap retriggers (`retry`).
 * - `error`   — alert icon + `errorText` + retry button (`refresh`).
 * - `content` — default slot list with pull-to-refresh and load-more.
 *
 * Pull-to-refresh: dragging down at the top of the list reveals the header
 * spinner; releasing past the threshold emits `update:refreshing` (true) and
 * `refresh`. The header parks open while `refreshing` is true and collapses
 * when it returns to false (v-model:refreshing).
 */
import { computed, ref, watch } from 'vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import FastScroller from './FastScroller.vue'
import type { ContentLayoutEmits, ContentLayoutSlots } from '@/types/components'

const props = withDefaults(
  defineProps<{
    /** Which internal view is shown. @default 'content' */
    state?: ContentLayoutState
    /** Empty-state message (Android `setEmptyString`). @default 'No hint' */
    emptyText?: string
    /** Error-state message. @default 'Something went wrong' */
    errorText?: string
    /** Header pull-refresh spinner active. v-model:refreshing. @default false */
    refreshing?: boolean
    /** Footer "load next page" spinner active. @default false */
    loadingMore?: boolean
    /** Enable pull-to-refresh header. @default true */
    refreshEnabled?: boolean
    /** Show the right-edge FastScroller. @default true */
    fastScroll?: boolean
  }>(),
  {
    state: 'content',
    emptyText: 'No hint',
    errorText: 'Something went wrong',
    refreshing: false,
    loadingMore: false,
    refreshEnabled: true,
    fastScroll: true,
  },
)

const emit = defineEmits<ContentLayoutEmits>()
defineSlots<ContentLayoutSlots>()

/** Pulled distance (px) at which a release triggers a refresh. */
const PULL_THRESHOLD = 64
/** Maximum pull distance (px) — rubber-band ceiling. */
const PULL_MAX = 120
/** Height the header parks at while refreshing. */
const REFRESH_HEADER_HEIGHT = 56
/** Distance from the bottom (px) that triggers `load-more`. */
const LOAD_MORE_THRESHOLD = 48

const fastScrollerRef = ref<InstanceType<typeof FastScroller> | null>(null)
const plainScrollRef = ref<HTMLElement | null>(null)

/** Current pull-to-refresh drag distance in px. */
const pullDistance = ref(props.refreshing ? REFRESH_HEADER_HEIGHT : 0)
/** True while a drag gesture is in flight (disables settle transitions). */
const pulling = ref(false)

/** The active scroll element (FastScroller's container or the plain div). */
const scrollEl = computed<HTMLElement | null>(() => {
  if (!props.fastScroll) return plainScrollRef.value
  return fastScrollerRef.value?.containerRef ?? null
})

/* ------------------------------- pull-to-refresh ------------------------ */

let touchStartY = 0
let touchActive = false

function onTouchStart(event: TouchEvent): void {
  const el = scrollEl.value
  if (!props.refreshEnabled || props.state !== 'content' || props.refreshing || !el) return
  if (el.scrollTop > 0) return
  const touch = event.touches[0]
  if (!touch) return
  touchStartY = touch.clientY
  touchActive = true
}

function onTouchMove(event: TouchEvent): void {
  if (!touchActive) return
  const el = scrollEl.value
  const touch = event.touches[0]
  if (!touch || !el) return
  const dy = touch.clientY - touchStartY
  if (dy > 0 && el.scrollTop <= 0) {
    pulling.value = true
    pullDistance.value = Math.min(dy * 0.5, PULL_MAX)
    if (event.cancelable) event.preventDefault()
  } else {
    pulling.value = false
    pullDistance.value = 0
  }
}

function onTouchEnd(): void {
  if (!touchActive) return
  touchActive = false
  pulling.value = false
  if (pullDistance.value >= PULL_THRESHOLD) {
    pullDistance.value = REFRESH_HEADER_HEIGHT
    emit('update:refreshing', true)
    emit('refresh')
  } else {
    pullDistance.value = 0
  }
}

/* ------------------------------- infinite paging ------------------------ */

function onContentScroll(): void {
  const el = scrollEl.value
  if (!el || props.state !== 'content' || props.loadingMore) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - LOAD_MORE_THRESHOLD) {
    emit('load-more')
  }
}

/* Attach gesture/scroll listeners to whichever scroll element is active. */
watch(
  scrollEl,
  (el, _old, onCleanup) => {
    if (!el) return
    el.addEventListener('touchstart', onTouchStart, { passive: true })
    el.addEventListener('touchmove', onTouchMove, { passive: false })
    el.addEventListener('touchend', onTouchEnd)
    el.addEventListener('scroll', onContentScroll, { passive: true })
    onCleanup(() => {
      el.removeEventListener('touchstart', onTouchStart)
      el.removeEventListener('touchmove', onTouchMove)
      el.removeEventListener('touchend', onTouchEnd)
      el.removeEventListener('scroll', onContentScroll)
    })
  },
  { flush: 'post', immediate: true },
)

/* Programmatic refresh (v-model:refreshing) parks/collapses the header. */
watch(
  () => props.refreshing,
  (value) => {
    pulling.value = false
    pullDistance.value = value ? REFRESH_HEADER_HEIGHT : 0
  },
)

/* --------------------------------- styling ------------------------------ */

const settleTransition = 'transform 250ms var(--ease-decelerate-quart)'

const scrollerStyle = computed(() => ({
  transform: pullDistance.value > 0 ? `translateY(${pullDistance.value}px)` : '',
  transition: pulling.value ? 'none' : settleTransition,
}))

const refreshHeaderStyle = computed(() => ({
  transform: `translateY(${pullDistance.value - REFRESH_HEADER_HEIGHT}px)`,
  opacity: Math.min(pullDistance.value / REFRESH_HEADER_HEIGHT, 1),
  transition: pulling.value
    ? 'none'
    : `${settleTransition}, opacity 250ms var(--ease-decelerate-quart)`,
}))

/** Scroll the list back to the top (used by screens after a refresh). */
function scrollToTop(): void {
  scrollEl.value?.scrollTo({ top: 0 })
}

defineExpose({ scrollToTop })
</script>

<style scoped>
.content-layout {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;
  background: var(--color-bg);
}

/* Centered states (loading / empty / error). */
.content-layout__center {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing);
  padding: var(--keyline-margin);
}

/* The empty tip is a full-area button (Android tip view onClick). */
.content-layout__tip {
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
}

.content-layout__tip-icon {
  color: var(--color-primary);
}

.content-layout__tip-text {
  margin: 0;
  font-size: var(--text-little-small);
  color: var(--text-color-secondary);
}

.content-layout__retry {
  margin-top: var(--spacing);
  padding: 8px 24px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: var(--text-small);
  cursor: pointer;
  transition: background 150ms linear;
}

.content-layout__retry:active {
  background: var(--color-primary-dark);
}

/* Content state. */
.content-layout__body {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content-layout__refresh {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 5;
  pointer-events: none;
}

.content-layout__scroller-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  z-index: 1;
}

.content-layout__scroller {
  flex: 1;
  min-height: 0;
}

.content-layout__plain-scroll {
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

.content-layout__footer {
  display: flex;
  justify-content: center;
  padding: var(--spacing) 0;
}

/* ---------------------------------------------------------------------------
   ProgressView replica (Material indeterminate ring).
   Trim animation 1333ms, rotation 6665ms — exact Android timings.
   Sizes: Large 76px (--spinner-large), default 48px (--spinner-default).
   --------------------------------------------------------------------------- */
.pl-spinner {
  display: inline-block;
  line-height: 0;
}

.pl-spinner svg {
  animation: pl-rotate 6665ms linear infinite;
}

.pl-spinner circle {
  stroke: var(--progress-color);
  stroke-linecap: round;
  animation: pl-trim 1333ms ease-in-out infinite;
}

.pl-spinner--large svg {
  width: var(--spinner-large);
  height: var(--spinner-large);
}

.pl-spinner--header svg {
  width: 36px;
  height: 36px;
}

.pl-spinner--footer svg {
  width: var(--spinner-default);
  height: var(--spinner-default);
}

@keyframes pl-rotate {
  100% {
    transform: rotate(360deg);
  }
}

@keyframes pl-trim {
  0% {
    stroke-dasharray: 1, 200;
    stroke-dashoffset: 0;
  }
  50% {
    stroke-dasharray: 90, 200;
    stroke-dashoffset: -35px;
  }
  100% {
    stroke-dasharray: 90, 200;
    stroke-dashoffset: -125px;
  }
}
</style>
