<template>
  <div class="download-view">
    <!-- Download label tabs (Android DownloadsScene label spinner, reimagined
         as a scrollable tab strip; active tab gets the primary underline) -->
    <nav class="label-tabs" aria-label="Download labels">
      <button
        v-for="tab in tabs"
        :key="tab.id ?? 'all'"
        type="button"
        class="label-tabs__tab"
        :class="{ 'label-tabs__tab--active': activeLabel === tab.id }"
        :aria-current="activeLabel === tab.id ? 'true' : undefined"
        @click="selectTab(tab.id, $event)"
      >
        {{ tab.name }}
      </button>
    </nav>

    <!-- Server-side search (Android download_search_dialog replica): 输入即
         防抖搜索，q 走 /download/list 服务端过滤，负载留在服务器。 -->
    <div class="search-bar">
      <AppIcon name="magnify-dark" size="18px" />
      <input
        v-model="searchQuery"
        class="search-bar__input"
        type="search"
        placeholder="搜索标题…"
        aria-label="搜索下载"
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

    <!-- Multi-select toolbar (Android custom choice mode: 全选/开始/停止/
         删除/移动；长按或右键条目进入) -->
    <div
      v-if="selectMode"
      class="select-bar"
      role="toolbar"
      aria-label="批量操作"
      @keyup.esc="exitSelectMode"
    >
      <span class="select-bar__count" role="status">
        共 {{ total }} 条 · 已选 {{ selectedIds.size }} 条
      </span>
      <div class="select-bar__actions">
        <button type="button" class="select-bar__btn" @click="selectAll">全选</button>
        <button
          type="button"
          class="select-bar__btn"
          :disabled="selectedStartableIds.length === 0"
          @click="onBatchStart"
        >
          开始
        </button>
        <button
          type="button"
          class="select-bar__btn"
          :disabled="selectedStoppableIds.length === 0"
          @click="onBatchStop"
        >
          停止
        </button>
        <button
          type="button"
          class="select-bar__btn"
          :disabled="selectedIds.size === 0"
          @click="onBatchMove"
        >
          移动
        </button>
        <button
          type="button"
          class="select-bar__btn select-bar__btn--danger"
          :disabled="selectedIds.size === 0"
          @click="onBatchDelete"
        >
          删除
        </button>
        <button
          type="button"
          class="select-bar__close"
          aria-label="退出多选"
          @click="exitSelectMode"
        >
          <AppIcon name="close-dark" size="18px" />
        </button>
      </div>
    </div>

    <ContentLayout
      ref="contentRef"
      class="download-view__content"
      :state="state"
      :loading-more="loadingMore"
      v-model:refreshing="refreshing"
      empty-text="No downloads"
      error-text="Failed to load downloads"
      @refresh="onRefresh"
      @retry="onRetry"
    >
      <!-- Virtualized single-column list: only the rows inside the scroller
           viewport (+ overscan) are mounted; the ul keeps the full total
           height so the scrollbar, FastScroller and load-more footer
           geometry stay intact (tanstack virtualizer window mode). -->
      <ul
        ref="listHostRef"
        class="download-list"
        :style="{ height: `${virtualizer.getTotalSize()}px` }"
      >
        <li
          v-for="item in virtualizer.getVirtualItems()"
          :key="`${item.key}`"
          class="download-list__item"
          :style="{
            transform: `translateY(${item.start}px)`,
            animationDelay: `${Math.min(item.index * 24, 240)}ms`,
          }"
        >
          <DownloadItemCard
            :item="downloads[item.index]"
            :speed="liveSpeeds[downloads[item.index]?.gid ?? -1] ?? 0"
            :selectable="selectMode"
            :selected="selectedIds.has(downloads[item.index].id)"
            @start="onStart"
            @pause="onPause"
            @cancel="onCancel"
            @delete="onDelete"
            @menu="onItemMenu"
            @select="onItemSelect"
          />
        </li>
      </ul>
    </ContentLayout>

    <!-- FabLayout replica: primary 56dp FAB + mini FABs
         (scene_download.xml: play / pause / … cluster); hidden in select mode
         (Android choice mode replaces the FAB cluster with the action bar). -->
    <FabLayout
      v-if="!selectMode"
      v-model:expanded="fabExpanded"
      primary-icon="plus-dark"
      :actions="fabActions"
      @click-secondary="onFabAction"
    />

    <Teleport to="body">
      <!-- New label dialog (Android EditTextDialog replica) -->
      <div
        v-if="showLabelDialog"
        class="dialog-scrim"
        @click.self="closeLabelDialog"
      >
        <div
          class="dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="new-label-title"
          @keyup.esc="closeLabelDialog"
        >
          <h3 id="new-label-title" class="dialog__title">New label</h3>
          <input
            ref="labelInputRef"
            v-model="labelName"
            class="dialog__input"
            type="text"
            maxlength="20"
            placeholder="Label name"
            autocomplete="off"
            @keyup.enter="createLabel"
          />
          <p v-if="labelError" class="dialog__error" role="alert">{{ labelError }}</p>
          <div class="dialog__actions">
            <button type="button" class="dialog__btn" @click="closeLabelDialog">Cancel</button>
            <button
              type="button"
              class="dialog__btn dialog__btn--primary"
              :disabled="!labelName.trim() || labelSaving"
              @click="createLabel"
            >
              {{ labelSaving ? 'Creating…' : 'Create' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Move-to-label dialog (Android MoveDialogHelper: pick the target
           label for the selected downloads; "默认标签" = labelId 0). -->
      <div
        v-if="showMoveDialog"
        class="dialog-scrim"
        @click.self="closeMoveDialog"
      >
        <div
          class="dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="move-label-title"
          @keyup.esc="closeMoveDialog"
        >
          <h3 id="move-label-title" class="dialog__title">Move to label</h3>
          <ul class="dialog__label-list">
            <li>
              <button type="button" class="dialog__label-option" @click="confirmMove(0)">
                默认标签
              </button>
            </li>
            <li v-for="label in labels" :key="label.id">
              <button type="button" class="dialog__label-option" @click="confirmMove(label.id)">
                {{ label.label }}
              </button>
            </li>
          </ul>
          <div class="dialog__actions">
            <button type="button" class="dialog__btn" @click="closeMoveDialog">Cancel</button>
          </div>
        </div>
      </div>

      <!-- Toast (Android Toast equivalent) -->
      <div v-if="toastMessage" class="toast" role="status">{{ toastMessage }}</div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
/**
 * DownloadView — web replica of Android `DownloadsScene`:
 * ContentLayout (pull-to-refresh + empty tip + fast scroller) filled with
 * `item_download.xml` rows, a label filter strip (All + user labels), and the
 * scene's FabLayout cluster (start all / pause all / new label).
 *
 * Real-time updates: subscribes to `/topic/download/all` over the STOMP
 * WebSocket. The backend currently always publishes `speed = 0`
 * (DownloadService.publishProgress), so the transfer rate shown on each row
 * is derived client-side from progress deltas (pages/second) and used for
 * the ETA estimate.
 *
 * State model = `DownloadInfo.STATE_*`: 0 idle · 1 wait · 2 download ·
 * 3 finish · 4 failed (anotherviewer-web mirrors the Android constants).
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import type { StompSubscription } from '@stomp/stompjs'
import { useVirtualizer } from '@tanstack/vue-virtual'
import { downloadApi } from '@/api/download'
import type { DownloadItem, DownloadLabel, DownloadBatchTarget } from '@/api/download'
import { useWebSocket } from '@/composables/useWebSocket'
import type { DownloadProgress } from '@/composables/useWebSocket'
import type { FabAction } from '@/types/components'
import { loadDownloadListPrefs } from '@/utils/downloadListSettings'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import DownloadItemCard from '@/components/download/DownloadItem.vue'

/** View states matching ContentLayout's internal ViewTransition. */
type ViewState = 'loading' | 'content' | 'empty' | 'error'

/** Android `DownloadInfo.STATE_*` used for optimistic UI updates. */
const STATE_NONE = 0
const STATE_WAIT = 1
const STATE_DOWNLOAD = 2
const STATE_FINISH = 3
const STATE_FAILED = 4

/* ------------------------------------------------------------- list ----- */

/** 列表偏好（设备本地）：每页条数 + 排序模式，与 AdminDownload 同键共享。 */
const listPrefs = loadDownloadListPrefs()
const PAGE_SIZE = listPrefs.pageSize
const SORT_MODE = listPrefs.sortMode

/** Fixed row-height estimate for the virtualizer (single-column list). */
const ROW_ESTIMATE = 160

const downloads = ref<DownloadItem[]>([])
const labels = ref<DownloadLabel[]>([])
const activeLabel = ref<number | null>(null)
const state = ref<ViewState>('loading')
const refreshing = ref(false)
/** Total entries under the current label (from the server, page 1). */
const total = ref(0)
/** Guard against overlapping load-more requests. */
const loadingMore = ref(false)
const contentRef = ref<InstanceType<typeof ContentLayout> | null>(null)

interface LabelTab {
  id: number | null
  name: string
}

const tabs = computed<LabelTab[]>(() => [
  { id: null, name: 'All' },
  ...labels.value.map((label) => ({ id: label.id, name: label.label })),
])

/* ------------------------------------------------ virtual scrolling ----- */

const listHostRef = ref<HTMLElement | null>(null)

/**
 * The scroll container the virtualizer drives: ContentLayout's scroller
 * (FastScroller's container or the plain scroll div) — the same element the
 * pull-to-refresh / load-more listeners sit on, detected by walking up from
 * the list host (mirrors HomeView's approach).
 */
const scrollElRef = ref<HTMLElement | null>(null)

function findScroller(el: HTMLElement | null): HTMLElement | null {
  let node = el?.parentElement ?? null
  while (node) {
    const overflowY = getComputedStyle(node).overflowY
    if (
      /(auto|scroll|overlay)/.test(overflowY) ||
      node.classList.contains('fast-scroller__container') ||
      node.classList.contains('content-layout__plain-scroll')
    ) {
      return node
    }
    node = node.parentElement
  }
  return null
}

/** Re-detect the scroller whenever ContentLayout swaps in/out the list view. */
watch(
  state,
  async (s) => {
    if (s === 'content') {
      await nextTick()
      scrollElRef.value = findScroller(listHostRef.value)
    } else {
      scrollElRef.value = null
    }
  },
  { immediate: true },
)

const virtualizer = useVirtualizer(
  computed(() => ({
    count: downloads.value.length,
    getScrollElement: () => scrollElRef.value,
    estimateSize: () => ROW_ESTIMATE,
    overscan: 6,
    getItemKey: (index) => downloads.value[index]?.id ?? index,
  })),
)

/** Monotonic request guard — stale responses (fast label switches) drop. */
let requestSeq = 0

/* ---------------------------------- server-side search -------------------- */

/** 搜索词：防抖后作为 q 传给 /download/list（服务端过滤）。 */
const searchQuery = ref('')
const debouncedQuery = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

watch(searchQuery, (next) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (debouncedQuery.value !== next) {
      debouncedQuery.value = next
      // 搜索词变化 → 重置分页重新加载（负载在服务端）。
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
  const seq = ++requestSeq
  try {
    const result = await downloadApi.list(
      activeLabel.value ?? undefined,
      0,
      PAGE_SIZE,
      SORT_MODE,
      debouncedQuery.value || null,
    )
    if (seq !== requestSeq) return
    downloads.value = result.downloads
    labels.value = result.labels
    total.value = result.total
    state.value = result.downloads.length === 0 ? 'empty' : 'content'
    contentRef.value?.scrollToTop()
  } catch (error) {
    if (seq !== requestSeq) return
    console.error('Failed to load downloads', error)
    if (downloads.value.length === 0) {
      state.value = 'error'
    } else {
      showToast('Failed to refresh downloads')
    }
  }
}

/** Append the next page once the virtual window reaches the loaded tail. */
async function loadMore(): Promise<void> {
  if (loadingMore.value) return
  if (downloads.value.length >= total.value) return
  const seq = requestSeq
  loadingMore.value = true
  try {
    const result = await downloadApi.list(
      activeLabel.value ?? undefined,
      downloads.value.length,
      PAGE_SIZE,
      SORT_MODE,
      debouncedQuery.value || null,
    )
    if (seq !== requestSeq) return
    downloads.value.push(...result.downloads)
    total.value = result.total
  } catch (error) {
    console.error('Failed to load more downloads', error)
    // Retryable: the next scroll / virtualizer update re-triggers the load.
    showToast('Failed to load more downloads')
  } finally {
    loadingMore.value = false
  }
}

/** Fire when the virtual window's last row reaches the loaded tail. */
watch(
  () => virtualizer.value.range?.endIndex ?? -1,
  () => {
    const range = virtualizer.value.range
    if (!range || loadingMore.value) return
    if (downloads.value.length >= total.value) return
    if (range.endIndex >= downloads.value.length - 1) void loadMore()
  },
)

function selectTab(id: number | null, event: MouseEvent): void {
  if (id === activeLabel.value) return
  activeLabel.value = id
  const el = event.currentTarget
  if (el instanceof HTMLElement) {
    el.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
  }
  state.value = 'loading'
  void load()
}

async function onRefresh(): Promise<void> {
  await load()
  refreshing.value = false
}

function onRetry(): void {
  state.value = 'loading'
  void load()
}

/* -------------------------------------------------------- row actions --- */

/**
 * Runs a per-item API action with an optimistic state flip; on failure the
 * previous state is restored and a toast explains what happened.
 */
async function runItemAction(
  id: number,
  action: (id: number) => Promise<boolean>,
  nextState: number,
): Promise<void> {
  const item = downloads.value.find((entry) => entry.id === id)
  const previousState = item?.state
  if (item) item.state = nextState
  try {
    await action(id)
  } catch (error) {
    console.error('Download action failed', error)
    if (item && previousState !== undefined) item.state = previousState
    showToast('Operation failed')
  }
}

function onStart(id: number): void {
  void runItemAction(id, downloadApi.start, STATE_WAIT)
}

function onPause(id: number): void {
  void runItemAction(id, downloadApi.pause, STATE_NONE)
}

function onCancel(id: number): void {
  void runItemAction(id, downloadApi.cancel, STATE_NONE)
}

async function onDelete(id: number): Promise<void> {
  const index = downloads.value.findIndex((entry) => entry.id === id)
  if (index === -1) return
  const [removed] = downloads.value.splice(index, 1)
  if (total.value > 0) total.value -= 1
  if (downloads.value.length === 0) state.value = 'empty'
  try {
    await downloadApi.delete(id)
  } catch (error) {
    console.error('Failed to delete download', error)
    downloads.value.splice(index, 0, removed)
    state.value = 'content'
    showToast('Delete failed')
  }
}

/* ---------------------------------- multi-select (Android choice mode) ------ */

/** 多选模式：长按/右键条目进入（Android onItemLongClick → choice mode）。 */
const selectMode = ref(false)
const selectedIds = ref(new Set<number>())
const showMoveDialog = ref(false)
const batchBusy = ref(false)

/** 选中项中可"开始"的（idle/failed）。 */
const selectedStartableIds = computed(() =>
  downloads.value
    .filter((d) => selectedIds.value.has(d.id) && (d.state === STATE_NONE || d.state === STATE_FAILED))
    .map((d) => d.id),
)

/** 选中项中可"停止"的（wait/downloading）。 */
const selectedStoppableIds = computed(() =>
  downloads.value
    .filter((d) => selectedIds.value.has(d.id) && (d.state === STATE_WAIT || d.state === STATE_DOWNLOAD))
    .map((d) => d.id),
)

function onItemMenu(id: number): void {
  selectMode.value = true
  toggleSelect(id)
}

function onItemSelect(id: number): void {
  if (selectMode.value) toggleSelect(id)
}

function toggleSelect(id: number): void {
  const next = new Set(selectedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectedIds.value = next
}

function selectAll(): void {
  // 全选当前已加载页；若已加载页全选且仍有未加载条目（length < total），
  // 批量操作时以 all=true 让服务端按过滤条件处理全集（跨页全选）。
  const all = downloads.value.map((d) => d.id)
  selectedIds.value = new Set(all)
}

/** 跨页全选判定：已加载页全部选中且存在未加载条目 → 服务端 all 模式。 */
const selectAllAcrossPages = computed(
  () =>
    downloads.value.length > 0 &&
    selectedIds.value.size === downloads.value.length &&
    downloads.value.length < total.value,
)

/** 批量操作目标：跨页全选传 all + 当前过滤条件（label/q），否则传已选 ids。 */
function batchTarget(): DownloadBatchTarget {
  if (selectAllAcrossPages.value) {
    return {
      all: true,
      label: activeLabel.value,
      q: debouncedQuery.value || null,
    }
  }
  return { ids: Array.from(selectedIds.value) }
}

function exitSelectMode(): void {
  selectMode.value = false
  selectedIds.value = new Set()
  showMoveDialog.value = false
}

async function onBatchStart(): Promise<void> {
  const ids = selectedStartableIds.value
  if (ids.length === 0 || batchBusy.value) return
  batchBusy.value = true
  try {
    const target = batchTarget()
    const started = await downloadApi.startRange(target)
    for (const item of downloads.value) {
      if (selectedIds.value.has(item.id) && ids.includes(item.id)) item.state = STATE_WAIT
    }
    showToast(`Started ${started} downloads`)
  } catch (error) {
    console.error('Failed to start downloads', error)
    showToast('Failed to start downloads')
  } finally {
    batchBusy.value = false
    exitSelectMode()
  }
}

async function onBatchStop(): Promise<void> {
  const ids = selectedStoppableIds.value
  if (ids.length === 0 || batchBusy.value) return
  batchBusy.value = true
  try {
    const stopped = await downloadApi.stopRange(batchTarget())
    for (const item of downloads.value) {
      if (selectedIds.value.has(item.id) && ids.includes(item.id)) item.state = STATE_NONE
    }
    showToast(`Stopped ${stopped} downloads`)
  } catch (error) {
    console.error('Failed to stop downloads', error)
    showToast('Failed to stop downloads')
    await load()
  } finally {
    batchBusy.value = false
    exitSelectMode()
  }
}

async function onBatchDelete(): Promise<void> {
  if (selectedIds.value.size === 0 || batchBusy.value) return
  const target = batchTarget()
  const count = selectAllAcrossPages.value ? total.value : selectedIds.value.size
  // Android DeleteRangeDialogHelper：删除会同时移除下载文件（WebUI 删除语义一致）。
  if (!window.confirm(`删除选中的 ${count} 项下载？下载文件将一并删除。`)) return
  batchBusy.value = true
  try {
    const removed = await downloadApi.deleteRange(target)
    downloads.value = downloads.value.filter((d) => !selectedIds.value.has(d.id))
    total.value = Math.max(0, total.value - removed)
    if (downloads.value.length === 0) state.value = 'empty'
    showToast(`Deleted ${removed} downloads`)
  } catch (error) {
    console.error('Failed to delete downloads', error)
    showToast('Delete failed')
  } finally {
    batchBusy.value = false
    exitSelectMode()
  }
}

function onBatchMove(): void {
  if (selectedIds.value.size === 0) return
  showMoveDialog.value = true
}

function closeMoveDialog(): void {
  showMoveDialog.value = false
}

async function confirmMove(labelId: number): Promise<void> {
  closeMoveDialog()
  if (selectedIds.value.size === 0 || batchBusy.value) return
  batchBusy.value = true
  try {
    const moved = await downloadApi.move({ ...batchTarget(), labelId })
    for (const item of downloads.value) {
      if (selectedIds.value.has(item.id)) item.label = labelId
    }
    showToast(`Moved ${moved} downloads`)
  } catch (error) {
    console.error('Failed to move downloads', error)
    showToast('Failed to move downloads')
  } finally {
    batchBusy.value = false
    exitSelectMode()
  }
}

/* -------------------------------------------------------------- FAB ----- */

const fabExpanded = ref(false)

const fabActions: FabAction[] = [
  { id: 'start-all', icon: 'play-dark', label: 'Start all' },
  { id: 'pause-all', icon: 'pause-dark', label: 'Pause all' },
  { id: 'new-label', icon: 'folder-add-dark', label: 'New label' },
]

function onFabAction(action: FabAction): void {
  fabExpanded.value = false
  if (action.id === 'start-all') void startAll()
  else if (action.id === 'pause-all') void pauseAll()
  else if (action.id === 'new-label') openLabelDialog()
}

async function startAll(): Promise<void> {
  try {
    await downloadApi.startAll()
    for (const item of downloads.value) {
      if (item.state === STATE_NONE || item.state === STATE_FAILED) {
        item.state = STATE_WAIT
      }
    }
    showToast('All downloads started')
  } catch (error) {
    console.error('Failed to start all downloads', error)
    showToast('Failed to start downloads')
  }
}

/** The API exposes no pause-all endpoint — fan out over the active rows. */
async function pauseAll(): Promise<void> {
  const active = downloads.value.filter(
    (item) => item.state === STATE_WAIT || item.state === STATE_DOWNLOAD,
  )
  if (active.length === 0) {
    showToast('No active downloads')
    return
  }
  try {
    await Promise.all(active.map((item) => downloadApi.pause(item.id)))
    for (const item of active) item.state = STATE_NONE
    showToast('Downloads paused')
  } catch (error) {
    console.error('Failed to pause downloads', error)
    showToast('Failed to pause downloads')
    await load()
  }
}

/* ---------------------------------------------------- new label dialog -- */

const showLabelDialog = ref(false)
const labelName = ref('')
const labelSaving = ref(false)
const labelError = ref('')
const labelInputRef = ref<HTMLInputElement | null>(null)

function openLabelDialog(): void {
  labelName.value = ''
  labelError.value = ''
  showLabelDialog.value = true
}

function closeLabelDialog(): void {
  showLabelDialog.value = false
}

watch(showLabelDialog, async (open) => {
  if (open) {
    await nextTick()
    labelInputRef.value?.focus()
  }
})

async function createLabel(): Promise<void> {
  const name = labelName.value.trim()
  if (!name || labelSaving.value) return
  labelSaving.value = true
  labelError.value = ''
  try {
    await downloadApi.createLabel(name)
    showLabelDialog.value = false
    showToast(`Label “${name}” created`)
    await load()
  } catch (error) {
    console.error('Failed to create label', error)
    labelError.value = 'Failed to create label'
  } finally {
    labelSaving.value = false
  }
}

/* ------------------------------------------------------------- toast ---- */

const toastMessage = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined

function showToast(message: string): void {
  toastMessage.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
  }, 2400)
}

/* ------------------------------------------------- WebSocket progress --- */

const { connected, connect, subscribeAll } = useWebSocket()

/** gid → live transfer rate in pages/second, fed to each row. */
const liveSpeeds = ref<Record<number, number>>({})

/** gid → last progress sample, used to derive speed from deltas. */
const speedSamples = new Map<number, { done: number; time: number }>()

let progressSubscription: StompSubscription | null = null

function handleProgress(progress: DownloadProgress): void {
  const item = downloads.value.find((entry) => entry.gid === progress.gid)
  if (item) {
    item.state = progress.state
    item.done = progress.downloaded
    if (progress.total > 0) item.total = progress.total
    item.label = progress.label
  }

  const now = Date.now()
  const previous = speedSamples.get(progress.gid)
  let speed = progress.speed
  if (speed <= 0 && previous && now > previous.time && progress.downloaded > previous.done) {
    speed = ((progress.downloaded - previous.done) * 1000) / (now - previous.time)
  }

  if (progress.state === STATE_FINISH || progress.state === STATE_FAILED) {
    speedSamples.delete(progress.gid)
    liveSpeeds.value[progress.gid] = 0
  } else {
    speedSamples.set(progress.gid, { done: progress.downloaded, time: now })
    liveSpeeds.value[progress.gid] = speed
  }
}

/* Subscribe once the STOMP session is up (subscribeAll is a no-op before). */
watch(
  connected,
  (up) => {
    if (up && !progressSubscription) {
      progressSubscription = subscribeAll(handleProgress) ?? null
    }
  },
  { immediate: true },
)

onMounted(() => {
  connect()
  void load()
})

onUnmounted(() => {
  clearTimeout(toastTimer)
})
</script>

<style scoped>
.download-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  /* Standalone PWA: push the header row + label tabs below the status bar /
     cutout. border-box keeps the column at 100dvh — the flex:1
     ContentLayout shrinks instead of overflowing. The list bottom already
     clears the home indicator via --gallery-padding-bottom-fab. */
  padding-top: var(--safe-area-top);
  background: var(--color-bg);
}

.download-view__content {
  flex: 1;
  min-height: 0;
}

/* -------------------------------------------------------- label tabs --- */
.label-tabs {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  overflow-x: auto;
  padding: 0 max(var(--gallery-list-margin-h), 4px);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
  scrollbar-width: none;
}

.label-tabs::-webkit-scrollbar {
  display: none;
}

.label-tabs__tab {
  position: relative;
  flex: 0 0 auto;
  padding: 12px 16px;
  border: none;
  background: transparent;
  color: var(--text-color-secondary);
  font-family: inherit;
  font-size: var(--text-small); /* 14sp */
  font-weight: 500;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: color 160ms var(--ease-decelerate-quart);
}

.label-tabs__tab::after {
  content: '';
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 0;
  height: 2px;
  border-radius: 1px;
  background: var(--color-primary);
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 200ms var(--ease-decelerate-quart);
}

.label-tabs__tab:hover {
  color: var(--text-color-primary);
}

.label-tabs__tab--active {
  color: var(--text-color-primary);
}

.label-tabs__tab--active::after {
  transform: scaleX(1);
}

.label-tabs__tab:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* -------------------------------------------------- server-side search ---- */
.search-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  margin: 0 var(--keyline-margin) 8px;
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

/* -------------------------------------------------- multi-select bar ---- */
.select-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  padding: 8px var(--keyline-margin);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
  animation: select-in 160ms var(--ease-decelerate-quart);
}

@keyframes select-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}

.select-bar__count {
  flex: 0 0 auto;
  font-size: var(--text-super-small);
  font-weight: 700;
  color: var(--color-primary);
}

.select-bar__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;
}

.select-bar__actions::-webkit-scrollbar {
  display: none;
}

.select-bar__btn {
  flex: 0 0 auto;
  padding: 6px 12px;
  border: 1px solid var(--color-divider);
  border-radius: 999px;
  background: transparent;
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
  transition: background-color 140ms var(--ease-decelerate-quart);
}

.select-bar__btn:hover:not(:disabled) {
  background: var(--color-surface-activated);
}

.select-bar__btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.select-bar__btn--danger {
  border-color: color-mix(in srgb, var(--color-danger, #e5484d) 55%, transparent);
  color: var(--color-danger, #e5484d);
}

.select-bar__close {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-color-secondary);
  cursor: pointer;
}

.select-bar__close:hover {
  background: var(--color-surface-activated);
}

/* -------------------------------------------------------------- list ---- */
/* Single-column virtualized list: the ul carries the total scroll height and
   each row is absolutely positioned at its virtual offset (translateY), so
   only the visible window is ever mounted. Horizontal padding is re-applied
   per row because absolutely positioned children ignore the ul's padding. */
.download-list {
  position: relative;
  list-style: none;
  margin: 0;
  padding: var(--gallery-list-margin-v) var(--gallery-list-margin-h)
    var(--gallery-padding-bottom-fab);
}

.download-list__item {
  position: absolute;
  top: 0;
  left: var(--gallery-list-margin-h);
  right: var(--gallery-list-margin-h);
  /* `backwards` (not `both`): natural state opacity 1, never stuck invisible
     when WebKit fails to run the staggered entrance (T-1 regression). */
  opacity: 1;
  animation: item-in 240ms var(--ease-decelerate-quart) backwards;
}

@keyframes item-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
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

.dialog__input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: var(--color-bg);
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-little-small); /* 16sp */
  transition: border-color 140ms var(--ease-decelerate-quart);
}

.dialog__input::placeholder {
  color: var(--text-color-secondary);
}

.dialog__input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.dialog__error {
  margin: 6px 0 0;
  font-size: var(--text-super-small);
  color: var(--color-red-500);
}

/* Move-to-label options (Android MoveDialogHelper list replica) */
.dialog__label-list {
  list-style: none;
  margin: 0 0 var(--keyline-margin);
  padding: 0;
}

.dialog__label-option {
  display: block;
  width: 100%;
  padding: 10px 8px;
  border: none;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-small);
  text-align: left;
  cursor: pointer;
}

.dialog__label-option:hover {
  background: var(--color-surface-activated);
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

.dialog__btn:disabled {
  color: var(--text-color-secondary);
  opacity: 0.5;
  cursor: default;
}

.dialog__btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

/* -------------------------------------------------------------- toast --- */
.toast {
  position: fixed;
  left: 50%;
  /* The FAB cluster is offset by --safe-area-bottom (FabLayout) — carry the
     same inset so the toast keeps its distance above the FABs / home
     indicator on cutout devices. */
  bottom: calc(96px + var(--safe-area-bottom));
  transform: translateX(-50%);
  z-index: 300;
  padding: 10px 20px;
  background: var(--grey-850);
  color: var(--grey-100);
  border-radius: var(--card-radius);
  font-size: var(--text-small);
  box-shadow: 0 3px 10px var(--shadow-color);
  opacity: 1;
  animation: toast-in 220ms var(--ease-decelerate-quint);
}

@keyframes toast-in {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(10px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .download-list__item,
  .dialog-scrim,
  .dialog,
  .toast {
    animation: none;
  }
}
</style>
