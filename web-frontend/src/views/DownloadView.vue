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
         防抖搜索，q 走 /download/list 服务端过滤，负载留在服务器。与筛选槽位
         互斥：选槽位清空搜索词、输入搜索取消槽位（useFilterSlots）。 -->
    <div class="search-bar">
      <AppIcon name="magnify-dark" size="18px" />
      <input
        v-model="searchQuery"
        class="search-bar__input"
        type="search"
        :placeholder="activeSlot ? `筛选：${activeSlot.name}` : '搜索标题…'"
        aria-label="搜索下载"
        @compositionstart="searchComposing = true"
        @compositionend="onSearchCompositionEnd"
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

    <!-- Filter slot bar (A5d): 命名正则预设；点击槽位 → q=pattern&regex=true，
         与上方搜索框互斥。 -->
    <FilterSlotBar :slots="slots" :active-id="activeSlotId" @select="onSlotBarSelect" />

    <!-- 分页条（Android PaginationIndicator 对齐，plan-2026-08-30 §3.4.0.1）：
         页码指示 + 每页条数切换（localStorage 与 AdminDownload 同键）+ 跳页
         （服务端 offset 直取并替换列表）。无限加载体验保留。
         PC 适配（2026-08-30）：直点页码按钮组（滚动窗口 + 省略号折叠）+ 前后页，
         不再只有输入框。 -->
    <nav
      v-if="paginationVisible"
      class="pagination-bar"
      data-testid="download-pagination"
      aria-label="下载分页"
    >
      <span class="pagination-bar__info">
        第 {{ currentPage }} / {{ totalPages }} 页 · {{ total }} 条
      </span>
      <span class="pagination-bar__pages" role="group" aria-label="页码">
        <button
          type="button"
          class="pagination-bar__page"
          :disabled="currentPage <= 1"
          aria-label="上一页"
          @click="jumpToPage(currentPage - 1)"
        >
          ‹
        </button>
        <template v-for="(item, i) in pageWindow" :key="`${item}-${i}`">
          <button
            v-if="item !== '…'"
            type="button"
            class="pagination-bar__page"
            :class="{ 'pagination-bar__page--active': item === currentPage }"
            :aria-current="item === currentPage ? 'page' : undefined"
            :aria-label="`第 ${item} 页`"
            @click="jumpToPage(item)"
          >
            {{ item }}
          </button>
          <span v-else class="pagination-bar__ellipsis" aria-hidden="true">…</span>
        </template>
        <button
          type="button"
          class="pagination-bar__page"
          :disabled="currentPage >= totalPages"
          aria-label="下一页"
          @click="jumpToPage(currentPage + 1)"
        >
          ›
        </button>
      </span>
      <label class="pagination-bar__size">
        条/页
        <select
          v-model.number="pageSize"
          class="pagination-bar__select"
          aria-label="每页条数"
        >
          <option v-for="size in DOWNLOAD_PAGE_SIZES" :key="size" :value="size">
            {{ size }}
          </option>
        </select>
      </label>
      <span class="pagination-bar__jump">
        <input
          v-model.number="jumpInput"
          class="pagination-bar__input"
          type="number"
          min="1"
          :max="totalPages"
          :aria-label="`跳页（1 至 ${totalPages}）`"
          @keyup.enter="jumpToPage()"
          placeholder="页"
        />
        <button type="button" class="pagination-bar__btn" @click="jumpToPage()">
          跳页
        </button>
      </span>
    </nav>

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
      :has-more="downloads.length < total"
      v-model:refreshing="refreshing"
      empty-text="No downloads"
      :error-text="errorText"
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
            @open="onItemOpen"
            @read="onItemRead"
          />
        </li>
      </ul>
    </ContentLayout>

    <!-- PC 常驻批量工具条（2026-08-30）：pointer:fine + ≥720px 时替代 FAB 集群
         ——全部开始 / 全部下载（无视状态重下，磁盘校验通过跳过）/ 全部暂停 /
         新建标签 直接可见；触摸/窄屏仍用右下 FAB。 -->
    <div v-if="pcInput && !selectMode" class="pc-batch-bar" role="toolbar" aria-label="批量操作">
      <button type="button" class="pc-batch-bar__btn" @click="startAll()">
        <AppIcon name="play-dark" size="14px" />全部开始
      </button>
      <button type="button" class="pc-batch-bar__btn" @click="restartAll()">
        <AppIcon name="refresh-dark" size="14px" />全部下载
      </button>
      <button type="button" class="pc-batch-bar__btn" @click="pauseAll()">
        <AppIcon name="pause-dark" size="14px" />全部暂停
      </button>
      <button type="button" class="pc-batch-bar__btn" @click="openLabelDialog()">
        <AppIcon name="folder-add-dark" size="14px" />新建标签
      </button>
    </div>

    <!-- FabLayout replica: primary 56dp FAB + mini FABs
         (scene_download.xml: play / pause / … cluster); hidden in select mode
         (Android choice mode replaces the FAB cluster with the action bar). -->
    <FabLayout
      v-if="!selectMode && !pcInput"
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

<script lang="ts">
/**
 * 下载条目的路由构建（独立于组件生命周期，可被测试直接调用）。
 * P-A/P-B（plan-2026-08-30 §3.4.0）：本地 token 透传——详情/阅读器入口携
 * token 让服务端优先走本地行 / 上游直取，无本地背书时不再必然失败。
 */
export interface DownloadRouteTarget {
  gid: number
  token?: string | null
}

/** 缩略图 → 详情（`/gallery/:gid?token=`）。 */
export function buildDetailRoute(target: DownloadRouteTarget): {
  path: string
  query: Record<string, string>
} {
  return { path: `/gallery/${target.gid}`, query: target.token ? { token: target.token } : {} }
}

/** 主体 → 直接阅读（`/reader/:gid?token=`）。 */
export function buildReaderRoute(target: DownloadRouteTarget): {
  path: string
  query: Record<string, string>
} {
  return { path: `/reader/${target.gid}`, query: target.token ? { token: target.token } : {} }
}
</script>

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
import { useRouter } from 'vue-router'
import { useVirtualizer } from '@tanstack/vue-virtual'
import { downloadApi } from '@/api/download'
import type { DownloadItem, DownloadLabel, DownloadBatchTarget } from '@/api/download'
import { useWebSocket } from '@/composables/useWebSocket'
import type { DownloadProgress } from '@/composables/useWebSocket'
import { useFilterSlots } from '@/composables/useFilterSlots'
import { usePcInput } from '@/composables/usePcInput'
import FilterSlotBar from '@/components/FilterSlotBar.vue'
import type { FabAction } from '@/types/components'
import {
  DOWNLOAD_PAGE_SIZES,
  isDownloadPageSize,
  loadDownloadListPrefs,
  saveDownloadListPrefs,
} from '@/utils/downloadListSettings'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import DownloadItemCard from '@/components/download/DownloadItem.vue'

/** View states matching ContentLayout's internal ViewTransition. */
type ViewState = 'loading' | 'content' | 'empty' | 'error'

/** Android `DownloadInfo.STATE_*` used for optimistic UI updates. */
const router = useRouter()

const STATE_NONE = 0
const STATE_WAIT = 1
const STATE_DOWNLOAD = 2
const STATE_FINISH = 3
const STATE_FAILED = 4

/* ------------------------------------------------------------- list ----- */

/** 列表偏好（设备本地）：每页条数 + 排序模式，与 AdminDownload 同键共享。
 *  每页条数可在下方分页条切换（即时保存回 localStorage）。 */
const listPrefs = loadDownloadListPrefs()
const pageSize = ref(listPrefs.pageSize)
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
/** F4 REGEX_INVALID: the error tip switches to a dedicated regex message. */
const errorText = ref('Failed to load downloads')

/* ---------------------------------------------------- pagination bar ---- */

/** 当前页码（1 起，跟随加载位置；无限加载用当前语义，跳页用 offset 语义）。 */
const currentPage = ref(1)
/** 跳页输入。 */
const jumpInput = ref<number | null>(1)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
/**
 * 分页条可见性：total > pageSize 才显示。对齐 Android 仅当可见条数 ≥
 * paginationSize(500) 时显示指示器——WebUI 是无限加载，「可见条数」恒等于已
 * 加载数，因此用更合理的 total ≤ pageSize 同义判定（还有更多页才需要定位）。
 */
const paginationVisible = computed(() => total.value > pageSize.value)

/**
 * 跳页：服务端 offset 直取并替换列表（offset = (k-1)*pageSize），列表顶部
 * 重置为所跳页面（虚拟滚动由 virtualizer + scrollToTop 滚回顶部）。
 */
function jumpToPage(force?: number): void {
  const target = Math.min(
    Math.max(Math.floor(force ?? jumpInput.value ?? currentPage.value), 1),
    totalPages.value,
  )
  if (!Number.isFinite(target) || target < 1) return
  if (target === currentPage.value) return
  jumpInput.value = target
  state.value = 'loading'
  void load((target - 1) * pageSize.value)
}

/**
 * PC 页码窗口（2026-08-30）：总页数 ≤7 全量；否则首页/末页夹在窗口两端，
 * 窗口内 ±1 邻页 + 折叠省略号（每侧至多 7 字节），当前页永遠可见。
 * 返回示例（page=6/total=30）：[1,'…',5,6,7,'…',30]。
 */
const pageWindow = computed<(number | '…')[]>(() => {
  const total = totalPages.value
  const current = currentPage.value
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const windowSize = 5 // 当前 ±2（含本身）
  const start = Math.max(2, Math.min(current - 2, total - windowSize))
  const end = start + windowSize - 1
  const items: (number | '…')[] = []
  items.push(1)
  if (start > 2) items.push('…')
  for (let p = start; p <= end; p++) items.push(p)
  if (end < total - 1) items.push('…')
  items.push(total)
  return items
})

/** PC 键盘：PageUp/PageDown 上一页/下一页（列表失焦时仍生效，全页级）。 */
function onPageKey(e: KeyboardEvent): void {
  const target = e.target as HTMLElement | null
  if (target && (target.tagName === 'INPUT' || target.tagName === 'SELECT' || target.tagName === 'TEXTAREA')) return
  if (e.key === 'PageDown') {
    e.preventDefault()
    if (currentPage.value < totalPages.value) jumpToPage(currentPage.value + 1)
  } else if (e.key === 'PageUp') {
    e.preventDefault()
    if (currentPage.value > 1) jumpToPage(currentPage.value - 1)
  }
}
onMounted(() => window.addEventListener('keydown', onPageKey))
onUnmounted(() => window.removeEventListener('keydown', onPageKey))

/** 每页条数切换（分页条下拉）→ 即时保存本地偏好 + 重置回第 1 页加载。 */
watch(pageSize, (next) => {
  if (!isDownloadPageSize(next)) return
  saveDownloadListPrefs({ sortMode: SORT_MODE, pageSize: next })
  jumpInput.value = 1
  state.value = 'loading'
  void load(0)
})

/**
 * F4: extracts the business error code from the API error envelope
 * (`{error:{code,message,traceId,status}}` carried by axios as
 * `error.response.data`); null for any other failure shape.
 */
function errorCodeOf(error: unknown): string | null {
  const code = (error as { response?: { data?: { error?: { code?: unknown } } } } | undefined)
    ?.response?.data?.error?.code
  return typeof code === 'string' ? code : null
}

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

/* ---------------------------------- server-side search + filter slots ----- */

/** 搜索词：防抖后作为 q 传给 /download/list（服务端过滤）。 */
const searchQuery = ref('')
const debouncedQuery = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

/** 筛选槽位（A5d）：命名正则预设；与搜索框互斥（useFilterSlots 保证）——
 *  选槽位清空 searchQuery、输入搜索取消槽位。 */
const { slots, activeSlotId, activeSlot, selectSlot } = useFilterSlots(searchQuery)

function onSlotBarSelect(id: string | null): void {
  selectSlot(id)
  // 槽位点击总是重新加载（清空搜索词不一定触发防抖 watch——搜索词本来就空时）。
  state.value = 'loading'
  void load()
}

/** 当前筛选条件：槽位激活 → (q=pattern, regex=true)；否则 → 搜索词（LIKE）。 */
function currentFilter(): { q: string | null; regex: boolean } {
  const slot = activeSlot.value
  if (slot) return { q: slot.pattern, regex: true }
  return { q: debouncedQuery.value || null, regex: false }
}

watch(searchQuery, scheduleSearchCommit)

/* IME 组合输入保护（plan-2026-09-05 C1）：拼音组合期间的 input 事件携带
   中间态字母——组合置位时防抖回调直接丢弃，compositionend 后由最终选词的
   input 事件重新走防抖提交。 */
const searchComposing = ref(false)

function onSearchCompositionEnd(): void {
  searchComposing.value = false
  scheduleSearchCommit()
}

/** 防抖提交搜索词（watch 与 compositionend 共用同一时钟）。 */
function scheduleSearchCommit(): void {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (searchComposing.value) return
    // 搜索词变化 → 重置分页重新加载（负载在服务端）。
    const next = searchQuery.value
    if (debouncedQuery.value !== next) {
      debouncedQuery.value = next
      // 槽位激活时该变更来自 selectSlot 清空搜索词——加载已由 onSlotBarSelect
      // 触发（也避免与槽位过滤重复请求）。
      if (activeSlot.value) return
      state.value = 'loading'
      void load()
    }
  }, 400)
}

function clearSearch(): void {
  searchQuery.value = ''
  debouncedQuery.value = ''
  state.value = 'loading'
  void load()
}

/**
 * 加载（替换模式）：`offset` 为服务端分页偏移（跳页时 (n-1)*pageSize，
 * 其余入口保持 0）。成功后当前页码跟随加载位置（offset 语义）。
 */
async function load(offset = 0): Promise<void> {
  const seq = ++requestSeq
  try {
    const filter = currentFilter()
    const result = await downloadApi.list(
      activeLabel.value ?? undefined,
      offset,
      pageSize.value,
      SORT_MODE,
      filter.q,
      filter.regex,
    )
    if (seq !== requestSeq) return
    downloads.value = result.downloads
    labels.value = result.labels
    total.value = result.total
    currentPage.value = Math.min(Math.floor(offset / pageSize.value) + 1, totalPages.value)
    state.value = result.downloads.length === 0 ? 'empty' : 'content'
    contentRef.value?.scrollToTop()
  } catch (error) {
    if (seq !== requestSeq) return
    console.error('Failed to load downloads', error)
    // F4: invalid regex in q → 400 REGEX_INVALID; name the cause instead of
    // the generic "failed to load" tip (dedicated toast + error-state copy).
    if (errorCodeOf(error) === 'REGEX_INVALID') {
      errorText.value = '正则无效，请检查搜索/筛选的正则表达式'
      showToast('正则无效，请检查筛选表达式')
    } else {
      errorText.value = 'Failed to load downloads'
    }
    if (downloads.value.length === 0) {
      state.value = 'error'
    } else if (errorCodeOf(error) !== 'REGEX_INVALID') {
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
    const filter = currentFilter()
    const result = await downloadApi.list(
      activeLabel.value ?? undefined,
      downloads.value.length,
      pageSize.value,
      SORT_MODE,
      filter.q,
      filter.regex,
    )
    if (seq !== requestSeq) return
    downloads.value.push(...result.downloads)
    total.value = result.total
    // 无限加载：当前页码跟随已加载位置的下一页（封顶到最后一页）。
    currentPage.value = Math.min(
      Math.floor(Math.max(downloads.value.length - 1, 0) / pageSize.value) + 1,
      totalPages.value,
    )
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

/** Android 端逻辑：缩略图 → 详情页；主体 → 直接阅读。
 *  P-A/P-B（plan-2026-08-30 §3.4.0）：本地 token 透传。 */
function onItemOpen(gid: number): void {
  const item = downloads.value.find((entry) => entry.gid === gid)
  void router.push(item ? buildDetailRoute(item) : `/gallery/${gid}`)
}

function onItemRead(gid: number): void {
  const item = downloads.value.find((entry) => entry.gid === gid)
  void router.push(item ? buildReaderRoute(item) : `/reader/${gid}`)
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

/** 批量操作目标：跨页全选传 all + 当前过滤条件（label/q/regex），否则传已选 ids。
 *  槽位激活 → 正则全集；搜索词 → LIKE 全集；都空 → 仅 label。 */
function batchTarget(): DownloadBatchTarget {
  if (selectAllAcrossPages.value) {
    const slot = activeSlot.value
    if (slot) return { all: true, label: activeLabel.value, q: slot.pattern, regex: true }
    const q = debouncedQuery.value
    if (q) return { all: true, label: activeLabel.value, q, regex: false }
    return { all: true, label: activeLabel.value }
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
const { pcInput } = usePcInput()

const fabActions: FabAction[] = [
  { id: 'start-all', icon: 'play-dark', label: 'Start all' },
  { id: 'restart-all', icon: 'refresh-dark', label: 'Restart all' },
  { id: 'pause-all', icon: 'pause-dark', label: 'Pause all' },
  { id: 'new-label', icon: 'folder-add-dark', label: 'New label' },
]

function onFabAction(action: FabAction): void {
  fabExpanded.value = false
  if (action.id === 'start-all') void startAll()
  else if (action.id === 'restart-all') void restartAll()
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

/** 「全部下载」：无视现有状态重新开始；磁盘有文件且校验通过的行服务端跳过。 */
async function restartAll(): Promise<void> {
  try {
    await downloadApi.restartAll()
    for (const item of downloads.value) {
      if (item.state === STATE_FINISH) continue
      item.state = STATE_WAIT
      item.error = null
    }
    showToast('All downloads restarted')
  } catch (error) {
    console.error('Failed to restart all downloads', error)
    showToast('Failed to restart downloads')
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

/** Esc 统一挂 window（C7）：焦点不在面板内（刚打开未聚焦）时也能关闭。 */
function onDialogKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape' || event.isComposing) return
  if (showLabelDialog.value) closeLabelDialog()
  else if (showMoveDialog.value) closeMoveDialog()
}

watch([showLabelDialog, showMoveDialog], ([labelOpen, moveOpen]) => {
  if (labelOpen || moveOpen) window.addEventListener('keydown', onDialogKeydown)
  else window.removeEventListener('keydown', onDialogKeydown)
})

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

/** True once the all-downloads subscription is registered (survives
 *  reconnects via the composable's registry — no handle to keep). */
let progressSubscribed = false

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
    if (up && !progressSubscribed) {
      progressSubscribed = !!subscribeAll(handleProgress)
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
  window.removeEventListener('keydown', onDialogKeydown)
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

/* ----------------------------------------------------- pagination bar ---- */
/* 简洁一行条（label-tabs 样式语言）：页码 / 每页条数 / 跳页。 */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing);
  flex-shrink: 0;
  padding: 6px max(var(--gallery-list-margin-h), 4px);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
  font-size: var(--text-super-small); /* 12sp */
  color: var(--text-color-secondary);
}

.pagination-bar__info {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* PC 页码窗口（2026-08-30）：直点页码 + 省略号折叠 + 前后页。 */
.pagination-bar__pages {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex: 0 1 auto;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.pagination-bar__pages::-webkit-scrollbar {
  display: none;
}

.pagination-bar__page {
  min-width: 26px;
  padding: 2px 5px;
  border: 1px solid transparent;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--color-primary);
  font-family: inherit;
  font-size: var(--text-super-small);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: background-color 140ms var(--ease-decelerate-quart);
}

.pagination-bar__page:hover:not(:disabled) {
  background: var(--color-surface-activated);
}

.pagination-bar__page:disabled {
  color: var(--text-color-disabled, #9e9e9e);
  cursor: default;
}

.pagination-bar__page--active {
  background: var(--color-primary);
  color: var(--color-primary-inverse, #fff);
  border-color: var(--color-primary);
}

.pagination-bar__page--active:hover {
  background: var(--color-primary);
}

.pagination-bar__ellipsis {
  min-width: 18px;
  text-align: center;
  color: var(--text-color-secondary);
  user-select: none;
}

.pagination-bar__size,
.pagination-bar__jump {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  white-space: nowrap;
}

.pagination-bar__select,
.pagination-bar__input {
  padding: 2px 6px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: var(--color-surface);
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-super-small);
}

.pagination-bar__input {
  width: 52px;
  -moz-appearance: textfield;
  appearance: textfield;
}

.pagination-bar__input::-webkit-outer-spin-button,
.pagination-bar__input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.pagination-bar__select:focus,
.pagination-bar__input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.pagination-bar__btn {
  padding: 2px 8px;
  border: none;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--color-primary);
  font-family: inherit;
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
  transition: background-color 140ms var(--ease-decelerate-quart);
}

.pagination-bar__btn:hover {
  background: var(--color-surface-activated);
}

/* -------------------------------------------------- PC batch bar ---- */
.pc-batch-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 6px max(var(--gallery-list-margin-h), 4px);
  border-bottom: 1px solid var(--color-divider);
  background: var(--color-bg);
}

.pc-batch-bar__btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: 1px solid var(--color-divider);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-primary);
  font-family: inherit;
  font-size: var(--text-small);
  font-weight: 500;
  cursor: pointer;
  transition:
    background-color 140ms var(--ease-decelerate-quart),
    border-color 140ms var(--ease-decelerate-quart);
}

.pc-batch-bar__btn:hover {
  background: var(--color-surface-activated);
  border-color: var(--color-primary);
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

/* 键盘焦点可见（C7）：pill 容器内的输入框用内嵌 outline。 */
.search-bar__input:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* 触控目标加大（B5/C 附加项）：24px 图标钮 → 32px 命中区 + padding。 */
.search-bar__clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 4px;
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
