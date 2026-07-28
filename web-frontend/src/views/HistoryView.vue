<template>
  <div class="history-view">
    <AppHeader />

    <div class="history-view__heading">
      <h1 class="history-view__title">History</h1>
      <span v-if="state === 'content'" class="history-view__count">
        {{ entries.length }} galleries
      </span>
    </div>

    <ContentLayout
      ref="contentRef"
      class="history-view__content"
      :state="state"
      v-model:refreshing="refreshing"
      empty-text="No history"
      error-text="Failed to load history"
      @refresh="onRefresh"
      @retry="onRetry"
    >
      <ul class="gallery-list">
        <li
          v-for="(entry, index) in entries"
          :key="entry.gallery.gid"
          class="gallery-list__row"
          :style="{ animationDelay: `${Math.min(index * 24, 240)}ms` }"
        >
          <!-- Same horizontal card as the gallery list (AppCard, list mode) -->
          <AppCard :gallery="entry.gallery" mode="list" @click="openGallery" />
          <!-- Last-viewed timestamp -->
          <span
            class="time-badge"
            :title="`Last viewed ${new Date(entry.time).toLocaleString()}`"
          >
            <AppIcon name="history-black" size="14px" />
            {{ formatViewTime(entry.time) }}
          </span>
        </li>
      </ul>
    </ContentLayout>

    <!-- FabLayout replica: clear-history + back-to-top mini FABs -->
    <FabLayout
      v-model:expanded="fabExpanded"
      primary-icon="reorder"
      :actions="fabActions"
      @click-secondary="onFabAction"
    />

    <Teleport to="body">
      <!-- Clear-history confirmation (Android AlertDialog replica) -->
      <div
        v-if="showClearDialog"
        class="dialog-scrim"
        @click.self="closeClearDialog"
      >
        <div
          class="dialog"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="clear-history-title"
          aria-describedby="clear-history-message"
          @keyup.esc="closeClearDialog"
        >
          <h3 id="clear-history-title" class="dialog__title">Clear history</h3>
          <p id="clear-history-message" class="dialog__message">
            Clear all viewing history? This cannot be undone.
          </p>
          <div class="dialog__actions">
            <button type="button" class="dialog__btn" @click="closeClearDialog">Cancel</button>
            <button
              type="button"
              class="dialog__btn dialog__btn--danger"
              :disabled="clearing"
              @click="confirmClear"
            >
              {{ clearing ? 'Clearing…' : 'Clear' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
/**
 * HistoryView — web replica of Android `HistoryScene`:
 * ContentLayout (pull-to-refresh + empty tip + fast scroller) filled with
 * the standard gallery list card, each row stamped with the last-viewed
 * time (`item_history.xml` shows the same horizontal card layout), plus a
 * FabLayout with clear-all (confirmed via dialog) and go-to-top.
 *
 * The history endpoint returns the full list in one shot (no paging), so
 * `load-more` is intentionally not wired.
 *
 * Backend note: `HistoryItem.category` is the stringified `EhConfig` bit
 * (HistoryService maps `entity.category.toString()`), so rows are converted
 * to `GalleryInfo` (numeric bit) before being handed to `AppCard`.
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { historyApi } from '@/api/history'
import type { HistoryItem } from '@/api/history'
import {
  CATEGORY_BIT_VALUES,
  CATEGORY_LABELS,
  CATEGORY_ORDER,
  type FabAction,
  type GalleryInfo,
} from '@/types/components'
import AppHeader from '@/components/layout/AppHeader.vue'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import AppCard from '@/components/atoms/AppCard.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

/** View states matching ContentLayout's internal ViewTransition. */
type ViewState = 'loading' | 'content' | 'empty' | 'error'

/** Unknown category fallback — Android `EhUtils.UNKNOWN` bit. */
const CATEGORY_UNKNOWN_BIT = 0x400

/* ---------------------------------------------- category string → bit --- */

const NAME_TO_BIT = new Map<string, number>()
for (const key of CATEGORY_ORDER) {
  NAME_TO_BIT.set(CATEGORY_LABELS[key].toLowerCase(), CATEGORY_BIT_VALUES[key])
  NAME_TO_BIT.set(key, CATEGORY_BIT_VALUES[key])
}

/**
 * Normalizes a backend category string to its `EhConfig` bit value.
 * Accepts stringified bits ("2"), labels ("Artist CG") and keys ("artist_cg").
 */
function categoryBit(raw: string): number {
  const trimmed = raw.trim()
  if (trimmed !== '' && !Number.isNaN(Number(trimmed))) return Number(trimmed)
  return NAME_TO_BIT.get(trimmed.toLowerCase()) ?? CATEGORY_UNKNOWN_BIT
}

/* --------------------------------------------------------------- data --- */

const router = useRouter()

/** A history row: the gallery card payload + the last-viewed epoch (ms). */
interface HistoryEntry {
  gallery: GalleryInfo
  time: number
}

const entries = ref<HistoryEntry[]>([])
const state = ref<ViewState>('loading')
const refreshing = ref(false)
const contentRef = ref<InstanceType<typeof ContentLayout> | null>(null)

/** Maps a backend history row onto the `GalleryInfo` shape AppCard renders. */
function toGalleryInfo(item: HistoryItem): GalleryInfo {
  return {
    gid: item.gid,
    token: item.token,
    title: item.title || item.titleJpn || 'Untitled',
    titleJpn: item.titleJpn,
    thumb: item.thumb,
    category: categoryBit(item.category),
    posted: '',
    uploader: '',
    rating: item.rating,
    rated: false,
    simpleLanguage: '',
    simpleTags: [],
    thumbWidth: 0,
    thumbHeight: 0,
    pages: 0,
    favoriteSlot: -2,
    favoriteName: '',
  }
}

async function load(): Promise<void> {
  try {
    const response = await historyApi.listHistory()
    entries.value = response.history.map((item) => ({
      gallery: toGalleryInfo(item),
      time: item.time,
    }))
    state.value = entries.value.length === 0 ? 'empty' : 'content'
  } catch (error) {
    console.error('Failed to load history', error)
    if (entries.value.length === 0) state.value = 'error'
  }
}

async function onRefresh(): Promise<void> {
  await load()
  refreshing.value = false
}

function onRetry(): void {
  state.value = 'loading'
  void load()
}

function openGallery(gallery: GalleryInfo): void {
  void router.push(`/gallery/${gallery.gid}`)
}

/* ---------------------------------------------------------- timestamp --- */

const DAY_MS = 86_400_000

/** "Today 14:32" / "Yesterday 09:10" / "7/28/2026 09:10" — compact stamp. */
function formatViewTime(timestamp: number): string {
  const date = new Date(timestamp)
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const time = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  if (timestamp >= startOfToday) return `Today ${time}`
  if (timestamp >= startOfToday - DAY_MS) return `Yesterday ${time}`
  return `${date.toLocaleDateString()} ${time}`
}

/* --------------------------------------------------------------- FAB ---- */

const fabExpanded = ref(false)

const fabActions: FabAction[] = [
  { id: 'clear', icon: 'clear-all-dark', label: 'Clear history' },
  { id: 'scroll-top', icon: 'go-to-dark', label: 'Back to top' },
]

function onFabAction(action: FabAction): void {
  fabExpanded.value = false
  if (action.id === 'clear') {
    showClearDialog.value = true
  } else if (action.id === 'scroll-top') {
    contentRef.value?.scrollToTop()
  }
}

/* ------------------------------------------------- clear confirmation --- */

const showClearDialog = ref(false)
const clearing = ref(false)

function closeClearDialog(): void {
  if (!clearing.value) showClearDialog.value = false
}

async function confirmClear(): Promise<void> {
  if (clearing.value) return
  clearing.value = true
  try {
    await historyApi.clearHistory()
    entries.value = []
    state.value = 'empty'
    showClearDialog.value = false
  } catch (error) {
    console.error('Failed to clear history', error)
  } finally {
    clearing.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.history-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  background: var(--color-bg);
}

.history-view__content {
  flex: 1;
  min-height: 0;
}

/* ------------------------------------------------------------ heading --- */
.history-view__heading {
  display: flex;
  align-items: baseline;
  gap: var(--spacing);
  flex-shrink: 0;
  padding: 14px max(var(--gallery-list-margin-h), 4px);
  border-bottom: 1px solid var(--color-divider);
}

.history-view__title {
  font-size: var(--text-super-large); /* 24sp */
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-color-primary);
}

.history-view__count {
  font-size: var(--text-super-small); /* 12sp */
  color: var(--text-color-secondary);
  font-variant-numeric: tabular-nums;
}

/* -------------------------------------------------------------- list ---- */
.gallery-list {
  list-style: none;
  margin: 0;
  padding: var(--gallery-list-margin-v) var(--gallery-list-margin-h)
    var(--gallery-padding-bottom-fab);
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, var(--column-width-list-long)), 1fr));
}

.gallery-list__row {
  position: relative;
  animation: item-in 240ms var(--ease-decelerate-quart) both;
}

@keyframes item-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
}

/* Last-viewed timestamp — clock glyph + compact date/time, secondary ink. */
.time-badge {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-color-secondary);
  font-size: var(--text-super-small); /* 12sp */
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  pointer-events: none;
}

/* ------------------------------------------------------------- dialog --- */
.dialog-scrim {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--keyline-margin);
  background: var(--black-overlay);
  animation: scrim-in 160ms linear both;
}

@keyframes scrim-in {
  from {
    opacity: 0;
  }
}

.dialog {
  width: 100%;
  max-width: 360px;
  padding: 20px var(--keyline-margin) var(--spacing);
  background: var(--color-background-floating);
  border-radius: var(--card-radius);
  box-shadow: 0 6px 24px var(--shadow-color);
  animation: dialog-in 200ms var(--ease-decelerate-quart) both;
}

@keyframes dialog-in {
  from {
    opacity: 0;
    transform: scale(0.96) translateY(6px);
  }
}

.dialog__title {
  margin: 0 0 var(--spacing);
  font-size: var(--text-medium); /* 18sp */
  font-weight: 600;
  color: var(--text-color-primary);
}

.dialog__message {
  margin: 0;
  font-size: var(--text-small); /* 14sp */
  line-height: 1.5;
  color: var(--text-color-secondary);
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing);
  margin-top: var(--keyline-margin);
}

.dialog__btn {
  padding: 8px 12px;
  border: none;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--button-text-color);
  font-family: inherit;
  font-size: var(--text-small);
  font-weight: 500;
  cursor: pointer;
  transition: background-color 140ms var(--ease-decelerate-quart);
}

.dialog__btn:hover {
  background: var(--color-surface-activated);
}

.dialog__btn--danger {
  color: var(--color-red-500);
}

.dialog__btn:disabled {
  color: var(--text-color-secondary);
  opacity: 0.5;
  cursor: default;
}

.dialog__btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

@media (prefers-reduced-motion: reduce) {
  .gallery-list__row,
  .dialog-scrim,
  .dialog {
    animation: none;
  }
}
</style>
