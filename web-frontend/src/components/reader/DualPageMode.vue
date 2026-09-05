<template>
  <div
    ref="rootRef"
    class="dual-page"
    :class="{ 'dual-page--rtl': direction === 'rtl' }"
    role="region"
    :aria-label="spreadLabel"
  >
    <!-- :key remounts the row per spread so the enter animation replays -->
    <div :key="spread.first" class="dual-page__row" :class="enterClass">
      <!-- Cover / trailing odd page: displayed alone, centered (responsive-strategy §6 rule 4) -->
      <div v-if="spread.alone" class="dual-page__slot dual-page__slot--alone">
        <Transition name="dual-page-fade">
          <div v-if="!loadedPages.has(spread.first)" class="dual-page__loading" aria-hidden="true">
            <ProgressSpinner size="small" />
          </div>
        </Transition>
        <img
          class="dual-page__img dual-page__img--alone"
          :class="{ 'dual-page__img--loaded': loadedPages.has(spread.first) }"
          :src="srcFor(spread.first)"
          :srcset="srcsetFor(spread.first)"
          :alt="`Page ${spread.first + 1} of ${totalPages}`"
          draggable="false"
          decoding="async"
          @load="onPageLoad(spread.first)"
        />
      </div>

      <!-- Paired spread: two pages side by side (order mirrored in RTL) -->
      <template v-else>
        <div class="dual-page__slot">
          <Transition name="dual-page-fade">
            <div v-if="!loadedPages.has(spread.left)" class="dual-page__loading" aria-hidden="true">
              <ProgressSpinner size="small" />
            </div>
          </Transition>
          <img
            class="dual-page__img"
            :class="{ 'dual-page__img--loaded': loadedPages.has(spread.left) }"
            :src="srcFor(spread.left)"
            :srcset="srcsetFor(spread.left)"
            :alt="`Page ${spread.left + 1} of ${totalPages}`"
            draggable="false"
            decoding="async"
            @load="onPageLoad(spread.left)"
          />
        </div>
        <div class="dual-page__slot">
          <Transition name="dual-page-fade">
            <div v-if="!loadedPages.has(spread.right)" class="dual-page__loading" aria-hidden="true">
              <ProgressSpinner size="small" />
            </div>
          </Transition>
          <img
            class="dual-page__img"
            :class="{ 'dual-page__img--loaded': loadedPages.has(spread.right) }"
            :src="srcFor(spread.right)"
            :srcset="srcsetFor(spread.right)"
            :alt="`Page ${spread.right + 1} of ${totalPages}`"
            draggable="false"
            decoding="async"
            @load="onPageLoad(spread.right)"
          />
        </div>
      </template>
    </div>

    <!--
      A4: edge hot-zone hints — same hover-only affordance as PageMode
      (gradient + chevron + resize cursor, hidden on touch devices). Visual
      only; clicks bubble to the stage's tap-zone handler.
    -->
    <div class="dual-page__hint dual-page__hint--prev" aria-hidden="true">
      <!-- chevron_left -->
      <svg viewBox="0 0 24 24" focusable="false">
        <path
          d="M15 18l-6-6 6-6"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </div>
    <div class="dual-page__hint dual-page__hint--next" aria-hidden="true">
      <!-- chevron_right -->
      <svg viewBox="0 0 24 24" focusable="false">
        <path
          d="M9 6l6 6-6 6"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * DualPageMode.vue — two pages side by side, replicating Android
 * `SpreadLayoutManager` (GalleryView `LAYOUT_DUAL_PAGE`):
 *
 * - Page 1 (0-based 0, the cover) is displayed ALONE and centered; pairing
 *   starts after it — 1-based pairs (2,3), (4,5)… = 0-based (1,2), (3,4)…
 *   (`contracts/responsive-strategy.md` §6 rule 4).
 * - RTL reading direction reverses the spread: the reading-order-first page
 *   sits on the RIGHT (`SpreadLayoutManager.SPREAD_RIGHT_TO_LEFT`).
 * - Navigation moves by whole spreads, like the Android pager.
 * - Per responsive-strategy §8, each page requests `?w=` at HALF the
 *   container width (× DPR).
 * - Triggered by `orientation: landscape` / `min-aspect-ratio: 1/1`
 *   (resolved by the parent — see §6 rules 1–3).
 *
 * Zoom intentionally applies to nothing here: per the original design
 * decision, dual-page spreads are viewed at fit-width (no per-page zoom).
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'
import {
  firstPageOfSpread,
  pageImageSrcset,
  pageImageUrl,
  spreadIndexOf,
  useReaderGestures,
} from './PageMode.vue'
import type { HorizontalDirection } from './PageMode.vue'

interface DualPageModeProps {
  gid: number
  /** 0-based index of the current (reading-order first visible) page. */
  page: number
  totalPages: number
  direction: HorizontalDirection
  /** AI-enhanced hot-swap URLs keyed by 0-based page. */
  enhancedUrls?: ReadonlyMap<number, string>
}

interface DualPageModeEmits {
  /** Previous / next SPREAD (reading order). */
  (e: 'prev'): void
  (e: 'next'): void
  (e: 'toggle-chrome'): void
}

const props = withDefaults(defineProps<DualPageModeProps>(), {
  enhancedUrls: undefined,
})
const emit = defineEmits<DualPageModeEmits>()

const rootRef = ref<HTMLElement | null>(null)
const rootWidth = ref(800)
const enterClass = ref('')
const loadedPages = reactive(new Set<number>())

let rootObserver: ResizeObserver | null = null

function devicePixelRatio(): number {
  return typeof window !== 'undefined' && window.devicePixelRatio > 0
    ? window.devicePixelRatio
    : 1
}

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
/* Spread resolution — cover alone, then (1,2), (3,4), …               */
/* ------------------------------------------------------------------ */

const spread = computed(() => {
  const total = props.totalPages
  const index = Math.min(Math.max(props.page, 0), Math.max(0, total - 1))
  const first = index === 0 ? 0 : firstPageOfSpread(spreadIndexOf(index))
  const second = first + 1 < total ? first + 1 : null
  const rtl = props.direction === 'rtl'
  return {
    first,
    /** Physically left slot (reading-order second page in RTL). */
    left: second === null ? first : rtl ? second : first,
    /** Physically right slot (null when the page stands alone). */
    right: second === null ? first : rtl ? first : second,
    alone: second === null,
  }
})

const spreadLabel = computed(() =>
  spread.value.alone
    ? `Page ${spread.value.first + 1} of ${props.totalPages}`
    : `Pages ${spread.value.first + 1}–${spread.value.first + 2} of ${props.totalPages}`,
)

/* ------------------------------------------------------------------ */
/* Responsive URLs — each page gets half the container width (§8)      */
/* ------------------------------------------------------------------ */

function srcFor(page: number): string {
  const enhanced = props.enhancedUrls?.get(page)
  if (enhanced) return enhanced
  return pageImageUrl(props.gid, page, (rootWidth.value / 2) * devicePixelRatio())
}

function srcsetFor(page: number): string | undefined {
  if (props.enhancedUrls?.get(page)) return undefined
  return pageImageSrcset(props.gid, page, rootWidth.value / 2)
}

function onPageLoad(page: number) {
  loadedPages.add(page)
}

/* ------------------------------------------------------------------ */
/* Spread-turn enter animation                                         */
/* ------------------------------------------------------------------ */

watch(
  () => spread.value.first,
  (next, prev) => {
    if (prev === undefined) return
    const forward = next > prev
    const fromRight = props.direction === 'rtl' ? !forward : forward
    enterClass.value = fromRight
      ? 'dual-page__row--from-right'
      : 'dual-page__row--from-left'
  },
)

/* ------------------------------------------------------------------ */
/* Gestures — same tap zones / swipe rules as single-page mode         */
/* ------------------------------------------------------------------ */

useReaderGestures({
  el: rootRef,
  isRtl: () => props.direction === 'rtl',
  suppressed: () => false,
  onPrev: () => emit('prev'),
  onNext: () => emit('next'),
  onToggleChrome: () => emit('toggle-chrome'),
})
</script>

<style scoped>
.dual-page {
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

/* --- A4: edge hot-zone hints (same affordance as PageMode) ------------- */

.dual-page__hint {
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  width: clamp(56px, 12%, 140px);
  color: rgba(255, 255, 255, 0.85);
  opacity: 0;
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.dual-page__hint svg {
  width: 28px;
  height: 28px;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.6));
}

.dual-page__hint--prev {
  left: 0;
  justify-content: flex-start;
  padding-left: 12px;
  cursor: w-resize;
  background: linear-gradient(to right, rgba(0, 0, 0, 0.4), transparent);
}

.dual-page__hint--next {
  right: 0;
  justify-content: flex-end;
  padding-right: 12px;
  cursor: e-resize;
  background: linear-gradient(to left, rgba(0, 0, 0, 0.4), transparent);
}

.dual-page__hint:hover {
  opacity: 1;
}

/* Touch / hover-less devices never see the hints. */
@media (hover: none), (pointer: coarse) {
  .dual-page__hint {
    display: none;
  }
}

.dual-page__row {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  width: 100%;
  height: 100%;
}

.dual-page__row--from-right {
  animation: dual-page-enter-right var(--duration-scene-translate)
    var(--ease-decelerate-quint);
}

.dual-page__row--from-left {
  animation: dual-page-enter-left var(--duration-scene-translate)
    var(--ease-decelerate-quint);
}

/* Subtle book-spine shading between the two pages of a paired spread. */
.dual-page__row:not(:has(.dual-page__slot--alone))::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 8%;
  bottom: 8%;
  width: 2px;
  transform: translateX(-50%);
  background: linear-gradient(
    to bottom,
    transparent,
    color-mix(in srgb, var(--color-black) 45%, transparent) 18%,
    color-mix(in srgb, var(--color-black) 45%, transparent) 82%,
    transparent
  );
  pointer-events: none;
}

.dual-page__slot {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: 100%;
}

.dual-page__slot--alone {
  grid-column: 1 / -1;
}

/* 铺满槽位（contain 可放大可缩小）——max-* 只缩不放会让原图小于
   槽位的平板出现大片黑边。封面独页（--alone）保持"半幅居中"语义。 */
.dual-page__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  opacity: 0;
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

/* A lone page reads as one column of the spread, centered. */
.dual-page__img--alone {
  width: 50%;
}

.dual-page__img--loaded {
  opacity: 1;
}

.dual-page__loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dual-page-fade-enter-active,
.dual-page-fade-leave-active {
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.dual-page-fade-enter-from,
.dual-page-fade-leave-to {
  opacity: 0;
}

@keyframes dual-page-enter-right {
  from {
    transform: translateX(6%);
    opacity: 0.3;
  }
  to {
    transform: none;
    opacity: 1;
  }
}

@keyframes dual-page-enter-left {
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
  .dual-page__row--from-right,
  .dual-page__row--from-left {
    animation: none;
  }
}
</style>
