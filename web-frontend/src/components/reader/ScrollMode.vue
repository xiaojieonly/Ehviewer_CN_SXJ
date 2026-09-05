<template>
  <div ref="scrollerRef" class="scroll-mode" @click="emit('toggle-chrome')">
    <div
      v-for="index in totalPages"
      :key="index"
      :ref="(el) => setPageRef(index - 1, el)"
      class="scroll-mode__page"
      :data-page="index - 1"
    >
      <template v-if="rendered.has(index - 1)">
        <Transition name="scroll-mode-fade">
          <div
            v-if="!loadedPages.has(index - 1)"
            class="scroll-mode__loading"
            aria-hidden="true"
          >
            <ProgressSpinner size="small" />
          </div>
        </Transition>
        <img
          class="scroll-mode__img"
          :class="{ 'scroll-mode__img--loaded': loadedPages.has(index - 1) }"
          :src="srcFor(index - 1)"
          :srcset="srcsetFor(index - 1)"
          :alt="`Page ${index} of ${totalPages}`"
          loading="lazy"
          decoding="async"
          draggable="false"
          @load="onPageLoad(index - 1)"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * ScrollMode.vue — vertical continuous reading, replicating Android
 * `ScrollLayoutManager`:
 *
 * - All pages stacked full-width with `gallery_scroll_interval` (28dp)
 *   spacing; tap anywhere toggles the chrome.
 * - Lazy rendering via IntersectionObserver: only pages within ±~1.5
 *   viewports of the scroll position mount an `<img>`; the rest stay as
 *   min-height placeholders so the scroll geometry is preserved.
 * - A second observer (10%-tall center band) tracks the current page and
 *   round-trips it through the parent — the seek bar and the status bar's
 *   stroked "N/M" progress update live while scrolling.
 * - External page changes (seek bar drag, keyboard, auto-play) smooth-scroll
 *   the target page to the top.
 * - Images use the responsive `?w=` endpoint at container width × DPR
 *   (responsive-strategy §8).
 */
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'
import { pageImageSrcset, pageImageUrl } from './PageMode.vue'

interface ScrollModeProps {
  gid: number
  totalPages: number
  /** 0-based current page. v-model:currentPage. */
  currentPage: number
  /**
   * True while the seek bar is being scrubbed (plan-2026-09-05 A8): external
   * page changes jump INSTANTLY so the preview tracks the finger instead of
   * smooth-scrolling behind it.
   */
  scrubbing?: boolean
  /** AI-enhanced hot-swap URLs keyed by 0-based page. */
  enhancedUrls?: ReadonlyMap<number, string>
}

interface ScrollModeEmits {
  (e: 'update:currentPage', page: number): void
  (e: 'toggle-chrome'): void
}

const props = withDefaults(defineProps<ScrollModeProps>(), {
  scrubbing: false,
  enhancedUrls: undefined,
})
const emit = defineEmits<ScrollModeEmits>()

const scrollerRef = ref<HTMLElement | null>(null)
const scrollerWidth = ref(800)

/** Pages whose wrapper is near the viewport — only these mount an <img>. */
const rendered = reactive(new Set<number>())
const loadedPages = reactive(new Set<number>())

const pageRefs: Array<HTMLElement | null> = []

function setPageRef(index: number, el: unknown) {
  pageRefs[index] = el as HTMLElement | null
}

function devicePixelRatio(): number {
  return typeof window !== 'undefined' && window.devicePixelRatio > 0
    ? window.devicePixelRatio
    : 1
}

function srcFor(page: number): string {
  return (
    props.enhancedUrls?.get(page) ??
    pageImageUrl(props.gid, page, scrollerWidth.value * devicePixelRatio())
  )
}

function srcsetFor(page: number): string | undefined {
  if (props.enhancedUrls?.get(page)) return undefined
  return pageImageSrcset(props.gid, page, scrollerWidth.value)
}

function onPageLoad(page: number) {
  loadedPages.add(page)
}

/* ------------------------------------------------------------------ */
/* Observers: lazy render window + current-page tracking               */
/* ------------------------------------------------------------------ */

let renderObserver: IntersectionObserver | null = null
let currentObserver: IntersectionObserver | null = null
let widthObserver: ResizeObserver | null = null

/** Pages currently inside the center band, keyed by distance to center. */
const bandDistances = new Map<number, number>()
/** Guards the prop watcher against our own emits. */
let emittingCurrent = false

function bandDistance(page: number): number {
  const el = pageRefs[page]
  const scroller = scrollerRef.value
  if (!el || !scroller) return Number.POSITIVE_INFINITY
  const box = el.getBoundingClientRect()
  const scrollBox = scroller.getBoundingClientRect()
  return Math.abs(box.top + box.height / 2 - (scrollBox.top + scrollBox.height / 2))
}

function emitCurrent(page: number) {
  if (page === props.currentPage || page < 0 || page >= props.totalPages) return
  emittingCurrent = true
  emit('update:currentPage', page)
  nextTick(() => {
    emittingCurrent = false
  })
}

function nearestPageInBand(): number | null {
  let best: number | null = null
  let bestDist = Number.POSITIVE_INFINITY
  bandDistances.forEach((dist, page) => {
    if (dist < bestDist) {
      bestDist = dist
      best = page
    }
  })
  return best
}

onMounted(() => {
  const scroller = scrollerRef.value
  if (!scroller) return
  scrollerWidth.value = scroller.clientWidth || window.innerWidth

  if (typeof ResizeObserver !== 'undefined') {
    widthObserver = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width
      if (width && width > 0) scrollerWidth.value = width
    })
    widthObserver.observe(scroller)
  }

  const pages = Array.from({ length: props.totalPages }, (_, i) => i)

  if (typeof IntersectionObserver === 'undefined') {
    // Degrade to rendering everything (very old engines / odd test envs).
    pages.forEach((page) => rendered.add(page))
    return
  }

  renderObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const page = Number((entry.target as HTMLElement).dataset.page)
        if (entry.isIntersecting) rendered.add(page)
        else rendered.delete(page)
      }
    },
    { root: scroller, rootMargin: '150% 0px 150% 0px' },
  )

  currentObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const page = Number((entry.target as HTMLElement).dataset.page)
        if (entry.isIntersecting) bandDistances.set(page, bandDistance(page))
        else bandDistances.delete(page)
      }
      const current = nearestPageInBand()
      if (current !== null) emitCurrent(current)
    },
    // 10%-tall band across the vertical center of the scroll port.
    { root: scroller, rootMargin: '-45% 0px -45% 0px', threshold: 0 },
  )

  pages.forEach((page) => {
    const el = pageRefs[page]
    if (!el) return
    renderObserver?.observe(el)
    currentObserver?.observe(el)
  })

  // Restore the reading position (deep link / back navigation).
  if (props.currentPage > 0) scrollToPage(props.currentPage, true)
})

onBeforeUnmount(() => {
  renderObserver?.disconnect()
  currentObserver?.disconnect()
  widthObserver?.disconnect()
})

/* ------------------------------------------------------------------ */
/* External page changes → smooth-scroll the target to the top         */
/* ------------------------------------------------------------------ */

function scrollToPage(page: number, instant = false) {
  const scroller = scrollerRef.value
  const el = pageRefs[page]
  if (!scroller || !el) return
  const padTop = parseFloat(getComputedStyle(scroller).paddingTop) || 0
  scroller.scrollTo({
    top: Math.max(0, el.offsetTop - padTop),
    behavior: instant ? 'auto' : 'smooth',
  })
}

watch(
  () => props.currentPage,
  (page) => {
    // A8: while the seek bar is scrubbed, previews jump instantly; every
    // other external change (keyboard, deep link, auto-play) smooth-scrolls.
    if (!emittingCurrent) scrollToPage(page, props.scrubbing)
  },
)
</script>

<style scoped>
.scroll-mode {
  position: absolute;
  inset: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--grey-975);
  /* Keep the first/last pages clear of the overlaid chrome. */
  padding: var(--toolbar-height) 0
    calc(var(--seekbar-panel-height) + var(--gallery-scroll-interval));
  scroll-behavior: smooth;
  -webkit-tap-highlight-color: transparent;
}

.scroll-mode__page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: var(--gallery-page-min-height); /* 256dp placeholder height */
  margin-bottom: var(--gallery-scroll-interval); /* 28dp page interval */
}

.scroll-mode__img {
  display: block;
  width: 100%;
  height: auto;
  object-fit: contain;
  opacity: 0;
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
  user-select: none;
}

.scroll-mode__img--loaded {
  opacity: 1;
}

.scroll-mode__loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scroll-mode-fade-enter-active,
.scroll-mode-fade-leave-active {
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.scroll-mode-fade-enter-from,
.scroll-mode-fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .scroll-mode {
    scroll-behavior: auto;
  }
}
</style>
