<template>
  <div class="history-view">
    <!-- 平板对齐（T3，2026-09-20 定案）：宽屏（≥960px）双栏——历史列表恒驻
         左栏（360px 固定宽）+ 详情右栏（GalleryDetailPane 原位加载，不跳
         路由）；无选中渲染占位符（不自动选中）。窄屏 (<960px) 单列行为与
         现状完全一致（点击进整页详情）。返回语义（宽屏有选中）：Esc /
         浏览器返回先清选中（拦截在 TwoPaneLayout），再按一次才离开页面。 -->
    <TwoPaneLayout
      :has-selection="selectedGid !== null"
      placeholder="从左侧选择一个画廊查看详情"
      @clear-selection="clearSelection"
    >
      <template #list="{ wide }">
        <div class="history-view__heading">
          <h1 class="history-view__title">History</h1>
          <span v-if="state === 'content'" class="history-view__count">
            {{ countLabel }}
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

        <FilterSlotBar :slots="slots" :active-id="activeSlotId" @select="onSlotBarSelect" />

        <!-- 分页条（A4 定案：历史与下载页同构，2026-09-06）：页码指示 + 每页条数
             切换（50/100/200，默认 50，服务端 pageSize 钳制 1..200）+ 跳页。
             total ≤ pageSize 时隐藏（Android PaginationIndicator 语义）。 -->
        <nav
          v-if="paginationVisible"
          class="pagination-bar"
          data-testid="history-pagination"
          aria-label="历史分页"
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
              <option v-for="size in HISTORY_PAGE_SIZES" :key="size" :value="size">
                {{ size }}
              </option>
            </select>
          </label>
          <!-- 用户定案（2026-09-07）：跳页输入仅下载页保留。条数切换/页码窗口保留。 -->
        </nav>

        <ContentLayout
          ref="contentRef"
          class="history-view__content"
          :state="state"
          v-model:refreshing="refreshing"
          empty-text="No history"
          :error-text="errorText"
          @refresh="onRefresh"
          @retry="onRetry"
        >
          <!-- A4 定案（W3-F3）：历史与下载页同构的全宽单列密信息行——共享
               AppListRow（缩略图→详情 / 主体→统一阅读器 点击分区 + 角标挂点）。
               服务端分页（W2-B2 DB 分页）：usePagedList 把 page/pageSize 直传给
               /history/list（page 0 起），整页替换渲染。KeepAlive 页码还原语义 =
               页码（currentPage 随组件实例存续）+ 页内滚动（滚动容器 DOM 随
               KeepAlive 保留 scrollTop），返回即还原，无需额外逻辑。
               宽屏双栏下两个点击分区都改为选中右栏（T3）。 -->
          <div class="history-list">
            <AppListRow
              v-for="row in rows"
              :key="row.item.gid"
              :id="row.item.gid"
              :gid="row.item.gid"
              :title="displayTitle(row.item)"
              :subtitle="displaySubtitle(row.item)"
              :thumb="row.item.thumb"
              @open="(gid: number) => openDetail(gid, wide)"
              @read="(gid: number) => openDetail(gid, wide)"
            >
              <!-- Last-viewed stamp — clock glyph + compact date/time, secondary
                   ink; absolutely positioned corner badge anchored to the row
                   (AppListRow badge mount, the list-form .time-badge from before). -->
              <template #badge>
                <span
                  class="time-badge"
                  :title="`Last viewed ${new Date(row.item.time).toLocaleString()}`"
                >
                  <AppIcon name="history-black" size="14px" />
                  {{ formatViewTime(row.item.time) }}
                </span>
              </template>

              <!-- 元信息行：CategoryChip + W6 阅读进度角标（N+1P，语义同
                   GalleryCard：showReadProgress 开且 page > 0 才显示）。 -->
              <template #meta>
                <CategoryChip v-if="row.chip" :category="row.chip" />
                <span
                  v-if="showReadProgressBadge(row.item)"
                  class="history-item__read-progress"
                  data-testid="read-progress-badge"
                >
                  {{ readProgressLabelOf(row.item) }}
                </span>
              </template>
            </AppListRow>
          </div>
        </ContentLayout>

        <!-- FabLayout replica: clear-history + back-to-top mini FABs.
             宽屏双栏下集群锚回左栏右下角（TwoPaneLayout :deep 规则）。 -->
        <FabLayout
          v-model:expanded="fabExpanded"
          primary-icon="reorder"
          :actions="fabActions"
          @click-secondary="onFabAction"
        />
      </template>

      <!-- 详情右栏：选中即原位加载（不跳路由）；面板返回箭头 = 清除选中。 -->
      <template #detail>
        <GalleryDetailPane
          :gid="selectedGid!"
          :token="selectedToken"
          pane
          @back="clearSelection"
        />
      </template>
    </TwoPaneLayout>

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

      <!-- Toast (Android Toast equivalent, F4/F6) -->
      <div v-if="toastMessage" class="toast" role="status">{{ toastMessage }}</div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
/**
 * HistoryView — web replica of Android `HistoryScene`:
 * ContentLayout (pull-to-refresh + empty tip) filled with the shared
 * `AppListRow` single-column rows (A4 定案，W3-F3——与下载页完全同构：
 * 缩略图→详情 / 主体→直接阅读 点击分区；`#badge` 挂最后浏览时间角标，
 * `#meta` 放 CategoryChip + 阅读进度角标)，加一个 FabLayout（清空历史 +
 * 回顶部）。
 *
 * 服务端分页（A4 / W2-B2 DB 分页）：`usePagedList` 状态机管理页码/条数/
 * 跳页/PC 键盘翻页；`fetchPage` 把 1 起页码换算成 /history/list 的 0 起
 * `page` 直传（响应信封 `{history, total}`，total 驱动 totalPages）。
 * 条数档位 50/100/200（服务端钳制 1..200，默认 50 与后端一致）。
 * Search（q，防抖）与 filter slots（A5d，q=pattern&regex=true）收窄服务端
 * 结果，两者都随 page/pageSize 直传并在变更时回第 1 页。
 *
 * KeepAlive（App.vue 按 fullPath 缓存实例）：页码与页内滚动位置随组件实例
 * 存续，从阅读器/详情返回即还原——页码还原语义 = 页码 + 页内滚动。
 *
 * 隐私红线：标题一律经 `maskedTitle`（打码开启 → `#<gid>`）；日文副题在
 * 打码开启时一并隐藏（同 GalleryCard 的 `!privacyMaskEnabled` 守卫）。
 * R4-6: 无标题画廊以 `#<gid>` 展示。
 */
import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { historyApi } from '@/api/history'
import type { HistoryItem } from '@/api/history'
import { useFilterSlots } from '@/composables/useFilterSlots'
import { usePagedList } from '@/composables/usePagedList'
import { useWebSocket } from '@/composables/useWebSocket'
import { syncInvalidation } from '@/stores/syncInvalidation'
import { maskedTitle, privacyMaskEnabled } from '@/utils/privacyMask'
import { isJpnSubtitleVisible } from '@/utils/jpnSubtitle'
import { usePreferencesStore } from '@/stores/preferences'
import FilterSlotBar from '@/components/FilterSlotBar.vue'
import {
  CATEGORY_BIT_VALUES,
  CATEGORY_BY_BIT,
  CATEGORY_LABELS,
  CATEGORY_ORDER,
  type FabAction,
  type GalleryCategory,
} from '@/types/components'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import TwoPaneLayout from '@/components/layout/TwoPaneLayout.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import AppListRow from '@/components/gallery/AppListRow.vue'
import GalleryDetailPane from '@/components/gallery/GalleryDetailPane.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import CategoryChip from '@/components/atoms/CategoryChip.vue'

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
/** W6：阅读进度角标开关读 `general.showReadProgress`（防御式读取）。 */
const preferences = usePreferencesStore()

/**
 * 每页条数档位（A4 定案对齐下载页：50/100/200，默认 50）。后端
 * HistoryService 把 pageSize 钳制在 1..200——三档全部有效。
 */
const HISTORY_PAGE_SIZES = [50, 100, 200] as const
/** 初始每页条数（无持久化偏好键，回落后同为 50）。 */
const DEFAULT_HISTORY_PAGE_SIZE = 50

const state = ref<ViewState>('loading')
const refreshing = ref(false)
const contentRef = ref<InstanceType<typeof ContentLayout> | null>(null)
/** F4 REGEX_INVALID: the error tip switches to a dedicated regex message. */
const errorText = ref('Failed to load history')

/** 标题计数：服务端过滤后全集条数（分页不再累计已加载数）。 */
const countLabel = computed(() => `${total.value} galleries`)

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
  // 槽位点击总是重新加载（清空搜索词不一定触发防抖 watch——搜索词本来就
  // 空时）。非静默 load → onLoadStart 切 loading 态。
  void load()
}

/** 当前筛选条件：槽位激活 → (q=pattern, regex=true)；否则 → 搜索词（LIKE）。 */
function currentFilter(): { q: string | null; regex: boolean } {
  const slot = filterSlot.value
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
    // 搜索词变化 → 回第 1 页重新加载（负载在服务端）。
    const next = searchQuery.value
    if (debouncedQuery.value !== next) {
      debouncedQuery.value = next
      // 槽位激活时该变更来自 selectFilterSlot 清空搜索词——加载已由
      // onSlotBarSelect 触发（避免与槽位过滤重复请求）。
      if (filterSlot.value) return
      void load()
    }
  }, 400)
}

function clearSearch(): void {
  searchQuery.value = ''
  debouncedQuery.value = ''
  void load()
}

/* ---------------------------------------------------- pagination bar ---- */

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

/**
 * 分页状态机（W3-C1 usePagedList）：页码 / 条数 / 跳页 / PC 页码窗口 /
 * PageUp/Down / stale 竞态守卫。fetchPage 适配 /history/list 的
 * page/pageSize 直传契约（usePagedList 页码 1 起 → 接口 0 起）；视图四态机
 * 与错误文案留在本视图，经 onLoadStart/onSuccess/onError 钩子接线。
 */
const {
  items: entries,
  total,
  pageSize,
  currentPage,
  totalPages,
  paginationVisible,
  pageWindow,
  load: loadPage,
  jumpToPage,
} = usePagedList<HistoryItem>({
  fetchPage: async (page, size) => {
    const filter = currentFilter()
    const data = await historyApi.listHistory(filter.q, filter.regex || undefined, page - 1, size)
    return { items: data.history, total: data.total }
  },
  pageSizes: HISTORY_PAGE_SIZES,
  initialPageSize: DEFAULT_HISTORY_PAGE_SIZE,
  fallbackPageSize: DEFAULT_HISTORY_PAGE_SIZE,
  onLoadStart: () => {
    state.value = 'loading'
  },
  onSuccess: (result) => {
    state.value = result.items.length === 0 ? 'empty' : 'content'
    contentRef.value?.scrollToTop()
  },
  onError: (error) => {
    console.error('Failed to load history', error)
    // F4: invalid regex in q → 400 REGEX_INVALID; name the cause instead of
    // the generic "failed to load" tip (dedicated toast + error-state copy).
    if (errorCodeOf(error) === 'REGEX_INVALID') {
      errorText.value = '正则无效，请检查搜索/筛选的正则表达式'
      showToast('正则无效，请检查筛选表达式')
    } else {
      errorText.value = 'Failed to load history'
    }
    if (entries.value.length === 0) {
      state.value = 'error'
    } else if (errorCodeOf(error) !== 'REGEX_INVALID') {
      showToast('Failed to refresh history')
    }
  },
  keyboardPaging: true,
})

/**
 * 视图层加载入口：搜索/槽位/刷新等所有入口都回第 1 页。`silent: true`
 * 不切 loading 态——下拉刷新等宿主自管忙态的入口。
 */
function load(opts?: { silent?: boolean }): Promise<void> {
  return loadPage(1, opts)
}

async function onRefresh(): Promise<void> {
  await load({ silent: true })
  refreshing.value = false
}

function onRetry(): void {
  void load()
}

/* --------------------------------------------------- row presentation --- */

/** 行展示标题——脱敏在本视图完成（隐私红线：标题必经 maskedTitle）。 */
function displayTitle(item: HistoryItem): string {
  return maskedTitle(item.title || item.titleJpn || `#${item.gid}`, item.gid)
}

/**
 * 日文副题：打码开启时一并隐藏（同 GalleryCard 的标题日文行守卫）；
 * showJpnTitle 加载后为 false 也隐藏（协议默认 false，prefs 未加载按显示
 * 渲染防闪失——T2 定案，判定收敛在 utils/jpnSubtitle）。本视图刻意不自发
 * preferences load（只读不 load 的既有约定），副题可能在 load 完成前短暂
 * 显示后按 false 收起，可接受。
 */
function displaySubtitle(item: HistoryItem): string | null {
  if (privacyMaskEnabled.value || !item.titleJpn) return null
  return isJpnSubtitleVisible(preferences.prefs?.general) ? item.titleJpn : null
}

/** Numeric category bit → `GalleryCategory` key (undefined when unknown). */
function categoryKeyOf(item: HistoryItem): GalleryCategory | undefined {
  return CATEGORY_BY_BIT[categoryBit(item.category)]
}

/**
 * Per-row view models: the raw history item plus the pre-resolved category
 * chip key (v-if narrows the property, not a function call — same shape as
 * DownloadView's virtualRows.chip).
 */
const rows = computed(() =>
  entries.value.map((item) => ({ item, chip: categoryKeyOf(item) })),
)

/* W6 (plan-2026-09-02): 阅读进度角标——showReadProgress 开且进度 > 0 才
   显示；历史行无总页数 → NP 格式（对齐 GalleryCard/下载行语义）。字段缺失
   （旧服务器 undefined）时隐藏。 */
function readProgressLabelOf(item: HistoryItem): string {
  const progress = item.page
  if (typeof progress !== 'number' || !Number.isFinite(progress) || progress <= 0) return ''
  return `${progress + 1}P`
}

function showReadProgressBadge(item: HistoryItem): boolean {
  const prefs = preferences.prefs?.general as { showReadProgress?: boolean } | undefined
  return prefs?.showReadProgress === true && readProgressLabelOf(item) !== ''
}

/* --------------------------------------------------- click partitions --- */

/**
 * 双栏右栏选中态（T3，内存组件态）：宽屏点选 → 右栏原位加载（不跳路由）；
 * 跨 960px 阈值选中保留；窄屏跳整页详情（P-A：本地 token 透传，现状行为）。
 */
const selectedGid = ref<number | null>(null)
const selectedToken = ref<string | undefined>(undefined)

function selectGallery(gid: number): void {
  const item = entries.value.find((entry) => entry.gid === gid)
  selectedGid.value = gid
  selectedToken.value = item?.token || undefined
}

/** 清除选中（右栏返回箭头 / Esc / 宽屏浏览器返回首次触发）。 */
function clearSelection(): void {
  selectedGid.value = null
  selectedToken.value = undefined
}

/** 缩略图/主体点击：宽屏双栏 = 选中右栏；窄屏 = 详情页（本地 token 透传）。 */
function openDetail(gid: number, wide: boolean): void {
  if (wide) {
    selectGallery(gid)
    return
  }
  const item = entries.value.find((entry) => entry.gid === gid)
  void router.push({
    path: `/gallery/${gid}`,
    query: item?.token ? { token: item.token } : {},
  })
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

/** Esc 统一挂 window（C7）：打开时注册，焦点不在面板内也能关闭。 */
function onDialogKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && !event.isComposing) closeClearDialog()
}

watch(showClearDialog, (open) => {
  if (open) {
    window.addEventListener('keydown', onDialogKeydown)
  } else {
    window.removeEventListener('keydown', onDialogKeydown)
  }
})

/* KeepAlive 停用守卫（audit P2，W1-F3/P1-5 同类）：App.vue 缓存本视图——
   对话框开着离开（如点进详情）时，window Escape 监听会随缓存实例残留并在
   后台误关对话框。停用即摘除；重新激活且对话框仍开着时摘后重挂
   （remove-before-add，对齐 SearchView P1-5 模式）。防抖时钟一并在停用时
   作废（重新输入会重排时钟）；卸载清理由下方 onUnmounted 兜底（缓存淘汰
   不经过 deactivated）。 */
onActivated(() => {
  if (showClearDialog.value) {
    window.removeEventListener('keydown', onDialogKeydown)
    window.addEventListener('keydown', onDialogKeydown)
  }
})

onDeactivated(() => {
  if (searchTimer) clearTimeout(searchTimer)
  window.removeEventListener('keydown', onDialogKeydown)
})

/* -------------------------------------------------------------- toast --- */

const toastMessage = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined

function showToast(message: string): void {
  toastMessage.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
  }, 2400)
}

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
  clearTimeout(toastTimer)
  window.removeEventListener('keydown', onDialogKeydown)
})

function closeClearDialog(): void {
  if (!clearing.value) showClearDialog.value = false
}

async function confirmClear(): Promise<void> {
  if (clearing.value) return
  clearing.value = true
  try {
    await historyApi.clearHistory()
    entries.value = []
    total.value = 0
    state.value = 'empty'
    showClearDialog.value = false
  } catch (error) {
    console.error('Failed to clear history', error)
    // F6: 失败不再静默——关对话框（对齐成功路径）+ 错误 toast 说明结果。
    showClearDialog.value = false
    showToast('清除历史失败，请稍后重试')
  } finally {
    clearing.value = false
  }
}

/* ------------------------------------ 实时对账（WP8 任务 2） -------------- */

/** 本视图只消费连接态（connect 由 App 壳持有——只解构 connected 不 connect）。 */
const { connected } = useWebSocket()

// /topic/sync 广播 history 实体变更（他端或本端写）→ 静默重拉当前页；
// watch 默认非 immediate——首帧不触发。WS 重连成功（断线窗口内可能漏帧）同款。
watch(() => syncInvalidation.consume('history'), () => void load({ silent: true }))
watch(connected, (up, was) => {
  if (up && was === false) void load({ silent: true })
})

onMounted(() => {
  void load()
  // 深链直入时无其他视图代为预热偏好；不加载则 showReadProgress 角标
  // 按空 prefs 的防御默认隐藏（HomeView/DownloadView 同款守卫）。
  if (!preferences.prefs && !preferences.loading) void preferences.load()
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

/* 汉堡可见视口（<720px，或横屏矮视口）：顶部标题行避让浮动汉堡；
   列表内容全宽，从汉堡下穿过（FAB 语义）。 */
@media (max-width: 719px), (min-width: 720px) and (max-height: 479.98px) {
  .history-view__heading {
    padding-left: var(--hamburger-clearance);
  }
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

/* ----------------------------------------------------- pagination bar ---- */
/* 与 DownloadView 分页条同构复刻（A4）：页码 / 每页条数 / 跳页。 */
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

/* PC 页码窗口：直点页码 + 省略号折叠 + 前后页。 */
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

.pagination-bar__size {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  white-space: nowrap;
}

.pagination-bar__select {
  padding: 2px 6px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: var(--color-surface);
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-super-small);
}

.pagination-bar__select:focus {
  outline: none;
  border-color: var(--color-primary);
}

/* -------------------------------------------------------------- list ---- */
/* Single-column dense rows (A4): the row skeleton (card surface / thumb /
   title) lives in the shared AppListRow; only the history-specific
   last-viewed corner badge and the read-progress stamp are styled here. */
.history-list {
  padding: var(--gallery-list-margin-v) var(--gallery-list-margin-h)
    var(--gallery-padding-bottom-fab);
}

/* Last-viewed timestamp — clock glyph + compact date/time, secondary ink.
   Absolute corner badge anchored to the AppListRow row (its badge slot). */
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

/* W6: 阅读进度角标 — 12sp secondary，跟在分类 chip 之后。 */
.history-item__read-progress {
  flex-shrink: 0;
  font-size: var(--text-super-small); /* 12sp */
  color: var(--text-color-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* ------------------------------------------------------------- toast ---- */
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
  .dialog,
  .toast {
    animation: none;
  }
}
</style>
