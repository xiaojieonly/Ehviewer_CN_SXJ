<template>
  <div class="home" :style="homeBannerOffset">
    <!-- EH 熔断提示（plan-2026-08-30）：只读本地内容仍可用；「重新连接」探测
         成功后刷新列表。放在视图根顶部一行（ContentLayout 外）。 -->
    <AvailabilityBanner
      v-if="availability.state === 'down'"
      class="home__banner"
      @refresh="onRefresh"
    />
    <!-- Floating SearchBar (Android: the search bar floats over the list;
         the list clears it via --gallery-padding-top-search-bar). Kept below
         the pull-to-refresh header (z 5) but above the scrolling content. -->
    <div class="home__searchbar">
      <SearchBar
        :state="searchState"
        :title="searchTitle"
        :query="keyword"
        hint="搜索画廊"
        :left-icon="null"
        right-icon="magnify-dark"
        :suggestions="suggestions"
        filter-visible
        :filter-panel-open="filterPanelOpen"
        :filter-active="activeFilterChips.length > 0"
        :filter-chips="activeFilterChips"
        @update:query="keyword = $event"
        @search="applySearch"
        @click-title="enterSearchMode"
        @click-action="enterSearchMode"
        @back="leaveSearchMode"
        @select-suggestion="onSelectSuggestion"
        @dismiss-suggestion="onDismissSuggestion"
        @click-filter="filterPanelOpen = !filterPanelOpen"
        @remove-filter-chip="onRemoveFilterChip"
        @clear-filter-chips="onClearFilters"
      />
      <!-- Wave-1 1a: anchored PC filter popover, coexists with the viewMode
           toggle (P2 stitching). -->
      <FilterPanel
        v-model:open="filterPanelOpen"
        :filters="activeFilters"
        @update:filters="applyFilters"
        @search="onFilterPanelSearch"
      />
    </div>

    <!-- ContentLayout: loading spinner / sadpanda empty tip / error retry /
         pull-to-refresh header / infinite-paging footer / FastScroller. -->
    <ContentLayout
      ref="contentLayoutRef"
      class="home__content"
      :state="contentState"
      :refreshing="refreshing"
      :loading-more="loadingMore"
      empty-text="这里什么都没有"
      :error-text="errorText"
      @update:refreshing="refreshing = $event"
      @refresh="onRefresh"
      @load-more="onLoadMore"
      @retry="onRefresh"
    >
      <template #empty>
        <AppIcon name="sad-panda-primary" size="64px" class="home__empty-icon" />
        <p class="home__empty-text">还没有画廊数据&#10;去搜索或登录后开始浏览</p>
        <div class="home__empty-actions">
          <button
            type="button"
            class="home__empty-cta"
            @click.stop="goSearch"
          >
            去搜索
          </button>
          <button
            v-if="!authStore.isAuthenticated"
            type="button"
            class="home__empty-cta home__empty-cta--ghost"
            @click.stop="goLogin"
          >
            登录
          </button>
        </div>
        <p v-if="ehSessionChecked && !ehSignedIn" class="home__empty-eh-hint">
          未登录 EH 会话，画廊可能无法阅读
          <button type="button" class="home__empty-eh-link" @click.stop="goEhSession">
            前往配置
          </button>
        </p>
      </template>
      <!-- Toplist feed: lightweight ranked rows (rank + tag + value).
           Feed mode replaces the virtualized gallery grid entirely. -->
      <div v-if="feedMode === 'toplist'" class="home__toplist">
        <a
          v-for="(item, index) in topList"
          :key="item.gid"
          class="home__toplist-row"
          :href="item.href"
          target="_blank"
          rel="noopener"
          :data-testid="`toplist-row-${index}`"
        >
          <span class="home__toplist-rank">{{ index + 1 }}</span>
          <span class="home__toplist-tag">{{ item.tag }}</span>
          <span class="home__toplist-value">{{ item.value }}</span>
        </a>
      </div>
      <!-- Virtualized gallery list: only the rows intersecting the viewport
           (+ overscan) are mounted; the spacers above/below preserve the full
           scroll height so the scrollbar and the load-more footer (rendered
           after the slot by ContentLayout) keep working. GalleryList (B-1)
           renders the grid/list form from `prefs.general.listMode` and owns
           the view-mode toggle. -->
      <div v-else ref="virtualHostRef" class="home__virtual">
        <div
          class="home__virtual-spacer"
          :style="{ height: `${topSpacerHeight}px` }"
          aria-hidden="true"
        />
        <GalleryList :items="visibleGalleries" @select="openGallery" />
        <div
          class="home__virtual-spacer"
          :style="{ height: `${bottomSpacerHeight}px` }"
          aria-hidden="true"
        />
      </div>
    </ContentLayout>

    <!-- FAB pair: primary = back to top (Android `v_go_to`), secondary =
         refresh. The cluster once expanded speed-dial style, but after the
         list/grid toggle moved into GalleryList's toolbar (B-1) only refresh
         remained — a single action behind an expand step is pure friction,
         so both FABs are permanently visible (FabLayout alwaysVisible). -->
    <FabLayout
      always-visible
      primary-icon="go-to-dark"
      :actions="fabActions"
      @click-primary="onPrimaryFab"
      @click-secondary="onSecondaryFab"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * HomeView — the gallery list screen (S1), replicating Android `HomeScene` +
 * `GalleryListScene`: a floating SearchBar over an auto-column gallery list,
 * pull-to-refresh, infinite paging, empty/error tips and the bottom-right
 * FabLayout cluster.
 *
 * Composition (all frozen-contract components):
 * - `SearchBar`   — controlled state machine (normal → search → search-list);
 *                   quick searches load as suggestion rows, mirroring the
 *                   Android quick-search entries in the suggestion list.
 * - `ContentLayout` — ViewTransition states (loading / content / empty /
 *                   error), pull-to-refresh header (v-model:refreshing),
 *                   `load-more` footer paging, sadpanda tip retry.
 * - `GalleryList` — shared grid/list renderer (B-1): consumes
 *                   `prefs.general.listMode` and owns the view-mode toggle;
 *                   grid column count is width-derived, never hardcoded
 *                   (contracts/responsive-strategy.md §4).
 * - `FabLayout`   — primary go-to-top + secondary refresh.
 *
 * The list/grid mode lives in server preferences (`general.listMode`). The
 * legacy localStorage persistence is migrated once on first load (B-1): the
 * stored value is written into preferences and the key then removed.
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { galleryApi, type FeedMode } from '@/api/gallery'
import { isOfflineError, isEhUnavailableError } from '@/api/client'
import { availability, loadAvailability, markDown } from '@/stores/availability'
import AvailabilityBanner from '@/components/common/AvailabilityBanner.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import SearchBar from '@/components/search/SearchBar.vue'
import FilterPanel from '@/components/search/FilterPanel.vue'
import GalleryList, { resolveListMode } from '@/components/gallery/GalleryList.vue'
import type { SearchFilters } from '@/api/gallery'
import {
  filterChips,
  isFilterActive,
  removeFilterChip,
  type FilterChip,
} from '@/components/search/searchFilters'
import { useAuthStore } from '@/stores/auth'
import { usePreferencesStore } from '@/stores/preferences'
import type {
  ContentState,
  FabAction,
  GalleryInfo,
  SearchBarState,
  SearchSuggestion,
} from '@/types/components'
import type { TopListItem } from '@/types'

/** ContentLayout's view states (frozen `ContentState` + its `error` extra). */
type HomeContentState = ContentState | 'error'

/** AnotherViewer's default gallery page size. */
const PAGE_SIZE = 25

/**
 * Legacy localStorage key of the pre-preferences list mode. Read once during
 * the B-1 migration, written into `general.listMode`, then removed.
 */
const LIST_MODE_KEY = 'anotherviewer-webui:gallery-list-mode'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const preferencesStore = usePreferencesStore()

/**
 * EH 提示条（AvailabilityBanner 固定 40px 高）可见时的偏移量：浮动 SearchBar
 * 与列表顶部清理位同步下移，保证不遮挡横幅也不相互覆盖。
 */
const homeBannerOffset = computed(() => ({
  '--availability-offset': availability.state === 'down' ? '40px' : '0px',
}))

/* -------------------------------- feed mode ----------------------------- */

/** CN labels for the frozen feed modes (`?feed=` query on the home route). */
const FEED_TITLES: Record<FeedMode, string> = {
  subscription: '订阅',
  popular: '热门',
  toplist: '排行榜',
}

/** Active feed mode from the route query; undefined = plain search home. */
const feedMode = computed<FeedMode | undefined>(() => {
  const q = route.query.feed
  return typeof q === 'string' && q in FEED_TITLES ? (q as FeedMode) : undefined
})

const feedTitle = computed(() => (feedMode.value ? FEED_TITLES[feedMode.value] : undefined))

/* -------------------------------- list state ---------------------------- */

const galleries = ref<GalleryInfo[]>([])
const topList = ref<TopListItem[]>([])
const page = ref(0)
const total = ref(0)
const fetchedPages = ref(0)
const noMoreData = ref(false)
const contentState = ref<HomeContentState>('loading')
const refreshing = ref(false)
const loadingMore = ref(false)
/** Error-state copy; switched to an offline tip when the SW reports 503 offline. */
const errorText = ref('加载失败，请稍后重试')
const contentLayoutRef = ref<InstanceType<typeof ContentLayout> | null>(null)

/** Monotonic request guard — stale responses (fast refresh / search) drop. */
let requestSeq = 0

/** Append-with-dedupe by gid — bumped galleries can reappear across pages. */
function appendDeduped<T extends { gid: number }>(
  existing: T[],
  fresh: T[],
): { items: T[]; noMore: boolean } {
  const seen = new Set(existing.map((g) => g.gid))
  const added = fresh.filter((g) => !seen.has(g.gid))
  return { items: [...existing, ...added], noMore: fresh.length === 0 || added.length === 0 }
}

/**
 * Commit a fetched page onto a gid-keyed list and drive the shared
 * page/total/fetched/no-more bookkeeping for both the search and feed paths.
 * Returns the resulting list so the caller can assign its ref.
 */
function commitPage<T extends { gid: number }>(
  current: T[],
  next: T[],
  target: number,
  count: number,
  mode: 'replace' | 'append',
): T[] {
  page.value = target
  total.value = count
  if (mode === 'replace') {
    fetchedPages.value = 1
    noMoreData.value = next.length === 0
    return next
  }
  const { items, noMore } = appendDeduped(current, next)
  if (noMore) noMoreData.value = true
  else fetchedPages.value += 1
  return items
}

async function loadPage(target: number, mode: 'replace' | 'append'): Promise<void> {
  const seq = ++requestSeq
  if (mode === 'append') loadingMore.value = true
  try {
    // Feed mode drives the data source; the search keyword is ignored there
    // (frozen feed contract — subscription/popular mirror search's envelope).
    const feed = feedMode.value
    if (feed === 'toplist') {
      const res = await galleryApi.feed('toplist', target, PAGE_SIZE)
      if (seq !== requestSeq) return
      topList.value = commitPage(topList.value, res.data, target, res.total, mode)
    } else if (feed) {
      const res = await galleryApi.feed(feed, target, PAGE_SIZE)
      if (seq !== requestSeq) return
      galleries.value = commitPage(galleries.value, res.data, target, res.total, mode)
    } else {
      const res = await galleryApi.search(
        appliedKeyword.value || undefined,
        undefined,
        target,
        PAGE_SIZE,
        isFilterActive(activeFilters.value) ? activeFilters.value : undefined,
      )
      if (seq !== requestSeq) return
      galleries.value = commitPage(galleries.value, res.data, target, res.total, mode)
    }
    contentState.value = (feed === 'toplist' ? topList.value : galleries.value).length > 0 ? 'content' : 'empty'
  } catch (error) {
    if (seq !== requestSeq) return
    console.error('[HomeView] 加载画廊列表失败', error)
    // Append failures keep the loaded content; replace failures show the
    // error tip (retry button re-triggers a refresh).
    if (mode === 'replace') {
      if (isEhUnavailableError(error)) {
        // EH 熔断（§0）：列表端点不携带 HTTP 错误码（success:false + cause），
        // 由视图落 DOWN 标记 + 专属文案——服务器已短路，只读本地内容。
        markDown()
        errorText.value = 'EH 平台当前不可达，仅显示本地内容'
      } else {
        errorText.value = isOfflineError(error) ? '当前离线，且无本地缓存可用' : '加载失败，请稍后重试'
      }
      contentState.value = 'error'
    }
  } finally {
    // F5: 无条件复位——append 被后续 replace 作废（seq 过期）时也必须松开
    // loadingMore，否则页脚 spinner 永久卡死（对照 DownloadView.loadMore）。
    if (mode === 'append') loadingMore.value = false
  }
}

/** Pull-to-refresh / error retry / empty tip retry / FAB refresh. */
async function onRefresh(): Promise<void> {
  if (contentState.value === 'content' && galleries.value.length > 0) {
    // Keep the list visible under the parked refresh header (Android
    // RefreshLayout behavior), then snap back to the top.
    refreshing.value = true
    try {
      await loadPage(0, 'replace')
    } finally {
      refreshing.value = false
    }
    contentLayoutRef.value?.scrollToTop()
  } else {
    refreshing.value = false
    contentState.value = 'loading'
    await loadPage(0, 'replace')
  }
}

/** ContentLayout scrolled near the bottom — fetch and append the next page. */
function onLoadMore(): void {
  if (contentState.value !== 'content' || refreshing.value || loadingMore.value) return
  if (noMoreData.value) return
  if (fetchedPages.value * PAGE_SIZE >= total.value) return
  void loadPage(page.value + 1, 'append')
}

function openGallery(gallery: GalleryInfo): void {
  const query = gallery.token ? { token: gallery.token } : undefined
  void router.push({ path: `/gallery/${gallery.gid}`, query })
}

/* ------------------------- empty-state guided CTA ----------------------- */

/** Empty state: guide to the search screen. */
function goSearch(): void {
  void router.push('/search')
}

/** Empty state: guide to login when the user isn't authenticated. */
function goLogin(): void {
  void router.push('/login')
}

/* ------------------------- empty-state EH session hint ------------------- */

/** EH session synced from the Android app (expired counts as not signed in). */
const ehSignedIn = ref(false)
const ehSessionChecked = ref(false)

/** Empty state: guide to the EH session config when the session isn't synced. */
function goEhSession(): void {
  void router.push('/admin/eh')
}

/** Probe the EH session state once on mount; failures keep the hint visible. */
async function checkEhSession(): Promise<void> {
  try {
    const state = await authApi.ehSession()
    ehSignedIn.value = state.signedIn && !state.expired
  } catch {
    /* 无法确认时保守提示。 */
  } finally {
    ehSessionChecked.value = true
  }
}

/* ------------------------------ list/grid mode -------------------------- */

/**
 * Active layout — same source GalleryList consumes: `prefs.general.listMode`,
 * falling back to grid while the preferences are absent (B-1 defensive read).
 * HomeView needs it for the virtual-window column/row math below.
 */
const viewMode = computed(() => resolveListMode(preferencesStore.prefs?.general?.listMode))

/** One-shot guard for the legacy localStorage → preferences migration. */
let legacyModeMigrated = false

/**
 * B-1 migration: the pre-preferences list mode lived in localStorage. Once
 * preferences are available the stored value is written into
 * `general.listMode` (debounced PUT /preferences) and the key removed —
 * exactly once, so server-side values win afterwards.
 */
function migrateLegacyListMode(): void {
  if (legacyModeMigrated || !preferencesStore.prefs) return
  legacyModeMigrated = true
  try {
    const legacy = localStorage.getItem(LIST_MODE_KEY)
    if (legacy === null) return
    preferencesStore.updateGeneral({ listMode: legacy === 'grid' ? 'grid' : 'list' })
    localStorage.removeItem(LIST_MODE_KEY)
  } catch {
    /* Storage unavailable — nothing to migrate. */
  }
}

watch(() => preferencesStore.prefs, migrateLegacyListMode, { immediate: true })

/* --------------------------------- search ------------------------------- */

const searchState = ref<SearchBarState>('normal')
const keyword = ref('')
const appliedKeyword = ref('')

/* ------------------------- Wave-1 1a search filters ---------------------- */

const filterPanelOpen = ref(false)
const activeFilters = ref<SearchFilters>({})
const activeFilterChips = computed<FilterChip[]>(() => filterChips(activeFilters.value))

function applyFilters(next: SearchFilters): void {
  activeFilters.value = next
  void applySearch(keyword.value)
}

function onRemoveFilterChip(chipId: string): void {
  applyFilters(removeFilterChip(activeFilters.value, chipId))
}

function onClearFilters(): void {
  applyFilters({})
}

function onFilterPanelSearch(): void {
  filterPanelOpen.value = false
  void applySearch(keyword.value)
}
const suggestions = ref<SearchSuggestion[]>([])
let quickSearchesLoaded = false

/** Title row: feed name in feed mode, else the active keyword / neutral label. */
const searchTitle = computed(() => feedTitle.value ?? (appliedKeyword.value || '搜索'))

function enterSearchMode(): void {
  searchState.value = suggestions.value.length > 0 ? 'search-list' : 'search'
  void loadQuickSearches()
}

function leaveSearchMode(): void {
  searchState.value = 'normal'
}

/** IME action / programmatic search — reload page 0 with the new keyword. */
function applySearch(query: string): void {
  const q = query.trim()
  keyword.value = q
  appliedKeyword.value = q
  searchState.value = 'normal'
  contentState.value = 'loading'
  void loadPage(0, 'replace')
}

/**
 * Quick searches as suggestion rows (Android shows them in the SearchBar
 * suggestion list). `hint` carries the stored keyword so a tap can apply the
 * search directly; long-press dismisses the row locally.
 */
async function loadQuickSearches(): Promise<void> {
  if (quickSearchesLoaded) return
  try {
    const res = await galleryApi.getQuickSearches()
    if (res.success) {
      suggestions.value = res.data.map((qs) => ({
        text: qs.name,
        hint: qs.keyword || undefined,
      }))
    }
  } catch {
    /* Suggestions are optional enrichment — never block search on them. */
  } finally {
    quickSearchesLoaded = true
  }
  if (suggestions.value.length > 0 && searchState.value === 'search') {
    searchState.value = 'search-list'
  }
}

function onSelectSuggestion(suggestion: SearchSuggestion): void {
  applySearch(suggestion.hint ?? suggestion.text)
}

function onDismissSuggestion(suggestion: SearchSuggestion): void {
  suggestions.value = suggestions.value.filter((s) => s !== suggestion)
}

/* ---------------------------------- FABs -------------------------------- */

/**
 * The list/grid toggle moved into GalleryList's sticky toolbar (B-1) — the
 * cluster keeps refresh only, so it renders always-visible (no speed-dial).
 */
const fabActions: FabAction[] = [{ id: 'refresh', icon: 'refresh-dark', label: '刷新列表' }]

function onPrimaryFab(): void {
  contentLayoutRef.value?.scrollToTop()
}

function onSecondaryFab(action: FabAction): void {
  if (action.id === 'refresh') {
    void onRefresh()
  }
}

/* ------------------------------ virtual scrolling ------------------------ */

/**
 * Lightweight windowing for the gallery list (the previous `useVirtualScroll`
 * composable had no replacement after the audit): only the rows intersecting
 * the scroller viewport (+ overscan) are handed to GalleryList, while two
 * spacer divs above/below the list preserve the full list height — scrollbar
 * geometry, the FastScroller and ContentLayout's load-more footer (which
 * reads `scrollHeight` and lives after the slot) all keep working untouched.
 *
 * Geometry mirrors the CSS sources:
 * - grid columns = `floor((width + gap) / (minColumn + gap))` — the same
 *   math as GalleryGrid's `auto-fill minmax(120px, 1fr)`, reading
 *   `--column-width-grid-middle` / `--gallery-grid-interval` from computed
 *   style so breakpoint overrides stay honored;
 * - grid row height = column width × 1.5 (GalleryCard's default 2:3 tile);
 * - list mode = `floor(width / min(width, 480px))` auto-columns (GalleryList
 *   rows, `--column-width-list-long`) × measured first-card height, falling
 *   back to the 120px 2:3 thumb + paddings estimate.
 *
 * Rows are only estimated (tiles clamp to per-gallery aspect ratios), so an
 * overscan buffer keeps the window generous; cards re-run their staggered
 * entrance reveal when they mount (GalleryGrid/GalleryList own the
 * animation). Virtualization engages only with real viewport geometry —
 * headless environments (no layout) fall back to rendering the whole list.
 */
const OVERSCAN_ROWS = 3
/** `--column-width-grid-middle` (120px) fallback when styles are unavailable. */
const GRID_MIN_COLUMN_WIDTH = 120
/** `--gallery-grid-interval` (0dp) fallback when styles are unavailable. */
const GRID_GAP = 0
/** `--column-width-list-long` (480px) fallback when styles are unavailable. */
const LIST_MIN_COLUMN_WIDTH = 480
/** List-mode row estimate: 120px 2:3 thumb + body padding + card margins. */
const LIST_ROW_HEIGHT = 136

const virtualHostRef = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const containerHeight = ref(0)
const containerWidth = ref(0)
const columns = ref(1)
const rowHeight = ref(LIST_ROW_HEIGHT)

let scrollEl: HTMLElement | null = null
let scrollHandler: (() => void) | null = null
let onWindowResize: (() => void) | null = null
let resizeObserver: ResizeObserver | null = null

const totalRows = computed(() => Math.ceil(galleries.value.length / columns.value))

/** Virtualization only engages once real viewport geometry is measurable. */
const virtualized = computed(
  () => containerHeight.value > 0 && rowHeight.value > 0 && columns.value > 0,
)

const startRow = computed(() => {
  if (!virtualized.value) return 0
  return Math.min(
    Math.max(0, Math.floor(scrollTop.value / rowHeight.value) - OVERSCAN_ROWS),
    totalRows.value,
  )
})

const endRow = computed(() => {
  if (!virtualized.value) return totalRows.value
  return Math.min(
    totalRows.value,
    Math.ceil((scrollTop.value + containerHeight.value) / rowHeight.value) + OVERSCAN_ROWS,
  )
})

/** Only the windowed slice of galleries is handed to GalleryGrid. */
const visibleGalleries = computed(() => {
  if (!virtualized.value) return galleries.value
  return galleries.value.slice(startRow.value * columns.value, endRow.value * columns.value)
})

/** Spacer above the windowed grid — preserves the full-height scroll geometry. */
const topSpacerHeight = computed(() => startRow.value * rowHeight.value)

/** Spacer below the windowed grid (the load-more footer sits after it). */
const bottomSpacerHeight = computed(() => (totalRows.value - endRow.value) * rowHeight.value)

/**
 * The scrollable ancestor of the slot host — ContentLayout's scroller
 * (FastScroller's container or the plain scroll div). Detected via computed
 * overflow, with the known container classes as a style-unaware fallback.
 */
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

/** Re-derive viewport size, column count and row height from the live DOM. */
function measure(): void {
  const host = virtualHostRef.value
  if (!host) return
  const scroller = scrollEl ?? findScroller(host)
  if (!scroller) {
    containerHeight.value = 0
    return
  }
  scrollEl = scroller
  containerHeight.value = scroller.clientHeight
  containerWidth.value = scroller.clientWidth
  const style = getComputedStyle(host)
  if (viewMode.value === 'list') {
    // Rows auto-fill at `min(100%, --column-width-list-long)` tracks — the
    // same math as GalleryList's `.gallery-list__rows`.
    const listColumnWidth =
      parseFloat(style.getPropertyValue('--column-width-list-long')) || LIST_MIN_COLUMN_WIDTH
    const track = Math.min(containerWidth.value, listColumnWidth)
    columns.value = Math.max(1, Math.floor(containerWidth.value / Math.max(1, track)))
    const cardHeight = host.querySelector('.app-card')?.getBoundingClientRect().height ?? 0
    rowHeight.value = cardHeight > 0 ? cardHeight : LIST_ROW_HEIGHT
  } else {
    const minColumn = parseFloat(style.getPropertyValue('--column-width-grid-middle')) || GRID_MIN_COLUMN_WIDTH
    const gap = parseFloat(style.getPropertyValue('--gallery-grid-interval')) || GRID_GAP
    columns.value = Math.max(1, Math.floor((containerWidth.value + gap) / (minColumn + gap)))
    const columnWidth = (containerWidth.value - (columns.value - 1) * gap) / columns.value
    rowHeight.value = columnWidth * 1.5
  }
}

/** Attach scroll/resize listeners to the scroller (idempotent). */
function attachVirtualScroll(): void {
  detachVirtualScroll()
  const host = virtualHostRef.value
  if (!host) return
  const scroller = findScroller(host)
  if (!scroller) return
  scrollEl = scroller
  scrollHandler = () => {
    scrollTop.value = scroller.scrollTop
  }
  scroller.addEventListener('scroll', scrollHandler, { passive: true })
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => measure())
    resizeObserver.observe(scroller)
  }
  onWindowResize = () => measure()
  window.addEventListener('resize', onWindowResize)
  measure()
}

function detachVirtualScroll(): void {
  if (scrollEl && scrollHandler) scrollEl.removeEventListener('scroll', scrollHandler)
  if (onWindowResize) window.removeEventListener('resize', onWindowResize)
  scrollEl = null
  scrollHandler = null
  onWindowResize = null
  resizeObserver?.disconnect()
  resizeObserver = null
  containerHeight.value = 0
}

/* --------------------------------- lifecycle ---------------------------- */

onMounted(() => {
  // Preferences feed the GalleryList mode and the legacy-mode migration.
  // GalleryList would load them on its own mount, but that only happens once
  // the first page lands (ContentLayout renders its slot in the content
  // state) — kick the load here so the migration runs as early as possible.
  if (!preferencesStore.prefs && !preferencesStore.loading) {
    void preferencesStore.load()
  }
  // Probe the EH session once so the empty-state hint can guide users whose
  // Android-login session hasn't synced yet (or has expired).
  void checkEhSession()
  // 读取服务器侧 EH 熔断状态（幂等：in-flight 单飞）——DOWN 时顶部提示条
  // 与自动请求短路（服务器/拦截器）同时生效。
  void loadAvailability()
  void loadPage(0, 'replace')
})

/**
 * Feed navigation (/?feed=popular → /?feed=toplist, or a feed → the plain
 * home) reuses this component instance — reload page 0 when the query
 * changes (requestSeq drops any stale in-flight page).
 */
watch(
  () => route.query.feed,
  () => {
    topList.value = []
    galleries.value = []
    page.value = 0
    fetchedPages.value = 0
    noMoreData.value = false
    contentState.value = 'loading'
    void loadPage(0, 'replace')
  },
)

/** (Re)attach once ContentLayout swaps in the scrolling content view. */
watch(
  contentState,
  (state) => {
    if (state === 'content') {
      void nextTick(() => attachVirtualScroll())
    } else {
      detachVirtualScroll()
    }
  },
  { immediate: true },
)

/** List/grid toggle changes the column math — re-measure. */
watch(viewMode, () => {
  void nextTick(() => measure())
})

onBeforeUnmount(detachVirtualScroll)
</script>

<style scoped>
.home {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--color-bg);
}

/* Floating SearchBar — above the scrolling content (ContentLayout's scroller
   sits at z-index 1) but below its pull-to-refresh header (z-index 5), so
   the spinner stays visible while pulling; the FabLayout cluster (z 90/100)
   tops everything. */
.home__banner {
  flex-shrink: 0;
}

.home__searchbar {
  position: absolute;
  /* Offset below the status bar / cutout; the list clears the bar via
     --gallery-padding-top-search-bar, which carries the same inset.
     EH 提示条可见时（--availability-offset 40px）同步下移，与内容区顶部
     行对齐，不遮挡横幅。 */
  top: calc(var(--safe-area-top) + var(--availability-offset, 0px));
  left: 0;
  right: 0;
  z-index: 4;
  /* Only the bar card itself intercepts input; the margin area passes
     scroll gestures through to the list underneath. */
  pointer-events: none;
}

.home__searchbar :deep(.search-bar) {
  pointer-events: auto;
}

/* The anchored FilterPanel popover must also receive input (the floating
   bar container is pointer-events: none so the margin passes scrolls). */
.home__searchbar :deep(.filter-panel) {
  pointer-events: auto;
}

.home__content {
  flex: 1 1 auto;
  min-height: 0;
}

/* GalleryList's sticky toggle toolbar clears the floating SearchBar through
   `--gallery-list-clear-top` (its grid/rows no longer carry the clearance
   themselves). */
.home__virtual {
  --gallery-list-clear-top: calc(
    var(--gallery-padding-top-search-bar) + var(--availability-offset, 0px)
  );
}

/* Toplist feed rows — clear the floating SearchBar like the gallery list. */
.home__toplist {
  padding-top: calc(
    var(--gallery-padding-top-search-bar) + var(--availability-offset, 0px)
  );
}

.home__toplist-row {
  display: flex;
  align-items: center;
  gap: var(--spacing);
  padding: 12px var(--keyline-margin);
  border-bottom: 1px solid var(--color-divider);
  color: var(--text-color-primary);
  text-decoration: none;
}

.home__toplist-rank {
  min-width: 24px;
  font-weight: 600;
  color: var(--color-primary);
}

.home__toplist-tag {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home__toplist-value {
  flex: none;
  color: var(--text-color-secondary);
}

/* Empty-state guided CTA (UX-07): sadpanda + informative copy + actions.
   Rendered inside ContentLayout's `empty` slot (its tip area is a full-area
   retry button — the CTAs stop propagation so tapping them never retriggers
   a refresh). */
.home__empty-icon {
  color: var(--color-primary);
}

.home__empty-text {
  margin: 0;
  font-size: var(--text-little-small);
  color: var(--text-color-secondary);
  white-space: pre-line;
  text-align: center;
}

.home__empty-actions {
  display: flex;
  gap: var(--spacing);
}

.home__empty-cta {
  padding: 8px 24px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: var(--text-small);
  cursor: pointer;
  transition: background 150ms linear;
}

.home__empty-cta:active {
  background: var(--color-primary-dark);
}

.home__empty-cta--ghost {
  background: var(--color-background-floating);
  color: var(--drawable-color-primary);
  box-shadow: 0 1px 4px var(--shadow-color);
}

.home__empty-cta--ghost:active {
  background: var(--color-surface-activated);
}
</style>
