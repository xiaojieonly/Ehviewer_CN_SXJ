<template>
  <AppCard :mode="mode" @click="emit('click', gallery)">
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
          :src="gallery.thumb"
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
        <p v-if="gallery.titleJpn" class="gallery-card__title-jpn">{{ gallery.titleJpn }}</p>
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
          <span v-if="gallery.pages > 0" class="gallery-card__pages">{{ gallery.pages }}P</span>
          <span v-for="tag in gallery.simpleTags" :key="tag" class="gallery-card__tag">
            {{ tag }}
          </span>
        </div>
      </div>
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
          :src="gallery.thumb"
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
        <slot name="overlay" />
      </div>
      <h3 class="gallery-card__grid-title">{{ displayTitle }}</h3>
    </template>
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
 *   title below (2-line clamp).
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
 *
 * The preference keys are read defensively (optional chaining + defaults):
 * they are being added to the preferences schema by a parallel work stream,
 * so the card must behave identically whether or not they exist yet.
 */
import { computed, onMounted, ref } from 'vue'
import {
  CATEGORY_BY_BIT,
  type GalleryCardEmits,
  type GalleryCardProps,
  type GalleryCardSlots,
} from '@/types/components'
import { usePreferencesStore } from '@/stores/preferences'
import type { GeneralPreferences } from '@/api/preferences'
import AppCard from '@/components/atoms/AppCard.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import RatingStars from '@/components/atoms/RatingStars.vue'
import CategoryChip from '@/components/atoms/CategoryChip.vue'
import CategoryTriangle from '@/components/atoms/CategoryTriangle.vue'

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

const preferencesStore = usePreferencesStore()

const generalPrefs = computed<(GeneralPreferences & GeneralPrefsExtras) | undefined>(
  () => preferencesStore.prefs?.general,
)

/** B-2 info switches — strictly `true` shows the field; anything else hides it. */
const showUploader = computed(() => generalPrefs.value?.showUploader === true)
const showPostedTime = computed(() => generalPrefs.value?.showPostedTime === true)

/** R4-6: galleries without a title render as `#<gid>`. */
const displayTitle = computed(() => {
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

.app-card:active {
  transform: scale(0.98);
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

.gallery-card__grid-title {
  margin: 0;
  padding: 4px 6px 6px;
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
