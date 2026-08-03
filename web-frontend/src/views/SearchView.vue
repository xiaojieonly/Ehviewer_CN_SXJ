<!--
  SearchView.vue — dedicated search scene (S5), web equivalent of Android
  `GalleryListScene` in search mode.

  Composition (all frozen-contract components):
    NavigationDrawer — global nav (modal < 720px, persistent ≥ 720px);
    SearchBar        — floating bar with the normal → search → search-list
                       state machine; suggestions = search history +
                       quick-search presets (long-press deletes history);
    SearchLayout     — expandable filter panel (embeds CategoryTable,
                       keyword-mode radio group, AdvanceSearchTable replica);
    ContentLayout    — results with pull-to-refresh + infinite paging;
    GalleryCard      — gallery card in list/grid mode (auto-column grid per
                       contracts/responsive-strategy.md §4);
    FabLayout        — primary FAB opens the filter panel, secondary FABs
                       manage quick searches and toggle list/grid.

  Query composition mirrors Android `formatListUrlBuilder`:
    - categories use POSITIVE semantics here and are converted to the
      SiteConfig EXCLUSION bitmask (CATEGORY_BIT_VALUES) for the API;
    - uploader / tag keyword modes prefix the keyword (`uploader:` / `tag:`),
      the canonical AnotherViewer URL syntax;
    - advanced options (bitmask / min rating / page range) round-trip through
      quick-search presets — the current REST endpoint does not accept them.

  Persistence (client-side):
    anotherviewer-search-history   — recent keyword searches (max 20);
    anotherviewer-quick-searches   — user presets (seeded from GET /gallery/quick-search);
    anotherviewer-search-view-mode — results layout ('grid' | 'list').
-->
<template>
  <div class="search-scene">
    <NavigationDrawer
      v-model:open="drawerOpen"
      :items="DEFAULT_NAV_ITEMS"
      :active-item-id="'homepage'"
      :username="authStore.username ?? undefined"
      :theme="themeStore.currentTheme"
      @select="onNavSelect"
      @toggle-theme="themeStore.toggleTheme()"
    />

    <div class="search-scene__main">
      <!-- Floating search bar — Android `widget_search_bar.xml` replica. -->
      <SearchBar
        :state="searchBarState"
        :title="searchTitle"
        :query="query"
        hint="Search galleries"
        left-icon="reorder"
        right-icon="magnify-dark"
        :suggestions="suggestions"
        @update:state="searchBarState = $event"
        @update:query="onQueryInput"
        @search="commitSearch"
        @click-menu="drawerOpen = true"
        @click-action="commitSearch(query)"
        @click-title="openSearch"
        @select-suggestion="onSelectSuggestion"
        @dismiss-suggestion="onDismissSuggestion"
        @back="closeSearch"
      />

      <!-- Expandable filter panel (CategoryTable + modes + advanced). -->
      <SearchLayout
        v-model:mode="searchMode"
        v-model:enable-advance="enableAdvance"
        v-model:selected-categories="selectedCategories"
        v-model:normal-search-mode="normalSearchMode"
        v-model:advance-options="advanceOptions"
        v-model:expanded="panelExpanded"
        :keyword="query"
        @change-mode="toggleSearchMode"
        @select-image="showSnack('Image search is not available on this server yet')"
        @open-tag-selector="showSnack('Tag selector is not available yet')"
        @search="onPanelSearch"
      />

      <!-- Results meta bar: total + page + list/grid toggle. -->
      <div v-if="contentState === 'content'" class="results-bar">
        <span class="results-bar__meta">
          {{ total }} {{ total === 1 ? 'gallery' : 'galleries' }} · page {{ page + 1 }}
        </span>
        <div class="results-bar__modes" role="radiogroup" aria-label="Results layout">
          <button
            type="button"
            class="results-bar__mode"
            role="radio"
            :aria-checked="viewMode === 'grid'"
            aria-label="Grid view"
            @click="setViewMode('grid')"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z" />
            </svg>
          </button>
          <button
            type="button"
            class="results-bar__mode"
            role="radio"
            :aria-checked="viewMode === 'list'"
            aria-label="List view"
            @click="setViewMode('list')"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M3 5h18v3H3zm0 5.5h18v3H3zM3 16h18v3H3z" />
            </svg>
          </button>
        </div>
      </div>

      <!-- Results: pull-to-refresh + infinite paging via ContentLayout. -->
      <ContentLayout
        ref="contentRef"
        class="search-scene__content"
        :state="contentState"
        :refreshing="refreshing"
        :loading-more="loadingMore"
        empty-text="No galleries found — adjust the filters and try again"
        error-text="Search failed — check the connection and retry"
        @update:refreshing="refreshing = $event"
        @refresh="onRefresh"
        @load-more="onLoadMore"
        @retry="onRefresh"
      >
        <div class="results" :class="`results--${viewMode}`">
          <GalleryCard
            v-for="gallery in galleries"
            :key="gallery.gid"
            :gallery="gallery"
            :mode="viewMode"
            @click="openGallery"
          />
        </div>
      </ContentLayout>

      <!-- FAB cluster: primary opens filters, secondaries manage presets. -->
      <FabLayout
        v-model:expanded="fabExpanded"
        primary-icon="magnify-dark"
        :actions="fabActions"
        @click-primary="onFabPrimary"
        @click-secondary="onFabSecondary"
      />
    </div>

    <!-- Quick-search management dialog. -->
    <Transition name="dialog">
      <div
        v-if="dialog === 'manage'"
        class="dialog-scrim"
        @click.self="dialog = 'none'"
      >
        <div class="dialog" role="dialog" aria-modal="true" aria-label="Quick searches">
          <h2 class="dialog__title">Quick searches</h2>
          <ul v-if="quickSearches.length" class="qs-list">
            <li v-for="preset in quickSearches" :key="preset.id" class="qs-item">
              <button type="button" class="qs-item__main" @click="loadQuickSearch(preset)">
                <span class="qs-item__name">{{ preset.name }}</span>
                <span class="qs-item__summary">
                  {{ preset.keyword || '(no keyword)' }} · {{ modeLabel(preset.mode) }}
                </span>
              </button>
              <button
                type="button"
                class="qs-item__delete"
                :aria-label="`Delete ${preset.name}`"
                @click="deleteQuickSearch(preset.id)"
              >
                <AppIcon name="delete-dark" size="20px" />
              </button>
            </li>
          </ul>
          <p v-else class="dialog__empty">
            No presets yet — configure the filters and tap “Save quick search”.
          </p>
          <div class="dialog__actions">
            <button type="button" class="btn-text" @click="openSaveDialog">
              Save current…
            </button>
            <button type="button" class="btn-text" @click="dialog = 'none'">Close</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Save-preset dialog. -->
    <Transition name="dialog">
      <div
        v-if="dialog === 'save'"
        class="dialog-scrim"
        @click.self="dialog = 'none'"
      >
        <div class="dialog" role="dialog" aria-modal="true" aria-label="Save quick search">
          <h2 class="dialog__title">Save quick search</h2>
          <p class="dialog__summary">
            {{ query.trim() || '(no keyword)' }} · {{ modeLabel(MODE_TO_NUM[normalSearchMode]) }}
            · {{ selectedCategories.length }}/10 categories
            <template v-if="enableAdvance"> · advanced</template>
          </p>
          <label class="field">
            <input
              v-model="presetName"
              type="text"
              placeholder=" "
              maxlength="40"
              @keydown.enter.prevent="saveQuickSearch"
            />
            <span class="field__label">Preset name</span>
          </label>
          <div class="dialog__actions">
            <button type="button" class="btn-text" @click="dialog = 'none'">Cancel</button>
            <button type="button" class="btn-primary" @click="saveQuickSearch">Save</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type {
  AdvanceSearchOptions,
  FabAction,
  GalleryCategory,
  GalleryInfo,
  NavItem,
  NormalSearchMode,
  SearchBarState,
  SearchMode,
  SearchSuggestion,
} from '@/types/components'
import { CATEGORY_BIT_VALUES, CATEGORY_ORDER } from '@/types/components'
import type { QuickSearch } from '@/types'
import { galleryApi } from '@/api/gallery'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import NavigationDrawer, { DEFAULT_NAV_ITEMS } from '@/components/layout/NavigationDrawer.vue'
import SearchBar from '@/components/search/SearchBar.vue'
import SearchLayout from '@/components/search/SearchLayout.vue'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import GalleryCard from '@/components/gallery/GalleryCard.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

/* ------------------------------- constants ------------------------------ */

const PAGE_SIZE = 25
const HISTORY_KEY = 'anotherviewer-search-history'
const QUICK_SEARCH_KEY = 'anotherviewer-quick-searches'
const VIEW_MODE_KEY = 'anotherviewer-search-view-mode'
const MAX_HISTORY = 20

/** QuickSearch.mode numbering (Android `QuickSearch` / ListUrlBuilder modes). */
const MODE_TO_NUM: Readonly<Record<NormalSearchMode, number>> = {
  normal: 0,
  subscription: 1,
  uploader: 2,
  tag: 3,
}
const NUM_TO_MODE: Readonly<Record<number, NormalSearchMode>> = {
  0: 'normal',
  1: 'subscription',
  2: 'uploader',
  3: 'tag',
}
const MODE_LABELS: Readonly<Record<NormalSearchMode, string>> = {
  normal: 'Search',
  subscription: 'Subscription',
  uploader: 'Uploader',
  tag: 'Tag',
}

/** Drawer item id → route (items without a screen yet fall back to home). */
const NAV_ROUTES: Readonly<Record<string, string>> = {
  homepage: '/',
  favourite: '/favorites',
  history: '/history',
  downloads: '/downloads',
  settings: '/settings',
}

/* --------------------------------- state -------------------------------- */

const drawerOpen = ref(false)

// SearchBar state machine (Android SearchBar.STATE_*).
const searchBarState = ref<SearchBarState>('normal')
const searchTitle = ref('Search')
const query = ref('')
/** Keyword of the last committed search (drives the query sent to the API). */
const activeQuery = ref('')

// SearchLayout filter state (all v-model'd).
const searchMode = ref<SearchMode>('normal')
const panelExpanded = ref(true)
const enableAdvance = ref(false)
const selectedCategories = ref<GalleryCategory[]>([...CATEGORY_ORDER])
const normalSearchMode = ref<NormalSearchMode>('normal')
const advanceOptions = ref<AdvanceSearchOptions>({
  advanceSearch: 0,
  minRating: 0,
  pageFrom: 0,
  pageTo: 0,
})

// Results.
const contentRef = ref<InstanceType<typeof ContentLayout> | null>(null)
const contentState = ref<'loading' | 'content' | 'empty' | 'error'>('loading')
const galleries = ref<GalleryInfo[]>([])
const total = ref(0)
const page = ref(0)
const refreshing = ref(false)
const loadingMore = ref(false)
/** Monotonic request guard — stale responses (fast successive searches /
    pull-to-refresh) are discarded. */
let requestSeq = 0
const viewMode = ref<'grid' | 'list'>(readStorage<'grid' | 'list'>(VIEW_MODE_KEY) ?? 'grid')

// History + quick searches.
const history = ref<string[]>(readStorage<string[]>(HISTORY_KEY) ?? [])
const quickSearches = ref<QuickSearch[]>(readStorage<QuickSearch[]>(QUICK_SEARCH_KEY) ?? [])

// Dialogs + snackbar.
const dialog = ref<'none' | 'manage' | 'save'>('none')
const presetName = ref('')
const snack = ref('')
let snackTimer: number | undefined

const fabExpanded = ref(false)
const fabActions = computed<FabAction[]>(() => [
  {
    id: 'toggle-view',
    icon: viewMode.value === 'grid' ? 'reorder' : 'book-open',
    label: viewMode.value === 'grid' ? 'Switch to list view' : 'Switch to grid view',
  },
  { id: 'save-quick', icon: 'plus-dark', label: 'Save quick search' },
  { id: 'manage-quick', icon: 'book-open', label: 'Quick searches' },
])

/* ------------------------------ suggestions ----------------------------- */

interface SuggestionEntry {
  suggestion: SearchSuggestion
  kind: 'history' | 'quick'
  preset?: QuickSearch
}

const suggestionEntries = computed<SuggestionEntry[]>(() => {
  const q = query.value.trim().toLowerCase()
  const matches = (text: string | null | undefined): boolean =>
    !q || (text ?? '').toLowerCase().includes(q)
  const fromHistory: SuggestionEntry[] = history.value
    .filter(matches)
    .map((text) => ({ suggestion: { text, hint: 'History' }, kind: 'history' }))
  const fromQuick: SuggestionEntry[] = quickSearches.value
    .filter((preset) => matches(preset.name) || matches(preset.keyword))
    .map((preset) => ({
      suggestion: { text: preset.keyword, hint: preset.name },
      kind: 'quick',
      preset,
    }))
  return [...fromHistory, ...fromQuick].slice(0, 10)
})

const suggestions = computed<SearchSuggestion[]>(() =>
  suggestionEntries.value.map((entry) => entry.suggestion),
)

/* --------------------------- query composition -------------------------- */

/** Positive `selected` → Android SiteConfig exclusion bitmask (bit = excluded). */
function exclusionMask(selected: GalleryCategory[]): number {
  return CATEGORY_ORDER.reduce(
    (mask, category) => (selected.includes(category) ? mask : mask | CATEGORY_BIT_VALUES[category]),
    0,
  )
}

/** Exclusion bitmask → positive selection (used when loading a preset). */
function maskToSelected(mask: number): GalleryCategory[] {
  return CATEGORY_ORDER.filter((category) => (mask & CATEGORY_BIT_VALUES[category]) === 0)
}

/** Android `formatListUrlBuilder` keyword part (uploader:/tag: prefixes). */
function composedKeyword(): string | undefined {
  const q = activeQuery.value.trim()
  if (!q) return undefined
  if (normalSearchMode.value === 'uploader') return `uploader:${q}`
  if (normalSearchMode.value === 'tag') return `tag:${q}`
  return q
}

function categoryParam(): number | undefined {
  const mask = exclusionMask(selectedCategories.value)
  return mask === 0 ? undefined : mask
}

/* -------------------------------- search -------------------------------- */

async function runSearch(target: number, append = false): Promise<void> {
  const seq = ++requestSeq
  if (append) {
    if (loadingMore.value) return
    loadingMore.value = true
  } else if (galleries.value.length === 0) {
    contentState.value = 'loading'
  }
  try {
    const response = await galleryApi.search(composedKeyword(), categoryParam(), target, PAGE_SIZE)
    if (seq !== requestSeq) return
    if (append) {
      // Dedupe by gid — bumped galleries can reappear across pages.
      const seen = new Set(galleries.value.map((g) => g.gid))
      galleries.value = [...galleries.value, ...response.data.filter((g) => !seen.has(g.gid))]
    } else {
      galleries.value = response.data
    }
    total.value = response.total
    page.value = target
    contentState.value = galleries.value.length === 0 ? 'empty' : 'content'
  } catch (error) {
    if (seq !== requestSeq) return
    console.error('[SearchView] search failed', error)
    if (append) {
      showSnack('Failed to load the next page')
    } else {
      contentState.value = 'error'
    }
  } finally {
    if (seq !== requestSeq) return
    refreshing.value = false
    loadingMore.value = false
  }
}

/** Commit a search: record history, collapse the input, reload page 0. */
function commitSearch(raw: string | null | undefined): void {
  const q = (raw ?? '').trim()
  activeQuery.value = q
  if (q) addHistory(q)
  searchBarState.value = 'normal'
  searchTitle.value = q || 'Search'
  panelExpanded.value = false
  fabExpanded.value = false
  contentRef.value?.scrollToTop()
  void runSearch(0)
}

function onPanelSearch(keyword: string, options: AdvanceSearchOptions): void {
  advanceOptions.value = { ...options }
  commitSearch(keyword)
}

function onRefresh(): void {
  void runSearch(0)
}

const hasMore = computed<boolean>(() => galleries.value.length < total.value)

function onLoadMore(): void {
  if (hasMore.value && !refreshing.value) {
    void runSearch(page.value + 1, true)
  }
}

function openGallery(gallery: GalleryInfo): void {
  router.push(`/gallery/${gallery.gid}`)
}

/* --------------------------- SearchBar handlers -------------------------- */

function openSearch(): void {
  // Android: tapping the title focuses the edit text and shows the list.
  searchBarState.value = 'search-list'
}

function closeSearch(): void {
  searchBarState.value = 'normal'
  searchTitle.value = activeQuery.value || 'Search'
}

function onQueryInput(next: string): void {
  query.value = next
  if (searchBarState.value === 'search') {
    searchBarState.value = 'search-list'
  }
}

function onSelectSuggestion(_suggestion: SearchSuggestion, index: number): void {
  const entry = suggestionEntries.value[index]
  if (!entry) return
  if (entry.kind === 'quick' && entry.preset) {
    loadQuickSearch(entry.preset)
  } else {
    query.value = entry.suggestion.text
    commitSearch(entry.suggestion.text)
  }
}

/** Long-press a suggestion — deletes history entries (Android onLongClick). */
function onDismissSuggestion(_suggestion: SearchSuggestion, index: number): void {
  const entry = suggestionEntries.value[index]
  if (entry?.kind !== 'history') return
  history.value = history.value.filter((item) => item !== entry.suggestion.text)
  writeStorage(HISTORY_KEY, history.value)
  showSnack('Removed from history')
}

/* ------------------------------ history/presets -------------------------- */

function addHistory(keyword: string): void {
  history.value = [keyword, ...history.value.filter((item) => item !== keyword)].slice(0, MAX_HISTORY)
  writeStorage(HISTORY_KEY, history.value)
}

function loadQuickSearch(preset: QuickSearch): void {
  query.value = preset.keyword ?? ''
  normalSearchMode.value = NUM_TO_MODE[preset.mode] ?? 'normal'
  selectedCategories.value = maskToSelected(preset.category)
  advanceOptions.value = {
    advanceSearch: preset.advanceSearch,
    minRating: preset.minRating,
    pageFrom: preset.pageFrom,
    pageTo: preset.pageTo,
  }
  enableAdvance.value =
    preset.advanceSearch !== 0 || preset.minRating > 0 || preset.pageFrom > 0 || preset.pageTo > 0
  dialog.value = 'none'
  commitSearch(preset.keyword)
}

function openSaveDialog(): void {
  presetName.value = ''
  dialog.value = 'save'
}

function saveQuickSearch(): void {
  const name = presetName.value.trim()
  if (!name) {
    showSnack('Give the preset a name first')
    return
  }
  const preset: QuickSearch = {
    id: Date.now(),
    name,
    mode: MODE_TO_NUM[normalSearchMode.value],
    category: exclusionMask(selectedCategories.value),
    keyword: query.value.trim(),
    advanceSearch: advanceOptions.value.advanceSearch,
    minRating: advanceOptions.value.minRating,
    pageFrom: advanceOptions.value.pageFrom,
    pageTo: advanceOptions.value.pageTo,
  }
  quickSearches.value = [...quickSearches.value, preset]
  writeStorage(QUICK_SEARCH_KEY, quickSearches.value)
  dialog.value = 'none'
  showSnack(`Saved “${name}”`)
}

function deleteQuickSearch(id: number): void {
  quickSearches.value = quickSearches.value.filter((preset) => preset.id !== id)
  writeStorage(QUICK_SEARCH_KEY, quickSearches.value)
}

function modeLabel(mode: number): string {
  return MODE_LABELS[NUM_TO_MODE[mode] ?? 'normal']
}

/* ---------------------------------- FAB ---------------------------------- */

function onFabPrimary(): void {
  // Android: the primary FAB surfaces the search options.
  panelExpanded.value = true
  contentRef.value?.scrollToTop()
}

function onFabSecondary(action: FabAction): void {
  fabExpanded.value = false
  if (action.id === 'save-quick') {
    openSaveDialog()
  } else if (action.id === 'manage-quick') {
    dialog.value = 'manage'
  } else if (action.id === 'toggle-view') {
    setViewMode(viewMode.value === 'grid' ? 'list' : 'grid')
  }
}

function setViewMode(mode: 'grid' | 'list'): void {
  viewMode.value = mode
  writeStorage(VIEW_MODE_KEY, mode)
}

function toggleSearchMode(): void {
  searchMode.value = searchMode.value === 'normal' ? 'image' : 'normal'
}

/* --------------------------------- chrome -------------------------------- */

function onNavSelect(item: NavItem): void {
  router.push(NAV_ROUTES[item.id] ?? '/')
}

function showSnack(message: string): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, 2600)
}

/* -------------------------------- storage -------------------------------- */

function readStorage<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

function writeStorage(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Storage unavailable (privacy mode) — feature degrades silently.
  }
}

/* --------------------------------- boot ---------------------------------- */

onMounted(async () => {
  // Seed presets from the server the first time (Android QuickSearch table).
  if (quickSearches.value.length === 0) {
    try {
      const response = await galleryApi.getQuickSearches()
      if (response.success && response.data?.length) {
        quickSearches.value = response.data
        writeStorage(QUICK_SEARCH_KEY, quickSearches.value)
      }
    } catch {
      // Offline seed is best-effort; local presets still work.
    }
  }
  await runSearch(0)
})

onBeforeUnmount(() => {
  if (snackTimer) window.clearTimeout(snackTimer)
})
</script>

<style scoped>
/* Scene shell — horizontal flex so the persistent drawer (≥720px, static
   panel) sits beside the main column; below that the drawer overlays. */
.search-scene {
  display: flex;
  height: 100dvh;
  background: var(--color-bg);
  overflow: hidden;
}

.search-scene__main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  /* The SearchBar sits in normal flow at the head of this column, so the
     whole column (bar → filter panel → results bar → ContentLayout) clears
     the status bar / cutout together. The drawer sibling already carries its
     own safe-area padding — do not pad `.search-scene` itself. The results
     bottom clears the FAB cluster via --gallery-padding-bottom-fab, which
     already includes --safe-area-bottom (no double offset). */
  padding-top: var(--safe-area-top);
}

.search-scene__content {
  flex: 1 1 auto;
  min-height: 0;
}

/* ------------------------------ results bar ----------------------------- */

.results-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 var(--gallery-search-bar-margin-h);
  padding: 0 4px 6px;
}

.results-bar__meta {
  font-size: clamp(11px, 12px, 14px);
  letter-spacing: 0.03em;
  color: var(--text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.results-bar__modes {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border-radius: 999px;
  background: var(--color-surface);
}

.results-bar__mode {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 24px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--drawable-color-secondary);
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.results-bar__mode svg {
  width: 14px;
  height: 14px;
  fill: currentColor;
}

.results-bar__mode:hover {
  color: var(--drawable-color-primary);
}

.results-bar__mode[aria-checked='true'] {
  background: var(--color-background-floating);
  color: var(--text-color-theme-primary);
  box-shadow: 0 1px 2px var(--shadow-color);
}

/* -------------------------------- results -------------------------------- */

.results {
  padding: 2px var(--gallery-grid-margin-h) var(--gallery-padding-bottom-fab);
}

/* Auto-column grids — span count derives from width ÷ column-width exactly
   like AutoStaggeredGridLayoutManager (responsive-strategy.md §4). */
.results--grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(var(--column-width-grid-middle), 100%), 1fr));
  gap: var(--gallery-grid-margin-v) var(--gallery-grid-interval);
}

.results--list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(var(--column-width-list-short), 100%), 1fr));
  gap: var(--gallery-list-margin-v) var(--gallery-list-interval);
}

/* -------------------------------- dialogs -------------------------------- */

.dialog-scrim {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--black-overlay);
}

.dialog {
  width: min(420px, 100%);
  max-height: min(80dvh, 560px);
  overflow-y: auto;
  padding: 20px 20px 12px;
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  box-shadow: 0 8px 24px var(--shadow-color);
}

.dialog__title {
  margin: 0 0 12px;
  font-size: clamp(16px, 18px, 22px);
  font-weight: 700;
  color: var(--text-color-primary);
}

.dialog__summary {
  margin: 0 0 14px;
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
}

.dialog__empty {
  margin: 8px 0 16px;
  font-size: clamp(13px, 14px, 16px);
  color: var(--text-color-secondary);
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--color-divider);
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.dialog-enter-active .dialog,
.dialog-leave-active .dialog {
  transition:
    transform var(--duration-scene-translate) var(--ease-decelerate-quint),
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .dialog,
.dialog-leave-to .dialog {
  transform: translateY(16px) scale(0.97);
  opacity: 0;
}

/* Quick-search rows. */
.qs-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.qs-item {
  display: flex;
  align-items: center;
  gap: 4px;
  border-radius: var(--card-radius);
  transition: background-color 120ms var(--ease-decelerate-quart);
}

.qs-item:hover {
  background: var(--color-surface);
}

.qs-item + .qs-item {
  border-top: 1px solid var(--color-divider);
}

.qs-item__main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 8px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.qs-item__name {
  font-size: clamp(14px, 16px, 18px);
  font-weight: 600;
  color: var(--text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.qs-item__summary {
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.qs-item__delete {
  flex: 0 0 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-secondary);
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.qs-item__delete:hover {
  background: var(--color-surface-activated);
  color: var(--color-red-500);
}

/* ------------------------------ form + buttons --------------------------- */

.field {
  position: relative;
  display: block;
}

.field input {
  width: 100%;
  padding: 14px 12px 10px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.field input:focus {
  border-color: var(--color-primary);
}

.field__label {
  position: absolute;
  left: 10px;
  top: 50%;
  translate: 0 -50%;
  padding: 0 4px;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-secondary);
  pointer-events: none;
  transition:
    top 150ms var(--ease-decelerate-quart),
    font-size 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.field input:focus + .field__label,
.field input:not(:placeholder-shown) + .field__label {
  top: 0;
  font-size: clamp(10px, 12px, 13px);
  color: var(--color-primary);
  background: var(--color-background-floating);
}

.btn-primary {
  padding: 9px 22px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 1px 3px var(--shadow-color);
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.btn-primary:hover {
  background: var(--color-primary-dark);
}

.btn-primary:active {
  transform: scale(0.97);
}

.btn-text {
  padding: 9px 14px;
  border: none;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--text-color-theme-primary);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.btn-text:hover {
  background: var(--color-surface);
}

/* -------------------------------- snackbar ------------------------------- */

.snackbar {
  position: fixed;
  left: 50%;
  /* The FAB cluster below is offset by --safe-area-bottom (FabLayout), so
     the snack carries the same inset to stay clear of both the FABs and the
     home indicator. */
  bottom: calc(var(--corner-fab-margin) + var(--fab-size) + 16px + var(--safe-area-bottom));
  translate: -50% 0;
  z-index: 300;
  max-width: min(480px, calc(100vw - 32px));
  padding: 12px 20px;
  border-radius: var(--card-radius);
  background: var(--gallery-slider-background);
  color: var(--color-white);
  font-size: clamp(13px, 14px, 16px);
  box-shadow: 0 4px 12px var(--shadow-color);
}

.snack-enter-active,
.snack-leave-active {
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    translate var(--duration-scene-translate) var(--ease-decelerate-quint);
}

.snack-enter-from,
.snack-leave-to {
  opacity: 0;
  translate: -50% 12px;
}

@media (prefers-reduced-motion: reduce) {
  .dialog-enter-active .dialog,
  .dialog-leave-active .dialog,
  .snack-enter-active,
  .snack-leave-active {
    transition: none;
  }
}
</style>
