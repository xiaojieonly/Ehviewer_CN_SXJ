<template>
  <div class="home" :style="homeBannerOffset">
    <!-- 平板对齐（T3，2026-09-20 定案）：宽屏（≥960px）双栏——列表恒驻左栏
         （360px 固定宽）+ 详情右栏；无选中渲染占位符（不自动选中）。窄屏
         (<960px) 退化为单列列表，行为与现状完全一致（点击进整页详情）。
         返回语义（宽屏有选中）：Esc / 浏览器返回先清选中，再按一次才真正
         离开页面（拦截在 TwoPaneLayout，宿主只接 clear-selection）。 -->
    <TwoPaneLayout
      :has-selection="selectedGid !== null"
      placeholder="从左侧选择一个画廊查看详情"
      @clear-selection="clearSelection"
    >
      <template #list="{ wide }">
        <!-- EH 熔断提示（plan-2026-08-30）：只读本地内容仍可用；「重新连接」
             探测成功后刷新列表。列表栏顶部一行（与浮动搜索条同步避让）。 -->
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
          <!-- Wave-1 1a: anchored PC filter popover, coexists with the search
               input (keywordMode + save-quick-search wiring mirrors SearchView,
               plan-2026-09-05 C3 — the radio and the save action were dead
               without it). -->
          <FilterPanel
            v-model:open="filterPanelOpen"
            v-model:keyword-mode="keywordMode"
            :filters="activeFilters"
            @update:filters="applyFilters"
            @search="onFilterPanelSearch"
            @save-quick-search="openSaveQuickSearch"
          />
        </div>

        <!-- 分页条（A4 定案，W3-F1）：与下载/历史页同构的固定分页导航——页码
             窗口 + 前后页 + 跳页。上游（EH 站点列表）固定每页 25 条、没有条数
             档位，故不提供条/页下拉。total ≤ pageSize 隐藏（Android
             PaginationIndicator 语义）；toplist 无上游分页（total=返回行数），
             同样不渲染。固定在滚动区上方、吃浮动搜索条清理位。 -->
        <nav
          v-if="showPagination"
          class="pagination-bar home__pagination"
          data-testid="home-pagination"
          aria-label="首页分页"
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
          <!-- 用户定案（2026-09-07）：跳页输入仅下载页保留——站点最新列表是流式
               内容， arbitrary 页码没有目标语义。页码窗口/前后页保留。 -->
        </nav>

        <!-- ContentLayout: loading spinner / sadpanda empty tip / error retry /
             pull-to-refresh header（无限滚动页脚已随 A4 分页退役）. -->
        <ContentLayout
          ref="contentLayoutRef"
          class="home__content"
          :state="contentState"
          :refreshing="refreshing"
          empty-text="这里什么都没有"
          :error-text="errorText"
          @update:refreshing="refreshing = $event"
          @refresh="onRefresh"
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
               Feed mode replaces the gallery list entirely. -->
          <div v-if="feedMode === 'toplist'" class="home__toplist">
            <!-- 打码模式下后端把 value 替换为 #gid、href 清空——行退化为不可点的
                 div（href 为空时不渲染 <a>，避免点击原地刷新）。 -->
            <component
              :is="item.href ? 'a' : 'div'"
              v-for="(item, index) in topList"
              :key="item.gid ?? index"
              class="home__toplist-row"
              :href="item.href || undefined"
              :target="item.href ? '_blank' : undefined"
              :rel="item.href ? 'noopener' : undefined"
              :data-testid="`toplist-row-${index}`"
            >
              <span class="home__toplist-rank">{{ index + 1 }}</span>
              <span class="home__toplist-tag">{{ item.tag }}</span>
              <span class="home__toplist-value">{{ item.value }}</span>
            </component>
          </div>
          <!-- A4 定案（W3-F1）：与下载/历史页完全同构的全宽单列密信息行——共享
               AppListRow。用户定案（2026-09-07）：整行点击进 gallery 详情，
               「主体直达阅读」为下载页专属交互。宽屏（≥960px）双栏下两个点击
               分区都改为选中右栏（不跳路由，T3）；窄屏保持整页跳转。 -->
          <div v-else class="home__list" :class="{ 'home__list--bar': showPagination }">
            <AppListRow
              v-for="row in rows"
              :key="row.gallery.gid"
              :id="row.gallery.gid"
              :gid="row.gallery.gid"
              :title="displayTitle(row.gallery)"
              :subtitle="displaySubtitle(row.gallery)"
              :thumb="row.gallery.thumb"
              @open="(gid: number) => openDetail(gid, wide)"
              @read="(gid: number) => openDetail(gid, wide)"
            >
              <!-- 元信息行：分类 chip + 页数（元数据优先，对齐 GalleryCard）。 -->
              <template #meta>
                <CategoryChip v-if="row.chip" :category="row.chip" />
                <span v-if="row.gallery.pages > 0" class="home__row-pages">
                  {{ row.gallery.pages }}P
                </span>
              </template>
            </AppListRow>
          </div>
        </ContentLayout>

        <!-- FAB pair: primary = back to top (Android `v_go_to`), secondary =
             refresh. The cluster once expanded speed-dial style, but after the
             list/grid toggle retired with the waterfall (A4) only refresh
             remained — a single action behind an expand step is pure friction,
             so both FABs are permanently visible (FabLayout alwaysVisible).
             宽屏双栏下集群锚回左栏右下角（TwoPaneLayout :deep 规则）。 -->
        <FabLayout
          always-visible
          primary-icon="go-to-dark"
          :actions="fabActions"
          @click-primary="onPrimaryFab"
          @click-secondary="onSecondaryFab"
        />
      </template>

      <!-- 详情右栏：选中即原位加载（不跳路由，深链 /gallery/:gid 行为不变）；
           面板返回箭头 = 清除选中回到占位符。 -->
      <template #detail>
        <GalleryDetailPane
          :gid="selectedGid!"
          :token="selectedToken"
          pane
          @back="clearSelection"
        />
      </template>
    </TwoPaneLayout>

    <!-- Save-as-quick-search dialog (C3 wiring): minimal Android
         EditTextDialog replica — name the current filter state and POST the
         QuickSearchDto schema payload; failure shows inline in the dialog. -->
    <Teleport to="body">
      <div v-if="saveDialogOpen" class="dialog-scrim" @click.self="closeSaveQuickSearch">
        <div
          class="dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="save-quick-search-title"
        >
          <h3 id="save-quick-search-title" class="dialog__title">Save as quick search</h3>
          <input
            ref="saveNameInputRef"
            v-model="saveName"
            class="dialog__input"
            type="text"
            maxlength="50"
            placeholder="Preset name"
            autocomplete="off"
            @keydown.enter="saveQuickSearch"
          />
          <p v-if="saveError" class="dialog__error" role="alert">{{ saveError }}</p>
          <div class="dialog__actions">
            <button type="button" class="dialog__btn" @click="closeSaveQuickSearch">
              Cancel
            </button>
            <button
              type="button"
              class="dialog__btn dialog__btn--primary"
              :disabled="!saveName.trim() || savingQuickSearch"
              @click="saveQuickSearch"
            >
              {{ savingQuickSearch ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
/**
 * HomeView — the gallery list screen (S1), replicating Android `HomeScene` +
 * `GalleryListScene`: a floating SearchBar over the A4 single-column dense
 * info rows, pagination bar, pull-to-refresh, empty/error tips and the
 * bottom-right FabLayout cluster.
 *
 * A4 定案（W3-F1）：瀑布流/无限滚动出局——所有列表视图完全参照下载页逻辑，
 * 全宽单列密信息行（共享 `AppListRow`：缩略图→详情 / 主体→直接阅读 的行内
 * 点击分区）+ 分页导航（共享 `usePagedList` 状态机，整页替换渲染）。
 *
 * 数据源（W1-B1 翻转后）：空关键词 = 站点最新列表。上游（EH 站点列表）固定
 * 每页 25 条，服务端 `total = 结果页数 × 25`——usePagedList 的 1 起页码换算
 * 成上游 0 起 EH 页索引直传（offset 形态；条数档位只有 25 一档，分页条不做
 * 条数切换）。feed 模式（?feed=，frozen 契约）与搜索/筛选参数透传保持，仅
 * 形态切换；toplist 是无分页的排行行（tag/value/href），由独立 pagedList
 * 实例驱动。
 *
 * Composition (all frozen-contract components):
 * - `SearchBar`     — controlled state machine (normal → search → search-list);
 *                     quick searches load as suggestion rows, mirroring the
 *                     Android quick-search entries in the suggestion list.
 * - `FilterPanel`   — anchored PC filter popover (keywordMode + save-quick-
 *                     search wiring mirrors SearchView).
 * - `ContentLayout` — ViewTransition states (loading / content / empty /
 *                     error), pull-to-refresh header (v-model:refreshing),
 *                     sadpanda tip retry.
 * - `AppListRow`    — shared single-column row skeleton (W3-C1): thumb /
 *                     title / subtitle / meta slot; title 消费方先经
 *                     `maskedTitle` 脱敏（隐私红线）。
 * - `usePagedList`  — server pagination state machine (W3-C1): page window /
 *                     jump / stale guard; view states stay in this host via
 *                     onLoadStart/onSuccess/onError hooks.
 * - `FabLayout`     — primary go-to-top + secondary refresh.
 * - `TwoPaneLayout` — 平板对齐（T3，2026-09-20 定案）：宽屏（≥960px）列表
 *                     恒驻左栏（360px 固定宽）+ 详情右栏（GalleryDetailPane
 *                     原位加载，不跳路由）；无选中渲染占位符；窄屏单列行为
 *                     与现状一致。Esc/浏览器返回先清选中（拦截在布局组件）。
 *
 * KeepAlive（App.vue 列表缓存按 fullPath 分实例）：页码还原语义 = 页码
 * （currentPage 随组件实例存续）+ 页内滚动（滚动容器 DOM 随 KeepAlive 保留
 * scrollTop），从阅读器/详情返回即还原，无需额外逻辑。
 */
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { galleryApi, type FeedMode } from '@/api/gallery'
import { isOfflineError, isEhUnavailableError } from '@/api/client'
import { availability, loadAvailability, markDown } from '@/stores/availability'
import { usePagedList } from '@/composables/usePagedList'
import { useWebSocket } from '@/composables/useWebSocket'
import { syncInvalidation } from '@/stores/syncInvalidation'
import { maskedTitle, privacyMaskEnabled } from '@/utils/privacyMask'
import { isJpnSubtitleVisible } from '@/utils/jpnSubtitle'
import AvailabilityBanner from '@/components/common/AvailabilityBanner.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import CategoryChip from '@/components/atoms/CategoryChip.vue'
import AppListRow from '@/components/gallery/AppListRow.vue'
import GalleryDetailPane from '@/components/gallery/GalleryDetailPane.vue'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import TwoPaneLayout from '@/components/layout/TwoPaneLayout.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import SearchBar from '@/components/search/SearchBar.vue'
import FilterPanel from '@/components/search/FilterPanel.vue'
import type { SearchFilters } from '@/api/gallery'
import {
  filterChips,
  filtersToQuickSearchPayload,
  isFilterActive,
  removeFilterChip,
  type FilterChip,
} from '@/components/search/searchFilters'
import { useAuthStore } from '@/stores/auth'
import { usePreferencesStore } from '@/stores/preferences'
import {
  CATEGORY_BIT_VALUES,
  CATEGORY_BY_BIT,
  CATEGORY_LABELS,
  CATEGORY_ORDER,
  type ContentState,
  type FabAction,
  type GalleryCategory,
  type GalleryInfo,
  type NormalSearchMode,
  type SearchBarState,
  type SearchSuggestion,
} from '@/types/components'
import type { TopListItem } from '@/types'

/** ContentLayout's view states (frozen `ContentState` + its `error` extra). */
type HomeContentState = ContentState | 'error'

/**
 * 上游（EH 站点列表）固定每页 25 条（服务端 total = 结果页数 × 25）——分页
 * 档位只有这一档，分页条不提供条/页切换；fetchPage 里把 1 起页码换算成上游
 * 0 起 EH 页索引（page - 1）。
 */
const PAGE_SIZE = 25
const PAGE_SIZES = [PAGE_SIZE] as const

/** Keyword mode → QuickSearchDto mode number (SearchView 同款映射，W3 R4-10). */
const MODE_TO_NUM: Readonly<Record<NormalSearchMode, number>> = {
  normal: 0,
  subscription: 1,
  uploader: 2,
  tag: 3,
}

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

const contentState = ref<HomeContentState>('loading')
const refreshing = ref(false)
/** Error-state copy; switched to an offline tip when the SW reports 503 offline. */
const errorText = ref('加载失败，请稍后重试')
const contentLayoutRef = ref<InstanceType<typeof ContentLayout> | null>(null)

/* ------------------------- Wave-1 1a search filters ---------------------- */

const filterPanelOpen = ref(false)
const activeFilters = ref<SearchFilters>({})
const activeFilterChips = computed<FilterChip[]>(() => filterChips(activeFilters.value))

/** Keyword search mode (FilterPanel radio, C3 wiring — mirrors SearchView). */
const keywordMode = ref<NormalSearchMode>('normal')

/* --------------------------------- search ------------------------------- */

const searchState = ref<SearchBarState>('normal')
const keyword = ref('')
const appliedKeyword = ref('')

/**
 * 搜索请求关键词：keyword mode 的 `uploader:`/`tag:` 前缀在请求时合成
 * （SearchView `composedKeyword` 同款；subscription 与 normal 同义落回普通
 * 搜索），输入框内保持用户原文。
 */
function composedSearchKeyword(): string | undefined {
  const q = appliedKeyword.value
  if (!q) return undefined
  if (keywordMode.value === 'uploader') return `uploader:${q}`
  if (keywordMode.value === 'tag') return `tag:${q}`
  return q
}

/* ------------------------------ paged lists ------------------------------ */

/** Shared error shaping for both paged sources（EH 熔断 / 离线 / 通用）. */
function applyLoadError(error: unknown): void {
  if (isEhUnavailableError(error)) {
    // EH 熔断（§0）：列表端点不携带 HTTP 错误码（success:false + cause），
    // 由视图落 DOWN 标记 + 专属文案——服务器已短路，只读本地内容。
    markDown()
    errorText.value = 'EH 平台当前不可达，仅显示本地内容'
  } else {
    errorText.value = isOfflineError(error) ? '当前离线，且无本地缓存可用' : '加载失败，请稍后重试'
  }
}

/**
 * 分页状态机（W3-C1 usePagedList）：页码 / 跳页 / PC 页码窗口 / stale 竞态
 * 守卫。fetchPage 闭包读取当前 feed/关键词/筛选（重载时总是取最新值），
 * 把 1 起页码换算成搜索/ feed 端点的 0 起页参数（page - 1；上游固定
 * 25 条/页，条数档位单一）。视图四态机与错误文案留在本视图，经 onLoadStart
 * / onSuccess / onError 钩子接线。
 *
 * PC 键盘翻页不交给 composable 的 keyboardPaging（它无条件挂 window）——
 * toplist 模式无分页，键盘处理留在本视图按模式豁免（见 onPageKey）。
 */
const {
  items: galleries,
  total,
  currentPage,
  totalPages,
  paginationVisible,
  pageWindow,
  load: loadGalleryPage,
  jumpToPage,
} = usePagedList<GalleryInfo>({
  fetchPage: async (page, size) => {
    // Feed mode drives the data source; the search keyword is ignored there
    // (frozen feed contract — subscription/popular mirror search's envelope).
    const feed = feedMode.value
    if (feed && feed !== 'toplist') {
      const res = await galleryApi.feed(feed, page - 1, size)
      return { items: res.data, total: res.total }
    }
    const res = await galleryApi.search(
      composedSearchKeyword(),
      undefined,
      page - 1,
      size,
      isFilterActive(activeFilters.value) ? activeFilters.value : undefined,
    )
    return { items: res.data, total: res.total }
  },
  pageSizes: PAGE_SIZES,
  initialPageSize: PAGE_SIZE,
  fallbackPageSize: PAGE_SIZE,
  onLoadStart: () => {
    contentState.value = 'loading'
  },
  onSuccess: (result) => {
    contentState.value = result.items.length > 0 ? 'content' : 'empty'
    contentLayoutRef.value?.scrollToTop()
  },
  onError: (error) => {
    console.error('[HomeView] 加载画廊列表失败', error)
    applyLoadError(error)
    // 首屏失败（无内容）→ 错误态（retry 重新加载）；翻页失败保留已载内容。
    if (galleries.value.length === 0) contentState.value = 'error'
  },
})

/**
 * Toplist 独立分页实例：排行行（tag/value/href）不是 GalleryInfo，且上游
 * toplist 无分页语义（total = 返回行数 → 分页条永不出现），只借状态机的
 * stale 守卫与钩子接线。键盘翻页不启用。
 */
const {
  items: topList,
  load: loadToplistPage,
} = usePagedList<TopListItem>({
  fetchPage: async (page, size) => {
    const res = await galleryApi.feed('toplist', page - 1, size)
    return { items: res.data, total: res.total }
  },
  pageSizes: PAGE_SIZES,
  initialPageSize: PAGE_SIZE,
  fallbackPageSize: PAGE_SIZE,
  onLoadStart: () => {
    contentState.value = 'loading'
  },
  onSuccess: (result) => {
    contentState.value = result.items.length > 0 ? 'content' : 'empty'
    contentLayoutRef.value?.scrollToTop()
  },
  onError: (error) => {
    console.error('[HomeView] 加载排行榜失败', error)
    applyLoadError(error)
    if (topList.value.length === 0) contentState.value = 'error'
  },
})

/** 视图层加载入口：按当前模式选择数据源，回第 1 页。`silent: true` 不切
 *  loading 态（WS 重连后的静默对账重载用）。 */
function loadFirstPage(opts?: { silent?: boolean }): Promise<void> {
  return feedMode.value === 'toplist'
    ? loadToplistPage(1, opts)
    : loadGalleryPage(1, opts)
}

/** Pull-to-refresh / error retry / empty tip retry / FAB refresh. */
async function onRefresh(): Promise<void> {
  const hasContent =
    feedMode.value === 'toplist' ? topList.value.length > 0 : galleries.value.length > 0
  if (contentState.value === 'content' && hasContent) {
    // Keep the list visible under the parked refresh header (Android
    // RefreshLayout behavior); silent reload keeps the state machine off the
    // loading tip, onSuccess snaps back to the top.
    refreshing.value = true
    try {
      await (feedMode.value === 'toplist'
        ? loadToplistPage(1, { silent: true })
        : loadGalleryPage(1, { silent: true }))
    } finally {
      refreshing.value = false
    }
  } else {
    refreshing.value = false
    contentState.value = 'loading'
    await loadFirstPage()
  }
}

/** PC 键盘翻页（PageUp/PageDown，PC 端惯例）——INPUT/SELECT/TEXTAREA 聚焦
 *  豁免；toplist 模式无分页，整体豁免（不发给画廊实例的静默翻页请求）；
 *  KeepAlive 停用态整体豁免（监听器随实例常驻，不能后台劫持按键，audit P1-5 同类）。 */
function onPageKey(event: KeyboardEvent): void {
  if (feedMode.value === 'toplist' || !viewActive) return
  const target = event.target as HTMLElement | null
  if (
    target &&
    (target.tagName === 'INPUT' || target.tagName === 'SELECT' || target.tagName === 'TEXTAREA')
  ) {
    return
  }
  if (event.key === 'PageDown') {
    event.preventDefault()
    if (currentPage.value < totalPages.value) jumpToPage(currentPage.value + 1)
  } else if (event.key === 'PageUp') {
    event.preventDefault()
    if (currentPage.value > 1) jumpToPage(currentPage.value - 1)
  }
}

/** 分页条可见性：total 超过一页才显示；toplist 无上游分页，不渲染。 */
const showPagination = computed(() => feedMode.value !== 'toplist' && paginationVisible.value)

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
  // A5-1：/admin 并入 /settings 分组——EH 会话页现居 /settings/server/eh。
  void router.push('/settings/server/eh')
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

/* ------------------------- search filters wiring ------------------------- */

/** 筛选即时搜索防抖（C6）：连续勾选 N 个分类只发一次请求。 */
let filterDebounceTimer: ReturnType<typeof setTimeout> | undefined

function applyFilters(next: SearchFilters): void {
  activeFilters.value = next
  if (filterDebounceTimer) clearTimeout(filterDebounceTimer)
  filterDebounceTimer = setTimeout(() => {
    filterDebounceTimer = undefined
    void applySearch(keyword.value)
  }, 500)
}

function onRemoveFilterChip(chipId: string): void {
  applyFilters(removeFilterChip(activeFilters.value, chipId))
}

function onClearFilters(): void {
  applyFilters({})
}

function onFilterPanelSearch(): void {
  filterPanelOpen.value = false
  // 显式 Search 动作即时执行，不等待筛选防抖时钟。
  if (filterDebounceTimer) {
    clearTimeout(filterDebounceTimer)
    filterDebounceTimer = undefined
  }
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

/** IME action / programmatic search — reload page 1 with the new keyword. */
function applySearch(query: string): void {
  const q = query.trim()
  // Frozen feed 契约：feed 分支忽略关键词——静默丢词是零反馈失败（C2）。改为
  // router.replace 到 `/?keyword=` 深链形态：离开 feed 态、意图可见，并由
  // 下方 keyword watcher 真正执行搜索。
  if (feedMode.value) {
    void router.replace({ path: '/', query: q ? { keyword: q } : {} })
    return
  }
  commitKeyword(q)
}

/** applySearch 与 `?keyword=` 深链共用的落库 + 重载。 */
function commitKeyword(q: string): void {
  keyword.value = q
  appliedKeyword.value = q
  searchState.value = 'normal'
  void loadGalleryPage(1)
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

/* ---------------------- save-as-quick-search (C3 wiring) ------------------ */

const saveDialogOpen = ref(false)
const saveName = ref('')
const saveError = ref('')
const savingQuickSearch = ref(false)
const saveNameInputRef = ref<HTMLInputElement | null>(null)

function openSaveQuickSearch(): void {
  saveName.value = ''
  saveError.value = ''
  saveDialogOpen.value = true
  void nextTick(() => saveNameInputRef.value?.focus())
}

function closeSaveQuickSearch(): void {
  saveDialogOpen.value = false
}

/** Esc 统一挂 window（C7）：焦点不在面板内时也能关闭；组合中不误关。 */
function onSaveDialogKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && !event.isComposing) closeSaveQuickSearch()
}

watch(saveDialogOpen, (open) => {
  if (open) {
    window.addEventListener('keydown', onSaveDialogKeydown)
  } else {
    window.removeEventListener('keydown', onSaveDialogKeydown)
  }
})

/**
 * POST the QuickSearchDto-schema payload (SearchView `saveQuickSearch` 同款,
 * minus the offline localStorage fallback — HomeView 的 suggestion 列表只吃
 * 服务器预设)。成功后新预设立刻作为 suggestion 行出现。
 */
async function saveQuickSearch(): Promise<void> {
  const name = saveName.value.trim()
  if (!name || savingQuickSearch.value) return
  savingQuickSearch.value = true
  saveError.value = ''
  try {
    const created = await galleryApi.createQuickSearch(
      filtersToQuickSearchPayload(activeFilters.value, {
        name,
        keyword: keyword.value,
        mode: MODE_TO_NUM[keywordMode.value],
      }),
    )
    suggestions.value = [
      ...suggestions.value,
      { text: created.name, hint: created.keyword || undefined },
    ]
    saveDialogOpen.value = false
  } catch (error) {
    console.error('[HomeView] quick-search POST failed', error)
    saveError.value = '保存失败，请稍后重试'
  } finally {
    savingQuickSearch.value = false
  }
}

/* ---------------------------------- FABs -------------------------------- */

/** The list/grid toggle retired with the waterfall (A4) — refresh only. */
const fabActions: FabAction[] = [{ id: 'refresh', icon: 'refresh-dark', label: '刷新列表' }]

function onPrimaryFab(): void {
  contentLayoutRef.value?.scrollToTop()
}

function onSecondaryFab(action: FabAction): void {
  if (action.id === 'refresh') {
    void onRefresh()
  }
}

/* ----------------------------- row presentation -------------------------- */

/** 行展示标题——脱敏在本视图完成（隐私红线：标题必经 maskedTitle）。 */
function displayTitle(gallery: GalleryInfo): string {
  return maskedTitle(gallery.title || gallery.titleJpn || `#${gallery.gid}`, gallery.gid)
}

/**
 * 日文副题：打码开启时一并隐藏（同 GalleryCard 的标题日文行守卫）；
 * showJpnTitle 加载后为 false 也隐藏（协议默认 false，prefs 未加载按显示
 * 渲染防闪失——T2 定案，判定收敛在 utils/jpnSubtitle）。
 */
function displaySubtitle(gallery: GalleryInfo): string | null {
  if (privacyMaskEnabled.value || !gallery.titleJpn) return null
  return isJpnSubtitleVisible(preferencesStore.prefs?.general) ? gallery.titleJpn : null
}

/* -------------------------------- category ------------------------------ */

/** Unknown category fallback — Android `SiteUtils.UNKNOWN` bit. */
const CATEGORY_UNKNOWN_BIT = 0x400

/** Category string → bit lookup (labels + keys), mirrors HistoryView. */
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

/** Numeric category bit → `GalleryCategory` key (undefined when unknown). */
function categoryKeyOf(gallery: GalleryInfo): GalleryCategory | undefined {
  return CATEGORY_BY_BIT[categoryBit(gallery.category)]
}

/** Per-row view models: gallery + pre-resolved chip key (v-if narrows it). */
const rows = computed(() =>
  galleries.value.map((gallery) => ({ gallery, chip: categoryKeyOf(gallery) })),
)

/* --------------------------------- routing ------------------------------- */

/**
 * 双栏右栏选中态（T3，内存组件态，不持久化）：宽屏点选列表项 → 右栏原位
 * 加载该画廊详情（不跳路由；同项再点不重载不闪烁）；跨 960px 阈值来回
 * 选中保留（窄屏仅不渲染右栏，回宽即恢复）。深链 `/gallery/:gid` 整页详情
 * 行为不受影响。
 */
const selectedGid = ref<number | null>(null)
const selectedToken = ref<string | undefined>(undefined)

/** 选中列表项（携带行内 token，供 getDetail 上游直取）。 */
function selectGallery(gid: number): void {
  const gallery = galleries.value.find((g) => g.gid === gid)
  selectedGid.value = gid
  selectedToken.value = gallery?.token || undefined
}

/** 清除选中（右栏返回箭头 / Esc / 宽屏浏览器返回首次触发）。 */
function clearSelection(): void {
  selectedGid.value = null
  selectedToken.value = undefined
}

/** 缩略图/主体点击：宽屏双栏 = 选中右栏（不跳路由）；窄屏 = 详情页（本地
 *  token 透传，现状行为）。 */
function openDetail(gid: number, wide: boolean): void {
  if (wide) {
    selectGallery(gid)
    return
  }
  const gallery = galleries.value.find((g) => g.gid === gid)
  void router.push({
    path: `/gallery/${gid}`,
    query: gallery?.token ? { token: gallery.token } : {},
  })
}

/* --------------------------------- lifecycle ---------------------------- */

/* ------------------------------------ 实时对账（WP8 任务 2） -------------- */

/** 本视图只消费连接态（connect 由 App 壳持有——只解构 connected 不 connect）。 */
const { connected } = useWebSocket()

// /topic/sync 广播 quickSearches 实体变更 → 作废已加载标记并静默重拉建议
// 列表；watch 默认非 immediate——首帧不触发。WS 重连成功（断线窗口内可能
// 漏帧）→ 静默重载当前模式当前页。
watch(() => syncInvalidation.consume('quickSearches'), () => {
  quickSearchesLoaded = false
  void loadQuickSearches()
})
watch(connected, (up, was) => {
  if (up && was === false) void loadFirstPage({ silent: true })
})

onMounted(() => {
  // Warm the preferences store early: HistoryView rows (read-progress badge)
  // consume `general.showReadProgress` but do not kick a load themselves, and
  // the store would otherwise stay unloaded until some view needs a live pref.
  if (!preferencesStore.prefs && !preferencesStore.loading) {
    void preferencesStore.load()
  }
  // Probe the EH session once so the empty-state hint can guide users whose
  // Android-login session hasn't synced yet (or has expired).
  void checkEhSession()
  // 读取服务器侧 EH 熔断状态（幂等：in-flight 单飞）——DOWN 时顶部提示条
  // 与自动请求短路（服务器/拦截器）同时生效。
  void loadAvailability()
  // `?keyword=` 深链（详情页 tag/uploader 链接等，C2）：首载即执行该搜索，
  // 复用下方唯一的 loadFirstPage() 入口。
  const initialKeyword =
    typeof route.query.keyword === 'string' ? route.query.keyword.trim() : ''
  if (initialKeyword) {
    keyword.value = initialKeyword
    appliedKeyword.value = initialKeyword
  }
  void loadFirstPage()
  window.addEventListener('keydown', onPageKey)
})

/**
 * Feed navigation (/?feed=popular → /?feed=toplist, or a feed → the plain
 * home) reuses this component instance — reload page 1 from the new source
 * when the query changes (the paged lists' stale guards drop any in-flight
 * page of the previous mode).
 *
 * C2：feed 被搜索替换时（applySearch 的 router.replace 同时去掉 feed、带上
 * keyword）不在此处重载——若 keyword watcher 会执行该搜索（词变化）就交给它；
 * 若词未变（watcher 会跳过）则此处仍以普通搜索语义重载，避免「离开 feed
 * 却什么都没发生」。
 *
 * KeepAlive（App.vue 列表缓存）：fullPath 变化即换实例（App 按 key 区分），
 * 这两个 route watcher 在存活期内的真实导航中只会于「本实例已被停用」的
 * 窗口触发——停用态一律跳过，防止后台误改缓存实例的状态；重新激活时
 * fullPath 必等于本实例的 key，状态天然一致，无需补跑。
 */
let viewActive = true
onActivated(() => {
  viewActive = true
  // KeepAlive 停用守卫（audit P2，W1-F3/P1-5 同类）：保存对话框开着离开时，
  // Escape 监听已在停用时摘除——重新激活且对话框仍开着则摘后重挂
  // （remove-before-add，对齐 SearchView P1-5 模式）。
  if (saveDialogOpen.value) {
    window.removeEventListener('keydown', onSaveDialogKeydown)
    window.addEventListener('keydown', onSaveDialogKeydown)
  }
})
onDeactivated(() => {
  viewActive = false
  // 停用即摘 Escape 监听、作废筛选即时搜索的防抖时钟（重新勾选会重排）；
  // 卸载清理由下方 onBeforeUnmount 兜底（缓存淘汰不经过 deactivated）。
  if (filterDebounceTimer) clearTimeout(filterDebounceTimer)
  window.removeEventListener('keydown', onSaveDialogKeydown)
})

watch(
  () => route.query.feed,
  () => {
    if (!viewActive) return
    const keywordParam = route.query.keyword
    if (typeof keywordParam === 'string' && keywordParam.trim() !== appliedKeyword.value) return
    void loadFirstPage()
  },
)

/**
 * `?keyword=` 深链消费（C2）：详情页 tag/uploader 链接、feed 页搜索都落到
 * 这里——词变化时提交一次全量搜索。
 */
watch(
  () => route.query.keyword,
  (next) => {
    if (!viewActive) return
    const q = typeof next === 'string' ? next.trim() : ''
    if (!q || q === appliedKeyword.value) return
    commitKeyword(q)
  },
)

onBeforeUnmount(() => {
  if (filterDebounceTimer) clearTimeout(filterDebounceTimer)
  window.removeEventListener('keydown', onPageKey)
  window.removeEventListener('keydown', onSaveDialogKeydown)
})
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

/* 汉堡可见视口（<720px，或横屏矮视口）：浮动搜索条避让左上角汉堡；
   列表内容全宽，从汉堡下穿过（FAB 语义）。 */
@media (max-width: 719px), (min-width: 720px) and (max-height: 479.98px) {
  .home__searchbar {
    padding-left: var(--hamburger-clearance);
  }
}

.home__content {
  flex: 1 1 auto;
  min-height: 0;
}

/* 分页条（A4）：固定在滚动区上方，吃浮动搜索条清理位（同下载/历史页的
   常驻分页导航形态）。双类选择器压过 .pagination-bar 基类的 padding
   简写——同权重后序规则会把 64px 清理位覆盖成 6px，首页首行被浮动
   搜索栏遮住正是这个根因。 */
.pagination-bar.home__pagination {
  flex-shrink: 0;
  padding-top: calc(
    var(--gallery-padding-top-search-bar) + var(--availability-offset, 0px)
  );
}

/* Single-column dense rows (A4): the row skeleton (card surface / thumb /
   title) lives in the shared AppListRow. 无分页条时行自己清理浮动搜索条；
   分页条可见时清理位已由上方分页条占用。 */
.home__list {
  padding: calc(var(--gallery-padding-top-search-bar) + var(--availability-offset, 0px))
    var(--gallery-list-margin-h) var(--gallery-padding-bottom-fab);
}

.home__list--bar {
  padding-top: 0;
}

/* W6 元信息行：页数角标——12sp secondary，跟在分类 chip 之后。 */
.home__row-pages {
  flex-shrink: 0;
  font-size: var(--text-super-small); /* 12sp */
  color: var(--text-color-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
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

/* ------------------------------------------------------- pagination bar --- */
/* 与 DownloadView/HistoryView 分页条同构复刻（A4）；条/页切换不适用
   （上游固定 25 条/页），故无 __size/__select 一族。 */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing);
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

/* ------------------------------------------------------------- dialog --- */
/* Save-as-quick-search dialog（C3）：HistoryView/DownloadView 的 AlertDialog
   复刻样式同款。 */
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

.dialog__btn--primary {
  color: var(--color-primary);
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
