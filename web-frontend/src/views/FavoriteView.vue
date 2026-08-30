<template>
  <div class="favorite-view">
    <div class="favorite-view__heading">
      <h1 class="favorite-view__title">Favorites</h1>
      <span v-if="state === 'content'" class="favorite-view__count">
        {{ favorites.length }} galleries
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
        aria-label="搜索收藏"
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

    <!-- Favorite folder filter — Android FavoritesScene's folder spinner,
         reimagined as a scrollable chip strip. Names come from
         `prefs.general.favoriteSlotNames` (B-4, `|`-separated), empty slots
         on the SiteConfig.DEFAULT_FAV_CAT_NAMES defaults ("Favorites 0" …
         "Favorites 9"). -->
    <nav class="slot-bar" aria-label="Favorite folders">
      <!-- "All" covers every folder the server knows, including the Android
           local-favorites slot (-2) which no numbered tab reaches. -->
      <button
        type="button"
        class="slot-bar__chip"
        :class="{ 'slot-bar__chip--active': activeSlot === -1 }"
        :aria-current="activeSlot === -1 ? 'true' : undefined"
        @click="selectSlot(-1, $event)"
      >
        {{ 'All' }}
      </button>
      <button
        v-for="(name, slot) in slotNames"
        :key="slot"
        type="button"
        class="slot-bar__chip"
        :class="{ 'slot-bar__chip--active': activeSlot === slot }"
        :aria-current="activeSlot === slot ? 'true' : undefined"
        @click="selectSlot(slot, $event)"
      >
        {{ name }}
      </button>
    </nav>

    <ContentLayout
      ref="contentRef"
      class="favorite-view__content"
      :state="state"
      v-model:refreshing="refreshing"
      :loading-more="loadingMore"
      empty-text="No favorites"
      :error-text="errorText"
      @refresh="onRefresh"
      @retry="onRetry"
      @load-more="onLoadMore"
    >
      <!-- Shared gallery list (B-1): renders the grid/list form from
           prefs.general.listMode (R4-1 — no hardcoded layout anymore). The
           favorite-slot badge rides along in both forms via item-extra.

           F-UX5: the badge renders the ITEM's real favoriteSlot (carried by
           FavoriteItem since the tab-0 semantics fix), not the active tab
           number. Slot -1 (default folder, only ever listed in tab 0) shows
           the heart alone — the app's badge carries no number either. -->
      <GalleryList :items="favorites" @select="openGallery">
        <template #item-extra="{ gallery }">
          <span class="slot-badge" :title="`In ${slotBadgeName(gallery.favoriteSlot)}`">
            <AppIcon name="heart" size="12px" />
            <template v-if="gallery.favoriteSlot >= 0">{{ gallery.favoriteSlot }}</template>
          </span>
        </template>
      </GalleryList>
    </ContentLayout>

    <!-- FabLayout replica: refresh + back-to-top mini FABs
         (scene_favorites.xml v_refresh / v_go_to cluster) -->
    <FabLayout
      v-model:expanded="fabExpanded"
      primary-icon="reorder"
      :actions="fabActions"
      @click-secondary="onFabAction"
    />

    <Teleport to="body">
      <!-- Toast (Android Toast equivalent, F4) -->
      <div v-if="toastMessage" class="toast" role="status">{{ toastMessage }}</div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
/**
 * FavoriteView — web replica of Android `FavoritesScene`:
 * ContentLayout (pull-to-refresh + infinite paging + empty tip) filled with
 * the shared `GalleryList` (B-1 — grid/list form follows
 * `prefs.general.listMode`), a favorite-folder filter strip (slots 0–9,
 * names from `prefs.general.favoriteSlotNames`, B-4), and the scene's
 * FabLayout cluster (refresh / go-to-top).
 *
 * The API is slot-scoped (`/favorite/list?slot=N&page=M`), mirroring the
 * Android scene which always shows exactly one folder; each row carries a
 * heart badge with the folder number it belongs to. Search (q, debounced)
 * and filter slots (A5d, q=pattern&regex=true) narrow the list server-side;
 * the two are mutually exclusive.
 *
 * F-UX5 — tab semantics align with the Android FavoritesScene: tab 0 is the
 * DEFAULT FOLDER (server filters `favoriteSlot in (-1, 0)`), tabs 1-9 are
 * the custom folders (`favoriteSlot == N`). The chip strip still sends
 * 0-9 exactly as before; only the server-side tab-0 mapping changed.
 *
 * Backend note: `FavoriteItem.category` is the stringified `SiteConfig` bit
 * (FavoriteService maps `entity.category.toString()`), so rows are converted
 * to `GalleryInfo` (numeric bit) before being handed to the list.
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { favoriteApi } from '@/api/favorite'
import type { FavoriteItem } from '@/api/favorite'
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
import { parseFavoriteSlotNames } from '@/components/gallery/GalleryCard.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import { usePreferencesStore } from '@/stores/preferences'

/** View states matching ContentLayout's internal ViewTransition. */
type ViewState = 'loading' | 'content' | 'empty' | 'error'

const preferencesStore = usePreferencesStore()

/**
 * B-4: the 10 folder names from `general.favoriteSlotNames` (`|`-separated),
 * empty slots on the `SiteConfig.DEFAULT_FAV_CAT_NAMES` defaults. Read
 * defensively — the key is added by a parallel settings-schema stream, so
 * unloaded prefs or a missing key simply yield the defaults the strip has
 * always shown.
 */
const slotNames = computed(() =>
  parseFavoriteSlotNames(
    (preferencesStore.prefs?.general as { favoriteSlotNames?: string } | undefined)
      ?.favoriteSlotNames,
  ),
)

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

const favorites = ref<GalleryInfo[]>([])
const activeSlot = ref(0)
const currentPage = ref(1)
const totalPages = ref(1)
const state = ref<ViewState>('loading')
const refreshing = ref(false)
const loadingMore = ref(false)
const contentRef = ref<InstanceType<typeof ContentLayout> | null>(null)
/** F4 REGEX_INVALID: the error tip switches to a dedicated regex message. */
const errorText = ref('Failed to load favorites')

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

/** Monotonic request guard — stale responses (slot switches / refresh) drop. */
let requestSeq = 0

/** Maps an item slot onto its folder-tab index — slot -1 (default folder)
 *  is listed in tab 0, mirroring the Android FavoritesScene first tab. */
function slotTabIndex(slot: number): number {
  return slot >= 0 ? slot : 0
}

/** Folder display name for the badge title of an item with the given slot. */
function slotBadgeName(slot: number): string {
  return slotNames.value[slotTabIndex(slot)] ?? ''
}

/** Maps a backend favorite row onto the `GalleryInfo` shape the list renders. */
function toGalleryInfo(item: FavoriteItem): GalleryInfo {
  // F-UX5: the row's REAL slot rides along (FavoriteItem.favoriteSlot) so the
  // ♥ badge shows the true folder — tab 0 mixes slots -1 and 0. Legacy
  // servers without the field fall back to the active tab number.
  const slot = item.favoriteSlot ?? activeSlot.value
  return {
    gid: item.gid,
    token: item.token,
    // R4-6: title-less galleries surface as `#<gid>`, not "Untitled".
    title: item.title || item.titleJpn || `#${item.gid}`,
    titleJpn: item.titleJpn,
    thumb: item.thumb,
    category: categoryBit(item.category),
    posted: item.posted ?? '',
    uploader: item.uploader ?? '',
    rating: item.rating,
    rated: false,
    simpleLanguage: '',
    simpleTags: [],
    thumbWidth: 0,
    thumbHeight: 0,
    pages: 0,
    favoriteSlot: slot,
    favoriteName: slotBadgeName(slot),
  }
}

/* ---------------------------------------- search + filter slots (A5d) ----- */

/** 搜索词：防抖后作为 q 传给 /favorite/list（服务端过滤）。 */
const searchQuery = ref('')
const debouncedQuery = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

/**
 * 筛选槽位（A5d）：命名正则预设；与搜索框互斥（useFilterSlots 保证）——
 * 选槽位清空 searchQuery、输入搜索取消槽位。重命名为 filterSlot 以避免与
 * 收藏夹标签 activeSlot（0-9）冲突。
 */
const { slots, activeSlotId, activeSlot: filterSlot, selectSlot: selectFilterSlot } =
  useFilterSlots(searchQuery)

function onSlotBarSelect(id: string | null): void {
  selectFilterSlot(id)
  // 槽位点击总是重新加载（清空搜索词不一定触发防抖 watch——搜索词本来就空时）。
  favorites.value = []
  state.value = 'loading'
  void loadPage(1, false)
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
      favorites.value = []
      state.value = 'loading'
      void loadPage(1, false)
    }
  }, 400)
})

function clearSearch(): void {
  searchQuery.value = ''
  debouncedQuery.value = ''
  favorites.value = []
  state.value = 'loading'
  void loadPage(1, false)
}

async function loadPage(page: number, append: boolean): Promise<void> {
  const seq = ++requestSeq
  if (append) loadingMore.value = true
  try {
    const filter = currentFilter()
    const response = await favoriteApi.listFavorites(
      activeSlot.value,
      page,
      filter.q || null,
      filter.regex || undefined,
    )
    if (seq !== requestSeq) return
    const mapped = response.favorites.map(toGalleryInfo)
    if (append) {
      const known = new Set(favorites.value.map((gallery) => gallery.gid))
      favorites.value.push(...mapped.filter((gallery) => !known.has(gallery.gid)))
    } else {
      favorites.value = mapped
    }
    currentPage.value = response.currentPage
    totalPages.value = response.totalPages
    state.value = favorites.value.length === 0 ? 'empty' : 'content'
  } catch (error) {
    if (seq !== requestSeq) return
    console.error('Failed to load favorites', error)
    // F4: invalid regex in q → 400 REGEX_INVALID; name the cause instead of
    // the generic "failed to load" tip (dedicated toast + error-state copy).
    if (errorCodeOf(error) === 'REGEX_INVALID') {
      errorText.value = '正则无效，请检查搜索/筛选的正则表达式'
      showToast('正则无效，请检查筛选表达式')
    } else {
      errorText.value = 'Failed to load favorites'
    }
    if (!append && favorites.value.length === 0) state.value = 'error'
  } finally {
    // F5: 无条件复位——append 被后续 replace 作废（seq 过期）时也必须松开
    // loadingMore，否则页脚 spinner 永久卡死（对照 DownloadView.loadMore）。
    if (append) loadingMore.value = false
  }
}

function selectSlot(slot: number, event: MouseEvent): void {
  if (slot === activeSlot.value) return
  activeSlot.value = slot
  const el = event.currentTarget
  if (el instanceof HTMLElement) {
    el.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
  }
  favorites.value = []
  state.value = 'loading'
  void loadPage(1, false)
}

async function onRefresh(): Promise<void> {
  await loadPage(1, false)
  refreshing.value = false
}

function onRetry(): void {
  state.value = 'loading'
  void loadPage(1, false)
}

/** Footer near-bottom → next page (Android ContentLayout footer refresh). */
function onLoadMore(): void {
  if (state.value !== 'content' || loadingMore.value || refreshing.value) return
  if (currentPage.value >= totalPages.value) return
  void loadPage(currentPage.value + 1, true)
}

function openGallery(gallery: GalleryInfo): void {
  // 本地 token 透传（P-A）：收藏行若无历史/下载背书，服务端凭 token 上游直取。
  void router.push({
    path: `/gallery/${gallery.gid}`,
    query: gallery.token ? { token: gallery.token } : {},
  })
}

/* --------------------------------------------------------------- FAB ---- */

const fabExpanded = ref(false)

const fabActions: FabAction[] = [
  { id: 'refresh', icon: 'refresh-dark', label: 'Refresh favorites' },
  { id: 'scroll-top', icon: 'go-to-dark', label: 'Back to top' },
]

function onFabAction(action: FabAction): void {
  fabExpanded.value = false
  if (action.id === 'refresh') {
    state.value = 'loading'
    void loadPage(1, false)
  } else if (action.id === 'scroll-top') {
    contentRef.value?.scrollToTop()
  }
}

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
  clearTimeout(toastTimer)
})

onMounted(() => {
  // Folder names (B-4) come from preferences. GalleryList loads them when it
  // mounts, but the chip strip renders before the content state does — kick
  // the load here so custom names appear as early as possible.
  if (!preferencesStore.prefs && !preferencesStore.loading) {
    void preferencesStore.load()
  }
  void loadPage(1, false)
})
</script>

<style scoped>
.favorite-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  /* Standalone PWA: push the header row + slot bar + list below the status
     bar / cutout. border-box keeps the column at 100dvh — the flex:1
     ContentLayout shrinks instead of overflowing. The list bottom already
     clears the home indicator via --gallery-padding-bottom-fab. */
  padding-top: var(--safe-area-top);
  background: var(--color-bg);
}

.favorite-view__content {
  flex: 1;
  min-height: 0;
}

/* ------------------------------------------------------------ heading --- */
.favorite-view__heading {
  display: flex;
  align-items: baseline;
  gap: var(--spacing);
  flex-shrink: 0;
  padding: 14px max(var(--gallery-list-margin-h), 4px) 0;
}

.favorite-view__title {
  font-size: var(--text-super-large); /* 24sp */
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-color-primary);
}

.favorite-view__count {
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

/* ----------------------------------------------------------- slot bar --- */
.slot-bar {
  display: flex;
  gap: var(--spacing);
  flex-shrink: 0;
  overflow-x: auto;
  padding: var(--spacing) max(var(--gallery-list-margin-h), 4px);
  border-bottom: 1px solid var(--color-divider);
  scrollbar-width: none;
}

.slot-bar::-webkit-scrollbar {
  display: none;
}

.slot-bar__chip {
  flex: 0 0 auto;
  padding: 5px 14px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius); /* 2dp — CheckTextView, not a pill */
  background: transparent;
  color: var(--text-color-secondary);
  font-family: inherit;
  font-size: var(--text-super-small); /* 12sp */
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 160ms var(--ease-decelerate-quart),
    border-color 160ms var(--ease-decelerate-quart),
    color 160ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.slot-bar__chip:hover {
  border-color: var(--color-primary);
  color: var(--text-color-primary);
}

.slot-bar__chip:active {
  transform: scale(0.95);
}

.slot-bar__chip--active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}

.slot-bar__chip--active:hover {
  color: var(--color-white);
}

.slot-bar__chip:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

/* -------------------------------------------------------------- list ---- */
/* Row layout, entrance animation and stagger live in GalleryList (B-1);
   only the favorites-specific slot badge is styled here. */

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

@media (prefers-reduced-motion: reduce) {
  .toast {
    animation: none;
  }
}


/* Favorite slot indicator — heart + folder number, accent background. */
.slot-badge {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 7px;
  border-radius: var(--card-radius);
  background: var(--color-accent);
  color: var(--color-white);
  font-size: var(--text-super-small); /* 12sp */
  font-weight: 600;
  line-height: 1.4;
  box-shadow: 0 1px 3px var(--shadow-color);
  pointer-events: none;
}
</style>
