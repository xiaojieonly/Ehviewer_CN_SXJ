<template>
  <AppCard :mode="mode" @click="emit('click', gallery)" @contextmenu="onContextMenu">
    <!-- List mode — replicates `item_gallery_list.xml`: fixed 80×120dp (2:3)
         thumbnail left, info column right (title / japanese title / rating /
         category chip / page count / tags / posted). -->
    <template v-if="mode === 'list'">
      <div class="gallery-card__thumb">
        <img
          v-if="hasThumb"
          ref="imgRef"
          class="gallery-card__img"
          :class="{ 'is-loaded': imgLoaded }"
          :src="thumbSrc"
          :alt="displayTitle"
          loading="lazy"
          decoding="async"
          @load="onImgLoad"
          @error="onImgError"
        />
        <div v-else class="gallery-card__thumb-placeholder" aria-hidden="true">
          <AppIcon name="download-primary" size="28px" />
        </div>
      </div>
      <div class="gallery-card__body">
        <h3 class="gallery-card__title">{{ displayTitle }}</h3>
        <p v-if="gallery.titleJpn && !privacyMaskEnabled" class="gallery-card__title-jpn">
          {{ gallery.titleJpn }}
        </p>
        <!-- Info switches (B-2): uploader / posted render only when the
             corresponding `general.show*` preference is on (default off). -->
        <p v-if="showUploader && gallery.uploader" class="gallery-card__uploader">
          {{ gallery.uploader }}
        </p>
        <div class="gallery-card__rating-row">
          <RatingStars :rating="gallery.rating" />
          <span v-if="showPostedTime && gallery.posted" class="gallery-card__posted">{{ gallery.posted }}</span>
        </div>
        <div class="gallery-card__meta-row">
          <CategoryChip v-if="categoryKey" :category="categoryKey" />
          <!-- W5 (plan-2026-09-02): showReadProgress 开且 readProgress > 0 时，
               进度角标顶替页数文案（Android GalleryAdapterNew 同一 TextView
               的替换语义）：N/MP（有页数）或 NP（页数未知）。 -->
          <span
            v-if="showReadProgressBadge"
            class="gallery-card__pages"
            data-testid="read-progress-badge"
          >
            {{ readProgressLabel }}
          </span>
          <span v-else-if="gallery.pages > 0" class="gallery-card__pages">{{ gallery.pages }}P</span>
          <span v-for="tag in gallery.simpleTags" :key="tag" class="gallery-card__tag">
            {{ tag }}
          </span>
        </div>
      </div>
      <!-- F-UX6 (PC form only): hover / focus-within quick actions. -->
      <CardQuickActions
        v-if="pcInput"
        variant="list"
        :is-favorited="isFavorited"
        :favorite-pending="favoritePending"
        :download-state="downloadState"
        @favorite="toggleFavorite"
        @download="download"
        @detail="openDetail"
      />
    </template>

    <!-- Grid mode — replicates `item_gallery_grid.xml` + `TileThumb`:
         aspect-clamped tile filling the column, 32×24dp CategoryTriangle at
         the top-right corner, language badge, title below (2-line clamp). -->
    <template v-else>
      <div class="gallery-card__tile" :style="{ aspectRatio: tileAspect }">
        <img
          v-if="hasThumb"
          ref="imgRef"
          class="gallery-card__img"
          :class="{ 'is-loaded': imgLoaded }"
          :src="thumbSrc"
          :alt="displayTitle"
          loading="lazy"
          decoding="async"
          @load="onImgLoad"
          @error="onImgError"
        />
        <div v-else class="gallery-card__tile-placeholder" aria-hidden="true">
          <AppIcon name="download-primary" size="28px" />
        </div>
        <CategoryTriangle
          v-if="categoryKey"
          class="gallery-card__triangle"
          :category="categoryKey"
        />
        <span v-if="gallery.simpleLanguage" class="gallery-card__lang">
          {{ gallery.simpleLanguage }}
        </span>
        <!-- W5: grid 形态的阅读进度角标（列表形态放在 meta 行，见上）——
             右下角镜像左下角的语言徽标。 -->
        <span
          v-if="showReadProgressBadge"
          class="gallery-card__progress"
          data-testid="read-progress-badge"
        >
          {{ readProgressLabel }}
        </span>
        <slot name="overlay" />
        <!-- F-UX6 (PC form only): hover / focus-within quick actions. -->
        <CardQuickActions
          v-if="pcInput"
          variant="grid"
          :is-favorited="isFavorited"
          :favorite-pending="favoritePending"
          :download-state="downloadState"
          @favorite="toggleFavorite"
          @download="download"
          @detail="openDetail"
        />
      </div>
      <!-- F-UX1: the grid meta area is a FLOWING two-line region — title
           line plus an optional `grid-sub` line (e.g. History's last-viewed
           stamp). The sub line is a normal-flow sibling of the title, so a
           present sub line stretches the card instead of being absolutely
           positioned over the title. Cards without a sub line keep the
           classic single-line meta (padding lives on the wrapper either
           way). -->
      <div class="gallery-card__grid-meta">
        <h3 class="gallery-card__grid-title">{{ displayTitle }}</h3>
        <div v-if="hasGridSub" class="gallery-card__grid-sub">
          <GridSubLine />
        </div>
      </div>
    </template>

    <!-- Transient action feedback (F-UX6 PC quick actions / context menu). -->
    <div v-if="toastMessage" class="gallery-card__toast" role="status">
      {{ toastMessage }}
    </div>

    <!-- F-UX6: right-click menu (teleported; action state lives here). -->
    <CardContextMenu
      v-if="menuState"
      :x="menuState.x"
      :y="menuState.y"
      :items="menuItems"
      @action="onMenuAction"
      @close="menuState = null"
    />
  </AppCard>
</template>

<script lang="ts">
/**
 * Android `SiteConfig.DEFAULT_FAV_CAT_NAMES` — the built-in favorite folder
 * names, "Favorites 0" … "Favorites 9" (same defaults FavoriteView's folder
 * strip has always used).
 */
export const DEFAULT_FAVORITE_SLOT_NAMES: readonly string[] = Array.from(
  { length: 10 },
  (_, i) => `Favorites ${i}`,
)

/**
 * B-4: parses `general.favoriteSlotNames` — a `|`-separated list of up to 10
 * folder names. Missing/empty entries fall back to
 * {@link DEFAULT_FAVORITE_SLOT_NAMES} slot by slot (`A||C` → `A`, `Favorites 1`,
 * `C`, `Favorites 3` …). Non-string input yields the full default list.
 */
export function parseFavoriteSlotNames(raw: unknown): string[] {
  const parts = typeof raw === 'string' ? raw.split('|') : []
  return Array.from({ length: 10 }, (_, i) => {
    const name = parts[i]?.trim()
    return name ? name : DEFAULT_FAVORITE_SLOT_NAMES[i]
  })
}
</script>

<script setup lang="ts">
/**
 * GalleryCard — the gallery list card in both modes (roadmap §卡片规范,
 * replicating the `scene_gallery_list.xml` item layouts). Implements the
 * frozen `GalleryCardProps` / `GalleryCardEmits` / `GalleryCardSlots`
 * contracts from `@/types/components`.
 *
 * Built on the `AppCard` surface atom (2dp radius / 2dp elevation shadow /
 * 2dp outer margin, `contentColorPrimary` background): `mode` is forwarded
 * so the surface flexes row (list) / column (grid), the body renders in the
 * default slot, and AppCard's click / keyboard activation is re-emitted
 * with the gallery payload.
 *
 * - `list`: fixed 80×120dp thumbnail (`--thumb-list-width/height`, 2:3,
 *   CENTER_CROP), right column = title (16sp, 2 lines), japanese title (12sp,
 *   single line, secondary), `RatingStars` + posted date, `CategoryChip` +
 *   page count + simple tags.
 * - `grid`: tile filling the column width with `TileThumb`-style aspect
 *   clamping (`--thumb-min-aspect`…`--thumb-max-aspect`, default 2:3),
 *   32×24dp `CategoryTriangle` top-right, language badge (10sp white bold),
 *   title below (2-line clamp). The meta area below the tile is a flowing
 *   region: title line plus an optional `grid-sub` line that stretches the
 *   card when present (F-UX1 — History's last-viewed stamp; never an
 *   absolutely positioned overlay over the title).
 *
 * The numeric `gallery.category` is an `SiteConfig` bit value; it is converted
 * to a `GalleryCategory` via `CATEGORY_BY_BIT` before driving the chip /
 * triangle (unknown bits render neither).
 *
 * Preference-driven display (Wave-1 B group):
 * - B-2 `general.showUploader` / `general.showPostedTime` gate the uploader /
 *   posted rows (both default OFF — missing key, unloaded prefs and unknown
 *   values all hide the field).
 * - R4-6 an empty title renders as `#<gid>` instead of a blank heading.
 * - B-4 `favoriteSlotNames` derives the 10 favorite folder names from
 *   `general.favoriteSlotNames` (`|`-separated), empty slots falling back to
 *   {@link DEFAULT_FAVORITE_SLOT_NAMES}.
 * - W5 (plan-2026-09-02) `general.showReadProgress` + `gallery.readProgress > 0`
 *   show the reading-progress badge (`N/MP` with a known page count, `NP`
 *   otherwise), replacing the pages text in list form and riding the tile's
 *   bottom-right corner in grid form (Android `GalleryAdapterNew`).
 *
 * The preference keys are read defensively (optional chaining + defaults):
 * they are being added to the preferences schema by a parallel work stream,
 * so the card must behave identically whether or not they exist yet.
 */
import { computed, onBeforeUnmount, onMounted, ref, useSlots, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  CATEGORY_BY_BIT,
  type GalleryCardEmits,
  type GalleryCardProps,
  type GalleryCardSlots,
} from '@/types/components'
import { usePreferencesStore } from '@/stores/preferences'
import type { GeneralPreferences } from '@/api/preferences'
import { favoriteApi } from '@/api/favorite'
import { downloadApi } from '@/api/download'
import { rewriteSiteAssetUrl } from '@/utils/siteAsset'
import { maskedImageSrc, privacyMaskEnabled } from '@/utils/privacyMask'
import AppCard from '@/components/atoms/AppCard.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import RatingStars from '@/components/atoms/RatingStars.vue'
import CategoryChip from '@/components/atoms/CategoryChip.vue'
import CategoryTriangle from '@/components/atoms/CategoryTriangle.vue'
import CardContextMenu, { type CardContextMenuItem } from './CardContextMenu.vue'
import CardQuickActions from './CardQuickActions.vue'

/**
 * General-preference keys consumed by the card but added by the parallel
 * settings-schema stream (PreferenceDto +5 keys). Kept optional so this
 * compiles and runs whether or not the typed DTO carries them yet.
 */
interface GeneralPrefsExtras {
  showUploader?: boolean
  showPostedTime?: boolean
  favoriteSlotNames?: string
}

const props = defineProps<GalleryCardProps>()
const emit = defineEmits<GalleryCardEmits>()
defineSlots<GalleryCardSlots>()

/**
 * F-UX1: presence + rendering of the optional `grid-sub` meta line. The slot
 * extends the card beyond the frozen `GalleryCardSlots` contract, so it is
 * resolved dynamically (the contract file stays untouched) instead of through
 * a `<slot name="grid-sub">` element, which would be type-checked against the
 * frozen interface. Consumers thread it via `GalleryGrid`'s `cell-sub` slot.
 */
const slots = useSlots()
const gridSubSlot = computed(() => (slots as Record<string, unknown>)['grid-sub'])
const hasGridSub = computed(() => Boolean(gridSubSlot.value))

/** Inline functional component emitting the `grid-sub` slot content. */
function GridSubLine() {
  const slot = gridSubSlot.value
  return typeof slot === 'function' ? (slot as (props: Record<string, unknown>) => unknown)({}) : null
}

const preferencesStore = usePreferencesStore()

const generalPrefs = computed<(GeneralPreferences & GeneralPrefsExtras) | undefined>(
  () => preferencesStore.prefs?.general,
)

/** B-2 info switches — strictly `true` shows the field; anything else hides it. */
const showUploader = computed(() => generalPrefs.value?.showUploader === true)
const showPostedTime = computed(() => generalPrefs.value?.showPostedTime === true)

/**
 * W5 (plan-2026-09-02) — 阅读进度角标：`general.showReadProgress` 开启且
 * `gallery.readProgress > 0` 才显示；格式对齐 Android `GalleryAdapterNew`
 * （`startPage+1/pagesP`），页数未知（pages ≤ 0）退化为 `NP`。字段缺失
 * （旧服务器 undefined / null / 0）一律隐藏。
 */
const readProgressLabel = computed(() => {
  const progress = props.gallery.readProgress
  if (typeof progress !== 'number' || !Number.isFinite(progress) || progress <= 0) return ''
  const current = progress + 1
  return props.gallery.pages > 0 ? `${current}/${props.gallery.pages}P` : `${current}P`
})
const showReadProgressBadge = computed(
  () => generalPrefs.value?.showReadProgress === true && readProgressLabel.value !== '',
)

/** R4-6: galleries without a title render as `#<gid>`; 打码开启时一律序列号。 */
const displayTitle = computed(() => {
  if (privacyMaskEnabled.value) return `#${props.gallery.gid}`
  const title = props.gallery.title
  return title && title.trim().length > 0 ? title : `#${props.gallery.gid}`
})

/** B-4: the 10 favorite folder names, empty slots on their defaults. */
const favoriteSlotNames = computed(() =>
  parseFavoriteSlotNames(generalPrefs.value?.favoriteSlotNames),
)

defineExpose({ favoriteSlotNames })

/**
 * Mirror of `--thumb-min-aspect` / `--thumb-max-aspect` (FixedThumb attrs) —
 * kept in sync by hand; used to clamp the grid tile aspect ratio in JS.
 */
const THUMB_MIN_ASPECT = 0.333
const THUMB_MAX_ASPECT = 1.333

/** Numeric category bit → `GalleryCategory` key (undefined when unknown). */
const categoryKey = computed(() => CATEGORY_BY_BIT[props.gallery.category])

/**
 * Grid tile aspect ratio, replicating `TileThumb.setThumbSize` clamping:
 * `w / h` clamped into [0.333, 1.333], falling back to 2:3 when the source
 * dimensions are unknown (list thumbnails are always 2:3 = 80×120).
 */
const tileAspect = computed<string>(() => {
  const { thumbWidth, thumbHeight } = props.gallery
  if (!thumbWidth || !thumbHeight) return '2 / 3'
  return String(Math.min(THUMB_MAX_ASPECT, Math.max(THUMB_MIN_ASPECT, thumbWidth / thumbHeight)))
})

/* Thumbnail fade-in: hidden until decoded; `complete` check covers images
   that finished loading from cache before the listener attached. The card
   itself never depends on the thumbnail — a missing (`thumb` null/empty) or
   failed image falls back to the placeholder, and the title/progress/actions
   render regardless (E2E-9: cards stay visible without a thumbnail). */
const imgRef = ref<HTMLImageElement | null>(null)
const imgLoaded = ref(false)
const thumbFailed = ref(false)

/** A usable thumbnail source; null/empty renders the placeholder, and a
    failed load swaps to it too. */
const hasThumb = computed(() => Boolean(props.gallery.thumb) && !thumbFailed.value)

/**
 * R4-9: the raw `thumb` may point at the unresolvable Gallery Site host
 * (`e-hentai.org` family); rewrite those through the server's same-origin
 * image proxy so the thumbnail actually loads. Non-site URLs pass through.
 * 隐私打码开启时换成占位图（真实缩略图不发请求）。
 */
const thumbSrc = computed(() => maskedImageSrc(rewriteSiteAssetUrl(props.gallery.thumb)))

function onImgLoad(): void {
  imgLoaded.value = true
}

function onImgError(): void {
  // Swapped to the placeholder — the broken-image box must never leak the
  // `alt` (= title) text into the card (E2E-3).
  thumbFailed.value = true
}

onMounted(() => {
  if (imgRef.value?.complete) imgLoaded.value = true
})

/* --------------------------------------------------------------------------
   F-UX6 — PC quick actions + right-click menu (roadmap §3.1). PC form ONLY:
   rendered under `pointer: fine` AND viewport ≥720px (§0 red line — mobile
   forms never see these). Actions reuse the established API usage of
   GalleryDetailView / DownloadView; no new backend surface is introduced.
   -------------------------------------------------------------------------- */

const router = useRouter()

/** Minimum viewport width for the PC-only affordances. */
const PC_MIN_WIDTH = 720

const finePointer = ref(false)
const wideViewport = ref(false)

/** PC input gate — fine pointer AND ≥720px viewport. */
const pcInput = computed(() => finePointer.value && wideViewport.value)

function refreshPcInput(): void {
  finePointer.value =
    typeof window.matchMedia === 'function' && window.matchMedia('(pointer: fine)').matches
  wideViewport.value = window.innerWidth >= PC_MIN_WIDTH
}

// Initialize at setup time (not onMounted) so the FIRST render already
// carries the correct gate — no one-frame flash, no render-tick dependency.
refreshPcInput()

let pointerMql: MediaQueryList | null = null

onMounted(() => {
  refreshPcInput()
  if (typeof window.matchMedia === 'function') {
    pointerMql = window.matchMedia('(pointer: fine)')
    pointerMql.addEventListener('change', refreshPcInput)
  }
  window.addEventListener('resize', refreshPcInput)
})

/* ------------------------------------------------------------ actions ---- */

/**
 * Favorite state — seeded from the row's `favoriteSlot` (-2 = not favorited;
 * -1 default folder / 0-9 custom slots = favorited), toggled optimistically
 * exactly like GalleryDetailView.
 */
const isFavorited = ref(props.gallery.favoriteSlot >= -1)
const favoritePending = ref(false)
const downloadState = ref<'idle' | 'busy' | 'done'>('idle')

/** Cards are keyed by gid elsewhere; a recycled instance resets on swap. */
watch(
  () => props.gallery.gid,
  () => {
    isFavorited.value = props.gallery.favoriteSlot >= -1
    favoritePending.value = false
    downloadState.value = 'idle'
    menuState.value = null
  },
)

/** Optimistic favorite toggle (GalleryDetailView pattern). The target folder
    follows `general.defaultFavoriteSlot` (B-3) when it names a real folder;
    the backend default folder (-1) applies while prefs are unloaded or the
    configured slot is unsettable (< -1). */
async function toggleFavorite(): Promise<void> {
  if (favoritePending.value) return
  favoritePending.value = true
  const next = !isFavorited.value
  isFavorited.value = next
  try {
    const slot = preferencesStore.prefs?.general?.defaultFavoriteSlot
    const targetSlot = typeof slot === 'number' && slot >= -1 ? slot : undefined
    const res = next
      ? await favoriteApi.addFavorite(
          props.gallery.gid,
          props.gallery.token,
          props.gallery.category,
          targetSlot,
        )
      : await favoriteApi.removeFavorite(props.gallery.gid, props.gallery.token)
    if (!res.success) throw new Error('favorite action rejected')
    showToast(next ? 'Favorited' : 'Removed from favorites')
  } catch (error) {
    console.error('Failed to toggle favorite', error)
    isFavorited.value = !next
    showToast('Favorite update failed')
  } finally {
    favoritePending.value = false
  }
}

/** Queue the gallery in the downloader — GalleryDetailView usage. */
async function download(): Promise<void> {
  if (downloadState.value !== 'idle') return
  downloadState.value = 'busy'
  try {
    await downloadApi.add(
      props.gallery.gid,
      props.gallery.token,
      props.gallery.title,
      props.gallery.thumb,
    )
    downloadState.value = 'done'
    showToast('Added to downloads')
  } catch (error) {
    console.error('Failed to add download', error)
    downloadState.value = 'idle'
    showToast('Download failed')
  }
}

function openDetail(): void {
  const { gid, token } = props.gallery
  // 带 token 跳转：详情页可从站点直取（本地无历史记录时不再 404）。
  const query = token ? { token } : undefined
  void router.push({ path: `/gallery/${gid}`, query })
}

/**
 * Copy the app's own detail link. Never emits a real gallery-site URL — the
 * internal route is the shareable address, and the site host (`e-hentai.org`)
 * never leaks into the copied link.
 */
async function copyLink(): Promise<void> {
  const url = `${window.location.origin}/gallery/${props.gallery.gid}`
  try {
    await navigator.clipboard.writeText(url)
    showToast('Link copied')
  } catch (error) {
    console.error('Failed to copy link', error)
    showToast('Unable to copy link')
  }
}

/* --------------------------------------------------------------- toast --- */

const toastMessage = ref<string | null>(null)
let toastTimer: ReturnType<typeof setTimeout> | undefined

function showToast(message: string): void {
  toastMessage.value = message
  if (toastTimer !== undefined) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = null
  }, 1600)
}

/* ------------------------------------------------- context menu (F-UX6) --- */

const menuState = ref<{ x: number; y: number } | null>(null)

const menuItems = computed<CardContextMenuItem[]>(() => [
  { id: 'detail', icon: 'info-outline-dark', label: 'Details' },
  {
    id: 'favorite',
    icon: isFavorited.value ? 'heart' : 'heart-outline-primary',
    label: isFavorited.value ? 'Remove from favorites' : 'Favorite',
  },
  { id: 'download', icon: 'download', label: 'Download' },
  { id: 'copy-link', icon: 'copy', label: 'Copy link' },
])

function onContextMenu(event: MouseEvent): void {
  // Non-PC forms keep their native context menu untouched (§0 red line).
  if (!pcInput.value) return
  event.preventDefault()
  menuState.value = { x: event.clientX, y: event.clientY }
}

function onMenuAction(id: CardContextMenuItem['id']): void {
  menuState.value = null
  if (id === 'detail') openDetail()
  else if (id === 'favorite') void toggleFavorite()
  else if (id === 'download') void download()
  else if (id === 'copy-link') void copyLink()
}

onBeforeUnmount(() => {
  pointerMql?.removeEventListener('change', refreshPcInput)
  pointerMql = null
  window.removeEventListener('resize', refreshPcInput)
  if (toastTimer !== undefined) clearTimeout(toastTimer)
})
</script>

<style scoped>
/* --------------------------------------------------------------------------
   Card surface — inherited from the AppCard atom (2dp radius / 2dp elevation
   shadow / 2dp outer margin, `contentColorPrimary` background). The root IS
   `.app-card`, so the selectors below extend it with the gallery-specific
   interactions (entrance reveal, active press, hover title tint).
   -------------------------------------------------------------------------- */
.app-card {
  user-select: none;
  /* Entrance reveal; `--enter-delay` is staggered by GalleryGrid. Fill mode
     `backwards` (not `both`) so post-animation hover/active transforms win. */
  animation: gallery-card-enter var(--duration-scene-translate) var(--ease-decelerate-quint)
    backwards;
  animation-delay: var(--enter-delay, 0ms);
}

.app-card {
  /* Anchor for the F-UX6 list-form quick-action pill + the toast. */
  position: relative;
}

.app-card:active {
  transform: scale(0.98);
}

/* F-UX6 (PC form only): the quick-action bar rides on hover AND
   focus-within, so tabbing into a card's action buttons reveals it exactly
   like the mouse does (keyboard reachable). */
.app-card:hover .card-quick-actions,
.app-card:focus-within .card-quick-actions {
  opacity: 1;
  pointer-events: auto;
}

/* Transient action feedback, floating over the card bottom. */
.gallery-card__toast {
  position: absolute;
  z-index: 3;
  left: 50%;
  bottom: 12px;
  transform: translateX(-50%);
  max-width: calc(100% - 16px);
  padding: 5px 12px;
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  box-shadow: 0 2px 8px var(--shadow-color);
  color: var(--text-color-primary);
  font-size: var(--text-super-small);
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: none;
}

/* --------------------------------------------------------------------------
   Thumbnails — FixedThumb: CENTER_CROP inside the clamped aspect container.
   -------------------------------------------------------------------------- */
.gallery-card__thumb {
  flex: 0 0 var(--thumb-list-width); /* 80px */
  width: var(--thumb-list-width);
  height: var(--thumb-list-height); /* 120px */
  aspect-ratio: 2 / 3;
  overflow: hidden;
  background: var(--color-divider);
}

.gallery-card__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    transform var(--duration-scene-translate) var(--ease-decelerate-quint);
}

.gallery-card__img.is-loaded {
  opacity: 1;
}

/* Shared placeholder fallback (list + grid): plain surface, icon only —
   deliberately no alt/title text so missing thumbnails never leak the
   gallery title into the grey box (E2E-3). */
.gallery-card__thumb-placeholder,
.gallery-card__tile-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--drawable-color-secondary);
}

.gallery-card__thumb-placeholder {
  width: var(--thumb-list-width);
  height: var(--thumb-list-height);
}

.gallery-card__tile-placeholder {
  position: absolute;
  inset: 0;
}

.app-card:hover .gallery-card__img {
  transform: scale(1.045);
}

/* ---------------------------------------------------------------- list --- */
.gallery-card__body {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 6px var(--spacing);
  overflow: hidden;
}

.gallery-card__title {
  margin: 0;
  font-size: clamp(14px, var(--text-little-small), 18px); /* 16sp ideal */
  font-weight: 500;
  line-height: 1.25;
  color: var(--text-color-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 150ms var(--ease-decelerate-quart);
}

.app-card:hover .gallery-card__title {
  color: var(--text-color-theme-primary);
}

.gallery-card__title-jpn {
  margin: 0;
  font-size: clamp(11px, var(--text-super-small), 14px); /* 12sp ideal */
  line-height: 1.35;
  color: var(--text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Uploader row (B-2 `general.showUploader`) — roadmap §卡片规范: 14sp
   secondary, single line. */
.gallery-card__uploader {
  margin: 0;
  font-size: clamp(12px, var(--text-small), 14px); /* 14sp ideal */
  line-height: 1.3;
  color: var(--text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gallery-card__rating-row {
  display: flex;
  align-items: center;
  gap: var(--spacing);
  /* Pins the rating/meta block to the bottom of the 120px card. */
  margin-top: auto;
}

.gallery-card__posted {
  margin-left: auto;
  font-size: clamp(11px, var(--text-super-small), 14px);
  color: var(--text-color-secondary);
  white-space: nowrap;
}

.gallery-card__meta-row {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow: hidden;
}

.gallery-card__pages {
  flex-shrink: 0;
  font-size: clamp(11px, var(--text-super-small), 14px);
  color: var(--text-color-secondary);
  white-space: nowrap;
}

.gallery-card__tag {
  flex-shrink: 0;
  padding: 1px 5px;
  border-radius: var(--card-radius);
  background: var(--tag-background);
  color: var(--color-white);
  font-size: clamp(10px, calc(var(--text-super-small) - 1px), 12px); /* 11px ideal */
  line-height: 1.4;
  white-space: nowrap;
}

/* ---------------------------------------------------------------- grid --- */
.gallery-card__tile {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--color-divider);
}

.gallery-card__triangle {
  position: absolute;
  top: 0;
  right: 0;
}

.gallery-card__lang {
  position: absolute;
  left: 4px;
  bottom: 4px;
  font-size: clamp(9px, 10px, 11px); /* 10sp — roadmap spec value, no token */
  font-weight: 700;
  line-height: 1;
  color: var(--color-white);
  text-shadow: 0 1px 2px var(--black-overlay);
}

/* W5: grid-form read-progress badge — mirrors the language badge on the
   opposite (bottom-right) corner of the tile. */
.gallery-card__progress {
  position: absolute;
  right: 4px;
  bottom: 4px;
  font-size: clamp(9px, 10px, 11px);
  font-weight: 700;
  line-height: 1;
  color: var(--color-white);
  text-shadow: 0 1px 2px var(--black-overlay);
}

/* F-UX1: the grid meta area is a flowing column — the title line plus the
   optional `grid-sub` line stack in normal flow (no absolute overlap). The
   padding the title always carried now lives on the wrapper, so a meta area
   without a sub line renders exactly as before. */
.gallery-card__grid-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 4px 6px 6px;
}

.gallery-card__grid-title {
  margin: 0;
  font-size: clamp(11px, var(--text-super-small), 14px); /* 12sp ideal */
  font-weight: 400;
  line-height: 1.3;
  color: var(--text-color-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 150ms var(--ease-decelerate-quart);
}

/* Secondary meta line (History's last-viewed stamp) — 11sp secondary ink,
   single line, stretching the card when present (F-UX1). */
.gallery-card__grid-sub {
  display: flex;
  min-width: 0;
  color: var(--text-color-secondary);
  font-size: clamp(10px, calc(var(--text-super-small) - 1px), 12px);
  line-height: 1.4;
}

.app-card:hover .gallery-card__grid-title {
  color: var(--text-color-theme-primary);
}

/* ------------------------------------------------------------- motion ---- */
@keyframes gallery-card-enter {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-card {
    animation: none;
    transition: none;
  }

  .gallery-card__img {
    opacity: 1;
    transition: none;
  }
}
</style>
