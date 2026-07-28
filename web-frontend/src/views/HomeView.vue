<template>
  <div class="home">
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
        @update:query="keyword = $event"
        @search="applySearch"
        @click-title="enterSearchMode"
        @click-action="enterSearchMode"
        @back="leaveSearchMode"
        @select-suggestion="onSelectSuggestion"
        @dismiss-suggestion="onDismissSuggestion"
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
      error-text="加载失败，请稍后重试"
      @update:refreshing="refreshing = $event"
      @refresh="onRefresh"
      @load-more="onLoadMore"
      @retry="onRefresh"
    >
      <GalleryGrid :items="galleries" :mode="viewMode" @select="openGallery" />
    </ContentLayout>

    <!-- FAB cluster: primary = back to top (Android `v_go_to`), secondaries =
         list/grid toggle + refresh. Starts collapsed like the Android scene. -->
    <FabLayout
      v-model:expanded="fabExpanded"
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
 * - `GalleryGrid` — list/grid rendering; column count is width-derived, never
 *                   hardcoded (contracts/responsive-strategy.md §4).
 * - `FabLayout`   — primary go-to-top + secondary mode-toggle / refresh.
 *
 * The list/grid mode persists to localStorage (web equivalent of the Android
 * `Settings` list-mode preference).
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { galleryApi } from '@/api/gallery'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import SearchBar from '@/components/search/SearchBar.vue'
import GalleryGrid from '@/components/gallery/GalleryGrid.vue'
import type {
  ContentState,
  FabAction,
  GalleryInfo,
  SearchBarState,
  SearchSuggestion,
} from '@/types/components'

/** Layout modes of the gallery list (Android `Settings` list mode). */
type ListMode = 'list' | 'grid'

/** ContentLayout's view states (frozen `ContentState` + its `error` extra). */
type HomeContentState = ContentState | 'error'

/** EhViewer's default gallery page size. */
const PAGE_SIZE = 25

/** localStorage key for the persisted list mode. */
const LIST_MODE_KEY = 'ehviewer-webui:gallery-list-mode'

const router = useRouter()

/* -------------------------------- list state ---------------------------- */

const galleries = ref<GalleryInfo[]>([])
const page = ref(0)
const total = ref(0)
const contentState = ref<HomeContentState>('loading')
const refreshing = ref(false)
const loadingMore = ref(false)
const contentLayoutRef = ref<InstanceType<typeof ContentLayout> | null>(null)

/** Monotonic request guard — stale responses (fast refresh / search) drop. */
let requestSeq = 0

async function loadPage(target: number, mode: 'replace' | 'append'): Promise<void> {
  const seq = ++requestSeq
  if (mode === 'append') loadingMore.value = true
  try {
    const res = await galleryApi.search(
      appliedKeyword.value || undefined,
      undefined,
      target,
      PAGE_SIZE,
    )
    if (seq !== requestSeq) return
    page.value = target
    if (mode === 'replace') {
      galleries.value = res.data
    } else {
      // Dedupe by gid — bumped galleries can reappear across pages.
      const seen = new Set(galleries.value.map((g) => g.gid))
      galleries.value = [...galleries.value, ...res.data.filter((g) => !seen.has(g.gid))]
    }
    total.value = res.total
    contentState.value = galleries.value.length > 0 ? 'content' : 'empty'
  } catch (error) {
    if (seq !== requestSeq) return
    console.error('[HomeView] 加载画廊列表失败', error)
    // Append failures keep the loaded content; replace failures show the
    // error tip (retry button re-triggers a refresh).
    if (mode === 'replace') contentState.value = 'error'
  } finally {
    if (seq === requestSeq && mode === 'append') loadingMore.value = false
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
  if (galleries.value.length >= total.value) return
  void loadPage(page.value + 1, 'append')
}

function openGallery(gallery: GalleryInfo): void {
  void router.push(`/gallery/${gallery.gid}`)
}

/* ------------------------------ list/grid mode -------------------------- */

function readStoredMode(): ListMode {
  try {
    return localStorage.getItem(LIST_MODE_KEY) === 'grid' ? 'grid' : 'list'
  } catch {
    return 'list'
  }
}

const viewMode = ref<ListMode>(readStoredMode())

watch(viewMode, (mode) => {
  try {
    localStorage.setItem(LIST_MODE_KEY, mode)
  } catch {
    /* Storage unavailable (e.g. private mode) — mode simply won't persist. */
  }
})

/* --------------------------------- search ------------------------------- */

const searchState = ref<SearchBarState>('normal')
const keyword = ref('')
const appliedKeyword = ref('')
const suggestions = ref<SearchSuggestion[]>([])
let quickSearchesLoaded = false

/** Title row shows the active keyword, falling back to the site name. */
const searchTitle = computed(() => appliedKeyword.value || 'E-hentai')

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

/** Cluster starts collapsed, like the Android scene (tap primary to open). */
const fabExpanded = ref(false)

const fabActions = computed<FabAction[]>(() => [
  {
    id: 'toggle-mode',
    icon: viewMode.value === 'list' ? 'top-lists' : 'reorder',
    label: viewMode.value === 'list' ? '切换到网格视图' : '切换到列表视图',
  },
  { id: 'refresh', icon: 'refresh-dark', label: '刷新列表' },
])

function onPrimaryFab(): void {
  contentLayoutRef.value?.scrollToTop()
}

function onSecondaryFab(action: FabAction): void {
  // Android collapses the cluster after a secondary action (the backdrop
  // would otherwise keep the freshly re-laid-out list dimmed).
  fabExpanded.value = false
  if (action.id === 'toggle-mode') {
    viewMode.value = viewMode.value === 'list' ? 'grid' : 'list'
  } else if (action.id === 'refresh') {
    void onRefresh()
  }
}

/* --------------------------------- lifecycle ---------------------------- */

onMounted(() => {
  void loadPage(0, 'replace')
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
.home__searchbar {
  position: absolute;
  /* Offset below the status bar / cutout; the list clears the bar via
     --gallery-padding-top-search-bar, which carries the same inset. */
  top: var(--safe-area-top);
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

.home__content {
  flex: 1 1 auto;
  min-height: 0;
}
</style>
