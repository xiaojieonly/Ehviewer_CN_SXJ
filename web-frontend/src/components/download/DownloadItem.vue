<template>
  <article
    class="download-item"
    :class="`download-item--${stateKey}`"
    :aria-label="`${title} — ${stateLabel}`"
  >
    <!-- FixedThumb replica: 80×120dp, CENTER_CROP (item_download.xml) -->
    <div class="download-item__thumb">
      <img v-if="item.thumb" :src="item.thumb" :alt="title" loading="lazy" />
      <div v-else class="download-item__thumb-placeholder" aria-hidden="true">
        <AppIcon name="download-primary" size="28px" />
      </div>
    </div>

    <div class="download-item__body">
      <!-- CardTitle: 16sp, maxLines 2, end-ellipsize -->
      <h3 class="download-item__title" :title="title">{{ title }}</h3>

      <div class="download-item__meta">
        <CategoryChip v-if="categoryKey" :category="categoryKey" />
        <span v-if="item.total > 0" class="download-item__pages">
          {{ item.done }}/{{ item.total }} pages
        </span>
      </div>

      <!-- percent (left) + speed/ETA (right) — both text_super_small 12sp -->
      <div class="download-item__stats">
        <span class="download-item__percent">{{ percentText }}</span>
        <span v-if="statsText" class="download-item__speed">{{ statsText }}</span>
      </div>

      <!-- Horizontal ProgressBar replica (determinate; slides when total is
           still unknown, mirroring Android's indeterminate fallback) -->
      <div
        class="download-item__track"
        role="progressbar"
        :aria-label="`Download progress for ${title}`"
        :aria-valuemin="0"
        :aria-valuemax="100"
        :aria-valuenow="indeterminate ? undefined : percent"
      >
        <div
          class="download-item__fill"
          :class="{
            'download-item__fill--indeterminate': indeterminate,
            'download-item__fill--sheen': isDownloading && !indeterminate,
          }"
          :style="indeterminate ? undefined : { width: `${percent}%` }"
        />
      </div>

      <div class="download-item__footer">
        <!-- State text (Android: textColorThemeAccent, above the actions) -->
        <span class="download-item__state">
          <span class="download-item__state-dot" aria-hidden="true" />
          {{ stateLabel }}
        </span>

        <!-- Action cluster: 40dp icons with 8dp padding, as in item_download.xml -->
        <div class="download-item__actions">
          <button
            v-if="canStart"
            type="button"
            class="download-item__action"
            title="Start"
            aria-label="Start download"
            @click="emit('start', item.id)"
          >
            <AppIcon name="play-dark" size="24px" />
          </button>
          <button
            v-if="canPause"
            type="button"
            class="download-item__action"
            title="Pause"
            aria-label="Pause download"
            @click="emit('pause', item.id)"
          >
            <AppIcon name="pause-dark" size="24px" />
          </button>
          <button
            v-if="canCancel"
            type="button"
            class="download-item__action"
            title="Stop"
            aria-label="Stop download"
            @click="emit('cancel', item.id)"
          >
            <AppIcon name="close-dark" size="24px" />
          </button>
          <button
            type="button"
            class="download-item__action download-item__action--danger"
            title="Delete"
            aria-label="Delete download"
            @click="emit('delete', item.id)"
          >
            <AppIcon name="delete-dark" size="24px" />
          </button>
        </div>
      </div>

      <!-- Failure reason (DownloadInfo.error), secondary text under the state -->
      <p v-if="isFailed && errorText" class="download-item__error" :title="errorText">
        {{ errorText }}
      </p>
    </div>
  </article>
</template>

<script setup lang="ts">
/**
 * DownloadItem — web replica of `item_download.xml` (DownloadsScene row):
 * CardView (2dp radius/elevation/margin) + 80×120dp FixedThumb + CardTitle
 * (2-line clamp) + category tag + percent/speed row + horizontal ProgressBar
 * + state text + start/stop action icons (40dp, `v_play_x24` / `v_pause_x24`).
 *
 * State model = Android `DownloadInfo.STATE_*` (`dao/DownloadInfo.java`):
 * NONE 0 (idle) / WAIT 1 / DOWNLOAD 2 / FINISH 3 / FAILED 4.
 * State colors per the S4 spec: downloading = accent, idle/waiting = grey,
 * done = category-green, failed = red-500.
 *
 * Live transfer rate arrives via the `speed` prop (pages/s, computed by the
 * host view from the `/topic/download/all` WebSocket feed — the backend
 * currently always emits `speed = 0`, so the view derives it from progress
 * deltas). ETA = remaining pages / speed.
 */
import { computed } from 'vue'
import type { DownloadItem } from '@/api/download'
import { CATEGORY_BY_BIT } from '@/types/components'
import AppIcon from '@/components/atoms/AppIcon.vue'
import CategoryChip from '@/components/atoms/CategoryChip.vue'

/** Android `DownloadInfo.STATE_*` constants (dao/DownloadInfo.java:33-38). */
const STATE_NONE = 0
const STATE_WAIT = 1
const STATE_DOWNLOAD = 2
const STATE_FINISH = 3
const STATE_FAILED = 4

const props = withDefaults(
  defineProps<{
    /** Download row from `/download/list`; `state` is a `DownloadInfo.STATE_*`. */
    item: DownloadItem
    /** Live transfer rate in pages/second (WebSocket feed). 0 = unknown. */
    speed?: number
  }>(),
  { speed: 0 },
)

const emit = defineEmits<{
  /** Start / resume (Android start icon → DownloadManager.start). */
  (e: 'start', id: number): void
  /** Pause an active download (Android stop icon → DownloadManager.pause). */
  (e: 'pause', id: number): void
  /** Cancel / abort the current transfer. */
  (e: 'cancel', id: number): void
  /** Remove the entry from the download list. */
  (e: 'delete', id: number): void
}>()

const title = computed(() => props.item.title || props.item.titleJpn || 'Untitled')

/** Numeric `SiteConfig` category bit → chip key (undefined renders no chip). */
const categoryKey = computed(() => CATEGORY_BY_BIT[props.item.category])

const isDownloading = computed(() => props.item.state === STATE_DOWNLOAD)
const isFailed = computed(() => props.item.state === STATE_FAILED)

/** Failure reason from the backend; empty when the item has none. */
const errorText = computed(() => props.item.error?.trim() || '')

/** Downloading with an unknown page count → sliding indeterminate bar. */
const indeterminate = computed(() => isDownloading.value && props.item.total <= 0)

const percent = computed(() =>
  props.item.total > 0
    ? Math.min(100, Math.round((props.item.done / props.item.total) * 100))
    : 0,
)

const percentText = computed(() => (props.item.total > 0 ? `${percent.value}%` : '—'))

/** CSS modifier key for the current state. */
const stateKey = computed(() => {
  switch (props.item.state) {
    case STATE_NONE:
      return 'idle'
    case STATE_WAIT:
      return 'wait'
    case STATE_DOWNLOAD:
      return 'download'
    case STATE_FINISH:
      return 'finish'
    case STATE_FAILED:
      return 'failed'
    default:
      return 'failed'
  }
})

/** Android `download_state_*` strings (values-en/strings.xml:377-383). */
const stateLabel = computed(() => {
  switch (props.item.state) {
    case STATE_NONE:
      return 'Idle'
    case STATE_WAIT:
      return 'Waiting'
    case STATE_DOWNLOAD:
      return 'Downloading'
    case STATE_FINISH:
      return 'Done'
    default:
      return 'Failed'
  }
})

/** Android shows the start icon when idle or failed (retry). */
const canStart = computed(
  () => props.item.state === STATE_NONE || props.item.state === STATE_FAILED,
)
const canPause = computed(() => isDownloading.value)
const canCancel = computed(() => props.item.state === STATE_WAIT || isDownloading.value)

/** Seconds remaining at the current rate (0 = not computable). */
const etaSeconds = computed(() => {
  if (props.speed <= 0 || props.item.total <= 0) return 0
  return Math.max(0, props.item.total - props.item.done) / props.speed
})

function formatEta(seconds: number): string {
  const s = Math.round(seconds)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (h > 0) return `${h}h ${m}m`
  const sec = s % 60
  if (m > 0) return `${m}m ${sec}s`
  return `${sec}s`
}

/** Right-hand stats text: rate + ETA while downloading. */
const statsText = computed(() => {
  if (!isDownloading.value) return ''
  if (props.speed <= 0) return 'Fetching…'
  const rate = props.speed >= 10 ? props.speed.toFixed(0) : props.speed.toFixed(1)
  const eta = etaSeconds.value > 0 ? ` · ETA ${formatEta(etaSeconds.value)}` : ''
  return `${rate} pages/s${eta}`
})
</script>

<style scoped>
/* CardView.Reactive replica: 2dp radius / elevation / margin, theme surface. */
.download-item {
  display: flex;
  align-items: stretch;
  background: var(--color-surface);
  border-radius: var(--card-radius);
  box-shadow: 0 var(--card-elevation) var(--card-max-elevation) var(--shadow-color);
  margin: 2px;
  overflow: hidden;
  transition:
    box-shadow 160ms var(--ease-decelerate-quart),
    transform 160ms var(--ease-decelerate-quart);
}

.download-item:hover {
  box-shadow: 0 var(--card-elevation) calc(var(--card-max-elevation) * 3) var(--shadow-color);
  transform: translateY(-1px);
}

/* ------------------------------------------------------------- thumb --- */
.download-item__thumb {
  flex: 0 0 var(--thumb-list-width); /* 80px */
  width: var(--thumb-list-width);
  height: var(--thumb-list-height); /* 120px */
  overflow: hidden;
  background: var(--color-divider);
}

.download-item__thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover; /* FixedThumb CENTER_CROP */
  transition: transform 300ms var(--ease-decelerate-quart);
}

.download-item:hover .download-item__thumb img {
  transform: scale(1.04);
}

.download-item__thumb-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--drawable-color-secondary);
}

/* -------------------------------------------------------------- body --- */
.download-item__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: var(--spacing); /* 8px, item_download.xml margins */
}

.download-item__title {
  margin: 0;
  font-size: var(--text-little-small); /* 16sp CardTitle */
  font-weight: 500;
  line-height: 1.35;
  color: var(--text-color-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.download-item__meta {
  display: flex;
  align-items: center;
  gap: var(--spacing);
  min-width: 0;
}

.download-item__pages {
  margin-left: auto;
  font-size: var(--text-super-small); /* 12sp */
  color: var(--text-color-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* ------------------------------------------------------------- stats --- */
.download-item__stats {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing);
  font-size: var(--text-super-small); /* 12sp, percent + speed row */
}

.download-item__percent {
  color: var(--text-color-primary);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.download-item__speed {
  color: var(--text-color-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ----------------------------------------------------- progress bar --- */
.download-item__track {
  height: 4px;
  border-radius: 2px;
  overflow: hidden;
  background: var(--grey-300); /* S4 spec: grey-300 track (light) */
}

/* Theme-aware tracks matching Android progress_dark / progress_black. */
[data-theme='dark'] .download-item__track {
  background: var(--grey-600);
}

[data-theme='black'] .download-item__track {
  background: var(--grey-700);
}

.download-item__fill {
  position: relative;
  height: 100%;
  border-radius: 2px;
  overflow: hidden;
  background: var(--grey-500);
  transition:
    width 300ms var(--ease-decelerate-quart),
    background-color 200ms linear;
}

/* State colors: downloading = accent, idle/wait = grey, done = green,
   failed = red (S4 style spec). */
.download-item--download .download-item__fill {
  background: var(--color-accent);
}

.download-item--finish .download-item__fill {
  background: var(--color-cat-game-cg);
}

.download-item--failed .download-item__fill {
  background: var(--color-red-500);
}

/* Live sheen sweeping across the fill while downloading. */
.download-item__fill--sheen::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 0%, var(--translucent-bg) 50%, transparent 100%);
  transform: translateX(-100%);
  animation: dl-sheen 1300ms linear infinite;
}

@keyframes dl-sheen {
  to {
    transform: translateX(100%);
  }
}

/* Unknown page count: Material-style sliding segment. */
.download-item__fill--indeterminate {
  width: 40%;
  background: var(--color-accent);
  animation: dl-slide 1400ms var(--ease-decelerate-quart) infinite;
}

@keyframes dl-slide {
  0% {
    margin-left: -40%;
  }
  100% {
    margin-left: 100%;
  }
}

/* ------------------------------------------------------------ footer --- */
.download-item__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing);
  margin-top: auto;
}

.download-item__state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-super-small); /* 12sp */
  font-weight: 500;
  color: var(--grey-500);
}

.download-item--download .download-item__state {
  color: var(--color-accent);
}

.download-item--finish .download-item__state {
  color: var(--color-cat-game-cg);
}

.download-item--failed .download-item__state {
  color: var(--color-red-500);
}

.download-item__state-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.download-item--download .download-item__state-dot {
  animation: dl-pulse 1000ms ease-in-out infinite;
}

@keyframes dl-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

/* ----------------------------------------------------- failure reason --- */
.download-item__error {
  margin: 0;
  font-size: var(--text-super-small); /* 12sp */
  color: var(--text-color-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ----------------------------------------------------------- actions --- */
.download-item__actions {
  display: flex;
  align-items: center;
}

/* 40dp touch targets with 24dp glyphs (item_download.xml ImageViews). */
.download-item__action {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-primary);
  cursor: pointer;
  transition:
    background-color 140ms var(--ease-decelerate-quart),
    color 140ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.download-item__action:hover {
  background: var(--color-surface-activated);
}

.download-item__action:active {
  transform: scale(0.88);
}

.download-item__action:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.download-item__action--danger:hover {
  color: var(--color-red-500);
}

@media (prefers-reduced-motion: reduce) {
  .download-item__fill--sheen::after,
  .download-item__fill--indeterminate,
  .download-item--download .download-item__state-dot,
  .download-item:hover .download-item__thumb img {
    animation: none;
    transform: none;
  }
}
</style>
