<template>
  <div class="favorite-view">
    <div class="favorite-view__heading">
      <h1 class="favorite-view__title">Favorites</h1>
      <span v-if="state === 'content'" class="favorite-view__count">
        {{ favorites.length }} galleries
      </span>
    </div>

    <!-- Favorite folder filter — Android FavoritesScene's folder spinner,
         reimagined as a scrollable chip strip. Names come from
         `prefs.general.favoriteSlotNames` (B-4, `|`-separated), empty slots
         on the SiteConfig.DEFAULT_FAV_CAT_NAMES defaults ("Favorites 0" …
         "Favorites 9"). -->
    <nav class="slot-bar" aria-label="Favorite folders">
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
      error-text="Failed to load favorites"
      @refresh="onRefresh"
      @retry="onRetry"
      @load-more="onLoadMore"
    >
      <!-- Shared gallery list (B-1): renders the grid/list form from
           prefs.general.listMode (R4-1 — no hardcoded layout anymore). The
           favorite-slot badge rides along in both forms via item-extra. -->
      <GalleryList :items="favorites" @select="openGallery">
        <template #item-extra>
          <span class="slot-badge" :title="`In ${slotNames[activeSlot]}`">
            <AppIcon name="heart" size="12px" />
            {{ activeSlot }}
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
 * heart badge with the folder number it belongs to.
 *
 * Backend note: `FavoriteItem.category` is the stringified `SiteConfig` bit
 * (FavoriteService maps `entity.category.toString()`), so rows are converted
 * to `GalleryInfo` (numeric bit) before being handed to the list.
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { favoriteApi } from '@/api/favorite'
import type { FavoriteItem } from '@/api/favorite'
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

/** Monotonic request guard — stale responses (slot switches / refresh) drop. */
let requestSeq = 0

/** Maps a backend favorite row onto the `GalleryInfo` shape the list renders. */
function toGalleryInfo(item: FavoriteItem): GalleryInfo {
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
    favoriteSlot: activeSlot.value,
    favoriteName: slotNames.value[activeSlot.value] ?? '',
  }
}

async function loadPage(page: number, append: boolean): Promise<void> {
  const seq = ++requestSeq
  if (append) loadingMore.value = true
  try {
    const response = await favoriteApi.listFavorites(activeSlot.value, page)
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
    if (!append && favorites.value.length === 0) state.value = 'error'
  } finally {
    if (seq === requestSeq && append) loadingMore.value = false
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
  void router.push(`/gallery/${gallery.gid}`)
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
