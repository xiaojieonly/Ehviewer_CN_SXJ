<template>
  <div class="history-view">
    <div class="history-view__heading">
      <h1 class="history-view__title">History</h1>
      <span v-if="state === 'content'" class="history-view__count">
        {{ entries.length }} galleries
      </span>
    </div>

    <!-- Server-side search + filter slots (A5d): 防抖 q 搜索，与筛选槽位互斥
         （useFilterSlots——选槽位清搜索、输入搜索取消槽位）。 -->
    <div class="search-bar">
      <AppIcon name="magnify-dark" size="18px" />
      <input
        v-model="searchQuery"
        class="search-bar__input"
        type="search"
        :placeholder="filterSlot ? `筛选：${filterSlot.name}` : '搜索标题…'"
        aria-label="搜索历史"
      />
      <button
        v-if="searchQuery"
        type="button"
        class="search-bar__clear"
        aria-label="清除搜索"
        @click="clearSearch"
      >
        <AppIcon name="close-dark" size="16px" />
      </button>
    </div>

    <FilterSlotBar :slots="slots" :active-id="activeSlotId" @select="onSlotBarSelect" />

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
      <!-- Shared gallery list (B-1): renders the grid/list form from
           prefs.general.listMode (R4-1 — no hardcoded layout anymore).
           List form: the absolute corner badge (item-extra). Grid form: the
           last-viewed stamp flows inside the card meta as a second line
           (item-sub, F-UX1 — the corner badge is hidden there). -->
      <GalleryList :items="galleries" @select="openGallery">
        <template #item-extra="{ index }">
          <span
            v-if="entries[index]"
            class="time-badge"
            :title="`Last viewed ${new Date(entries[index].time).toLocaleString()}`"
          >
            <AppIcon name="history-black" size="14px" />
            {{ formatViewTime(entries[index].time) }}
          </span>
        </template>
        <template #item-sub="{ index }">
          <span
            v-if="entries[index]"
            class="time-row"
            :title="`Last viewed ${new Date(entries[index].time).toLocaleString()}`"
          >
            <AppIcon name="history-black" size="12px" />
            {{ formatViewTime(entries[index].time) }}
          </span>
        </template>
      </GalleryList>
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
 * the shared `GalleryList` (B-1 — grid/list form follows
 * `prefs.general.listMode`), each row stamped with the last-viewed time
 * (`item_history.xml` shows the same horizontal card layout), plus a
 * FabLayout with clear-all (confirmed via dialog) and go-to-top.
 *
 * The history endpoint returns the full list in one shot (no paging), so
 * `load-more` is intentionally not wired. Search (q, debounced) and filter
 * slots (A5d, q=pattern&regex=true) narrow the list server-side (in-memory
 * filter over the full set); the two are mutually exclusive.
 *
 * Backend note: `HistoryItem.category` is the stringified `SiteConfig` bit
 * (HistoryService maps `entity.category.toString()`), so rows are converted
 * to `GalleryInfo` (numeric bit) before being handed to the list.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { historyApi } from '@/api/history'
import type { HistoryItem } from '@/api/history'
import { useFilterSlots } from '@/composables/useFilterSlots'
import FilterSlotBar from '@/components/FilterSlotBar.vue'
import {
  CATEGORY_BIT_VALUES,
  CATEGORY_LABELS,
  CATEGORY_ORDER,
  type FabAction,
  type GalleryInfo,
} from '@/types/components'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import GalleryList from '@/components/gallery/GalleryList.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

/** View states matching ContentLayout's internal ViewTransition. */
type ViewState = 'loading' | 'content' | 'empty' | 'error'

/** Unknown category fallback — Android `SiteUtils.UNKNOWN` bit. */
const CATEGORY_UNKNOWN_BIT = 0x400

/* ---------------------------------------------- category string → bit --- */

const NAME_TO_BIT = new Map<string, number>()
for (const key of CATEGORY_ORDER) {
  NAME_TO_BIT.set(CATEGORY_LABELS[key].toLowerCase(), CATEGORY_BIT_VALUES[key])
  NAME_TO_BIT.set(key, CATEGORY_BIT_VALUES[key])
}

/**
 * Normalizes a backend category value to its `SiteConfig` bit value.
 * Accepts integer bits (2), stringified bits ("2"), labels ("Artist CG") and
 * keys ("artist_cg").
 */
function categoryBit(raw: number | string): number {
  if (typeof raw === 'number') return raw
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

/** Gallery payloads for the shared GalleryList (parallel to `entries`). */
const galleries = computed(() => entries.value.map((entry) => entry.gallery))

/** Maps a backend history row onto the `GalleryInfo` shape the list renders. */
function toGalleryInfo(item: HistoryItem): GalleryInfo {
  return {
    gid: item.gid,
    token: item.token,
    // R4-6: title-less galleries surface as `#<gid>`, not "Untitled".
    title: item.title || item.titleJpn || `#${item.gid}`,
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

/* ---------------------------------------- search + filter slots (A5d) ----- */

/** 搜索词：防抖后作为 q 传给 /history/list（服务端过滤）。 */
const searchQuery = ref('')
const debouncedQuery = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

/** 筛选槽位（A5d）：命名正则预设；与搜索框互斥（useFilterSlots 保证）。 */
const { slots, activeSlotId, activeSlot: filterSlot, selectSlot: selectFilterSlot } =
  useFilterSlots(searchQuery)

function onSlotBarSelect(id: string | null): void {
  selectFilterSlot(id)
  // 槽位点击总是重新加载（清空搜索词不一定触发防抖 watch——搜索词本来就空时）。
  state.value = 'loading'
  void load()
}

/** 当前筛选条件：槽位激活 → (q=pattern, regex=true)；否则 → 搜索词（LIKE）。 */
function currentFilter(): { q: string | null; regex: boolean } {
  const slot = filterSlot.value
  if (slot) return { q: slot.pattern, regex: true }
  return { q: debouncedQuery.value || null, regex: false }
}

watch(searchQuery, (next) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (debouncedQuery.value !== next) {
      debouncedQuery.value = next
      // 槽位激活时该变更来自 selectFilterSlot 清空搜索词——加载已由
      // onSlotBarSelect 触发（避免与槽位过滤重复请求）。
      if (filterSlot.value) return
      state.value = 'loading'
      void load()
    }
  }, 400)
})

function clearSearch(): void {
  searchQuery.value = ''
  debouncedQuery.value = ''
  state.value = 'loading'
  void load()
}

async function load(): Promise<void> {
  try {
    const filter = currentFilter()
    const data = await historyApi.listHistory(filter.q || null, filter.regex || undefined)
    entries.value = data.history.map((item) => ({
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
  /* Standalone PWA: push the header row + heading below the status bar /
     cutout. border-box keeps the column at 100dvh — the flex:1
     ContentLayout shrinks instead of overflowing. The list bottom already
     clears the home indicator via --gallery-padding-bottom-fab. */
  padding-top: var(--safe-area-top);
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

/* ------------------------------------------------- server-side search ---- */
.search-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  margin: var(--spacing) var(--keyline-margin) 8px;
  padding: 0 10px;
  border: 1px solid var(--color-divider);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--text-color-secondary);
}

.search-bar__input {
  flex: 1 1 auto;
  min-width: 0;
  padding: 8px 0;
  border: none;
  background: transparent;
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-small);
  outline: none;
}

.search-bar__input::placeholder {
  color: var(--text-color-secondary);
}

.search-bar__clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-color-secondary);
  cursor: pointer;
}

.search-bar__clear:hover {
  background: var(--color-surface-activated);
}

/* -------------------------------------------------------------- list ---- */
/* Row layout, entrance animation and stagger live in GalleryList (B-1);
   only the history-specific last-viewed badge is styled here. */

/* Last-viewed timestamp — clock glyph + compact date/time, secondary ink.
   List-form only: an absolute corner badge over the horizontal card. */
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

/* F-UX1: in the grid form the corner badge would sit on top of the card
   title — the last-viewed stamp renders inside the card meta instead
   (`.time-row` below), so the badge is suppressed there. */
.gallery-grid__cell .time-badge {
  display: none;
}

/* Grid-form last-viewed line — flows inside the card meta region as the
   second line below the title (F-UX1), stretching the card instead of
   overlapping the title. */
.time-row {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow: hidden;
  color: var(--text-color-secondary);
  font-size: inherit;
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
  opacity: 1;
  animation: scrim-in 160ms linear;
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
  opacity: 1;
  animation: dialog-in 200ms var(--ease-decelerate-quart);
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
  .dialog-scrim,
  .dialog {
    animation: none;
  }
}
</style>
